package dev.mcbookshelf.ward;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.jspecify.annotations.Nullable;

import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestEnvironments;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

/**
 * Parsed directives from a test file header.
 *
 * <p>TestDirectives are specified as comments in the format: # @directive value
 *
 * @param template    The structure template ID (default: minecraft:empty)
 * @param environment The test environment ID (default: minecraft:default)
 * @param timeout     Maximum ticks before timeout (default: 100)
 * @param optional    If true, failure doesn't fail the test run (default: false)
 * @param skyAccess   If true, test has sky access (default: false)
 * @param dummy       Position to spawn a dummy player, if specified
 */
public record TestDirectives(
		Identifier template,
		Identifier environment,
		int timeout,
		boolean optional,
		boolean skyAccess,
		Optional<Coordinates> dummy) {
	public TestData<Holder<TestEnvironmentDefinition<?>>> createTestData(Registry<TestEnvironmentDefinition<?>> environments) {
		return new TestData<>(
				environments.getOrThrow(ResourceKey.create(Registries.TEST_ENVIRONMENT, this.environment)),
				this.template,
				this.timeout,
				0,
				!this.optional,
				Rotation.NONE,
				false,
				1,
				1,
				this.skyAccess,
				0);
	}

	/**
	 * Builder for constructing TestDirectives.
	 */
	public static class Builder {
		private Identifier template = Identifier.withDefaultNamespace("empty");
		private Identifier environment = GameTestEnvironments.DEFAULT_KEY.identifier();
		private int timeout = 100;
		private boolean optional = false;
		private boolean skyAccess = false;
		private @Nullable Coordinates dummy = null;

		public void add(String name, @Nullable String value) {
			switch (name.toLowerCase(Locale.ROOT)) {
				case "optional" ->
						this.optional = value == null || Boolean.parseBoolean(value.trim());
				case "skyaccess" ->
						this.skyAccess = value == null || Boolean.parseBoolean(value.trim());
				case "timeout" -> {
					if (value == null) throw new IllegalArgumentException("Missing value");
					this.timeout = Integer.parseInt(value.trim());
					if (this.timeout <= 0) throw new IllegalArgumentException("Timeout must be positive");
				}
				case "template" -> {
					if (value == null) throw new IllegalArgumentException("Missing value");
					this.template = Identifier.parse(value.trim());
				}
				case "environment" -> {
					if (value == null) throw new IllegalArgumentException("Missing value");
					this.environment = Identifier.parse(value.trim());
				}
				case "dummy" -> {
					try {
						String pos = Objects.requireNonNullElse(value, "~ ~ ~");
						this.dummy = Vec3Argument.vec3().parse(new StringReader(pos));
					} catch (CommandSyntaxException e) {
						throw new IllegalArgumentException(e.getMessage());
					}
				}
				default -> throw new IllegalArgumentException("Unknown directive");
			}
		}

		public TestDirectives build() {
			return new TestDirectives(
					template,
					environment,
					timeout,
					optional,
					skyAccess,
					Optional.ofNullable(dummy));
		}
	}
}
