package dev.mcbookshelf.ward;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.permissions.PermissionSet;

import dev.mcbookshelf.ward.accessor.MappedRegistryAccessor;
import dev.mcbookshelf.ward.report.Diagnostic;
import dev.mcbookshelf.ward.report.ReportManager;

/**
 * Discovers and loads test files from data packs.
 *
 * <p>Vanilla only loads the TEST_ENVIRONMENT and TEST_INSTANCE registries at world load; this
 * listener re-loads them on /reload and additionally registers a TEST_INSTANCE and TEST_FUNCTION
 * for every test .mcfunction file.
 */
public class TestLibrary implements PreparableReloadListener {
	private static final FileToIdConverter TEST_FUNCTION_LISTER = new FileToIdConverter("test", ".mcfunction");
	private static final FileToIdConverter TEST_ENVIRONMENT_LISTER = FileToIdConverter.json("test_environment");
	private static final FileToIdConverter TEST_INSTANCE_LISTER = FileToIdConverter.json("test_instance");

	private static final Set<ResourceKey<Consumer<GameTestHelper>>> registeredFunctionKeys = new HashSet<>();

	private final HolderLookup.Provider registries;
	private final PermissionSet testCompilationPermissions;
	private final CommandDispatcher<CommandSourceStack> dispatcher;

	public TestLibrary(
			HolderLookup.Provider registries,
			PermissionSet testCompilationPermissions,
			CommandDispatcher<CommandSourceStack> dispatcher) {
		this.registries = registries;
		this.testCompilationPermissions = testCompilationPermissions;
		this.dispatcher = dispatcher;
	}

	/**
	 * Releases the last run's test functions from the global TEST_FUNCTION registry. The
	 * registered closures hold the parsed commands, and through them the dispatcher and registries
	 * of the server that loaded them: left in place, they keep a halted server reachable until the
	 * next run's reload replaces them.
	 */
	@SuppressWarnings("unchecked")
	public static void release() {
		if (registeredFunctionKeys.isEmpty()) {
			return;
		}

		MappedRegistry<Consumer<GameTestHelper>> functions = (MappedRegistry<Consumer<GameTestHelper>>) BuiltInRegistries.TEST_FUNCTION;
		MappedRegistryAccessor<Consumer<GameTestHelper>> accessor = (MappedRegistryAccessor<Consumer<GameTestHelper>>) functions;
		accessor.ward$unfreeze();
		accessor.ward$clearByPredicate(registeredFunctionKeys::contains);
		registeredFunctionKeys.clear();
		functions.freeze();
	}

