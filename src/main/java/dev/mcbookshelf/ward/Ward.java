package dev.mcbookshelf.ward;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.commands.CommandBuildContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.mcbookshelf.ward.commands.AssertCommand;
import dev.mcbookshelf.ward.commands.AwaitCommand;
import dev.mcbookshelf.ward.commands.DummyCommand;
import dev.mcbookshelf.ward.commands.FailCommand;
import dev.mcbookshelf.ward.commands.SucceedCommand;
import dev.mcbookshelf.ward.commands.arguments.DirectionArgument;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Ward implements ModInitializer {
	public static final String MOD_ID = "ward";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final @Nullable String DAEMON = System.getProperty("ward.daemon");
	public static final boolean ENABLED = DAEMON != null;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Override
	public void onInitialize() {
		ArgumentTypeRegistry.registerArgumentType(
				Identifier.fromNamespaceAndPath("ward", "direction"),
				DirectionArgument.class,
				SingletonArgumentInfo.contextFree(DirectionArgument::new));

		CommandRegistrationCallback.EVENT.register((dispatcher, context, _) -> {
			registerCommands(dispatcher, context);

			CommandDispatcher<CommandSourceStack> modDispatcher = new CommandDispatcher<>();
			registerCommands(modDispatcher, context);
			exportCommandTree(modDispatcher);
		});
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		FailCommand.register(dispatcher, context);
		SucceedCommand.register(dispatcher, context);
		AssertCommand.register(dispatcher, context);
		AwaitCommand.register(dispatcher, context);
		DummyCommand.register(dispatcher, context);
	}

	private void exportCommandTree(CommandDispatcher<CommandSourceStack> dispatcher) {
		try {
			// 1. Serialize isolated dispatcher root node
			JsonObject commandTreeJson = ArgumentUtils.serializeNodeToJson(dispatcher, dispatcher.getRoot());

			// 2. Step out of the "run/" working directory back to project root
			Path rootDir = Path.of(".").toAbsolutePath().normalize();
			if (rootDir.getFileName().toString().equals("run")) {
				rootDir = rootDir.getParent();
			}

			Path outputPath = rootDir.resolve("packages/mcward-beet/src/mcward/mecha/commands.json");

			if (outputPath.getParent() != null) {
				Files.createDirectories(outputPath.getParent());
			}

			// 3. Write output
			try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
				GSON.toJson(commandTreeJson, writer);
				LOGGER.info("Successfully exported mod command tree to {}", outputPath.toAbsolutePath());
			}
		} catch (IOException e) {
			LOGGER.error("Failed to export command tree JSON", e);
		}
	}
}