	@Override
	public CompletableFuture<Void> reload(
			SharedState currentReload,
			Executor taskExecutor,
			PreparationBarrier preparationBarrier,
			Executor reloadExecutor) {
		ResourceManager manager = currentReload.resourceManager();

		// Prepare phase: list and parse all resources in parallel
		CompletableFuture<Map<Identifier, Resource>> envResources = CompletableFuture.supplyAsync(() ->
				TEST_ENVIRONMENT_LISTER.listMatchingResources(manager), taskExecutor);

		CompletableFuture<Map<Identifier, Resource>> instResources = CompletableFuture.supplyAsync(() ->
				TEST_INSTANCE_LISTER.listMatchingResources(manager), taskExecutor);

		CompletableFuture<Map<Identifier, CompletableFuture<TestFunction>>> testFunctions = CompletableFuture.supplyAsync(() ->
				TEST_FUNCTION_LISTER.listMatchingResources(manager), taskExecutor)
				.thenCompose(resources -> prepareTestFunctions(resources, taskExecutor));

		// Apply phase: reload the test registries on the reload thread
		return CompletableFuture.allOf(envResources, instResources, testFunctions)
				.thenCompose(preparationBarrier::wait)
				.thenAcceptAsync((_) -> {
					RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);

					MappedRegistry<TestEnvironmentDefinition<?>> environments = reloadJsonRegistry(
							Registries.TEST_ENVIRONMENT,
							TEST_ENVIRONMENT_LISTER,
							TestEnvironmentDefinition.DIRECT_CODEC,
							envResources.join(),
							ops);
					environments.freeze();

					// Left unfrozen: registerTests adds an instance per .mcfunction test
					MappedRegistry<GameTestInstance> instances = reloadJsonRegistry(
							Registries.TEST_INSTANCE,
							TEST_INSTANCE_LISTER,
							GameTestInstance.DIRECT_CODEC,
							instResources.join(),
							ops);

					registerTests(instances, environments, collectTestFunctions(testFunctions.join()));
				}, reloadExecutor);
	}

	/**
	 * Parses each test .mcfunction file into a {@link TestFunction} asynchronously.
	 */
	private CompletableFuture<Map<Identifier, CompletableFuture<TestFunction>>> prepareTestFunctions(
			Map<Identifier, Resource> resources,
			Executor taskExecutor) {
		Map<Identifier, CompletableFuture<TestFunction>> result = Maps.newHashMap();
		CommandSourceStack compilationContext = Commands.createCompilationContext(this.testCompilationPermissions);

		for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
			Identifier id = TEST_FUNCTION_LISTER.fileToId(entry.getKey());
			result.put(id, CompletableFuture.supplyAsync(() -> {
				List<String> lines = readLines(entry.getValue());
				return TestFunction.fromLines(this.dispatcher, compilationContext, lines);
			}, taskExecutor));
		}

		return CompletableFuture.allOf(result.values().toArray(new CompletableFuture[0])).handle((_, _) -> result);
	}

	/**
	 * Unfreezes a registry, clears it, and re-registers every value parsed from the given JSON
	 * resources. Parse failures are reported as diagnostics.
	 *
	 * <p>The registry is returned still unfrozen; callers decide when to freeze.
	 */
	@SuppressWarnings("unchecked")
	private <T> MappedRegistry<T> reloadJsonRegistry(
			ResourceKey<Registry<T>> registryKey,
			FileToIdConverter lister,
			Codec<T> codec,
			Map<Identifier, Resource> resources,
			RegistryOps<JsonElement> ops) {
		MappedRegistry<T> registry = (MappedRegistry<T>) registries.lookupOrThrow(registryKey);
		MappedRegistryAccessor<T> accessor = (MappedRegistryAccessor<T>) registry;
		accessor.ward$unfreeze();
		accessor.ward$clearByPredicate(_ -> true);

		for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
			Identifier id = lister.fileToId(entry.getKey());
			ResourceKey<T> key = ResourceKey.create(registryKey, id);

			try (Reader reader = entry.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				T value = codec.parse(ops, json).getOrThrow();
				registry.register(key, value, RegistrationInfo.BUILT_IN);
			} catch (Exception e) {
				Ward.LOGGER.error("Failed to load {} from {}", registryKey.identifier(), id, e);
				ReportManager.report(Diagnostic.error(
						"minecraft:" + registryKey.identifier().getPath(),
						id.toString(),
						Diagnostic.describe(e)));
			}
		}

		return registry;
	}

	/**
	 * Registers a TEST_FUNCTION for every parsed test, plus a TEST_INSTANCE for those not already
	 * defined by a JSON file. Freezes both registries.
	 */
	@SuppressWarnings("unchecked")
	private void registerTests(
			MappedRegistry<GameTestInstance> instances,
			Registry<TestEnvironmentDefinition<?>> environments,
			Map<Identifier, TestFunction> tests) {
		MappedRegistry<Consumer<GameTestHelper>> functions = (MappedRegistry<Consumer<GameTestHelper>>) registries.lookupOrThrow(Registries.TEST_FUNCTION);
		MappedRegistryAccessor<Consumer<GameTestHelper>> accessor = (MappedRegistryAccessor<Consumer<GameTestHelper>>) functions;
		accessor.ward$unfreeze();

		if (!registeredFunctionKeys.isEmpty()) {
			accessor.ward$clearByPredicate(registeredFunctionKeys::contains);
			registeredFunctionKeys.clear();
		}

		for (Map.Entry<Identifier, TestFunction> entry : tests.entrySet()) {
			Identifier id = entry.getKey();
			TestFunction test = entry.getValue();

			ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, id);
			ResourceKey<GameTestInstance> instanceKey = ResourceKey.create(Registries.TEST_INSTANCE, id);

			try {
				// Only create a TEST_INSTANCE if not already defined by JSON
				if (!instances.containsKey(instanceKey)) {
					TestData<Holder<TestEnvironmentDefinition<?>>> testData = test.directives().createTestData(environments);
					instances.register(instanceKey, new FunctionGameTestInstance(functionKey, testData), RegistrationInfo.BUILT_IN);
				}

				functions.register(functionKey, test::run, RegistrationInfo.BUILT_IN);
				registeredFunctionKeys.add(functionKey);
			} catch (Exception e) {
				Ward.LOGGER.error("Failed to load test {}", id, e);
				ReportManager.report(Diagnostic.error("ward:test", id.toString(), Diagnostic.describe(e)));
			}
		}

		instances.freeze();
		functions.freeze();

		Ward.LOGGER.info("Loaded {} test functions", tests.size());
	}

	/**
	 * Collects the parsed tests, reporting the ones that failed to parse.
	 */
	private static Map<Identifier, TestFunction> collectTestFunctions(Map<Identifier, CompletableFuture<TestFunction>> futures) {
		ImmutableMap.Builder<Identifier, TestFunction> result = ImmutableMap.builder();
		futures.forEach((id, future) -> future.handle((test, e) -> {
			if (e == null) {
				result.put(id, test);
			} else {
				Ward.LOGGER.error("Failed to load test function {}", id, e);
				ReportManager.report(Diagnostic.error("ward:test", id.toString(), Diagnostic.describe(e)));
			}

			return null;
		}).join());
		return result.build();
	}

	private static List<String> readLines(Resource resource) {
		try (BufferedReader reader = resource.openAsReader()) {
			return reader.lines().toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
