package dev.mcbookshelf.ward.commands.assertions;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ComponentUtils;

import dev.mcbookshelf.ward.TestExecutor;

/**
 * A single assert/await condition, attaching its command nodes to the root literal.
 */
public interface Assertion {
	void attach(LiteralArgumentBuilder<CommandSourceStack> root, Context context);

	/**
	 * Execution modes for assertions combining negation and timing.
	 */
	enum Mode {
		/**Immediate assertion expecting count > 0. */
		ASSERT_TRUE,
		/** Immediate assertion expecting count == 0. */
		ASSERT_FALSE,
		/** Polling assertion expecting count > 0. */
		AWAIT_TRUE,
		/** Polling assertion expecting count == 0. */
		AWAIT_FALSE
	}

	/**
	 * Everything a condition needs to build its command nodes: the dispatcher (for redirect
	 * targets), the argument build context, and the mode the nodes execute under.
	 */
	record Context(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Mode mode) {
		/**
		 * Whether the mode is an immediate assert rather than a polling await.
		 */
		boolean immediate() {
			return this.mode == Mode.ASSERT_TRUE || this.mode == Mode.ASSERT_FALSE;
		}

		/**
		 * Applies the mode's execution strategy to a check on the current test.
		 *
		 * @return command success count (actual count for ASSERT modes, 1 for AWAIT modes)
		 */
		int apply(ResultSupplier check) throws CommandSyntaxException {
			TestExecutor test = TestExecutor.current();

			return switch (this.mode) {
				case ASSERT_TRUE -> test.assertTrue(check::get);
				case ASSERT_FALSE -> test.assertFalse(check::get);
				case AWAIT_TRUE -> {
					test.awaitTrue(check::get);
					yield Command.SINGLE_SUCCESS;
				}
				case AWAIT_FALSE -> {
					test.awaitFalse(check::get);
					yield Command.SINGLE_SUCCESS;
				}
			};
		}
	}

	/**
	 * Supplier that can throw CommandSyntaxException during assertion checks.
	 *
	 * <p>This is the single error path for assertions: arguments are resolved inside the supplier
	 * so that execution-time errors (unloaded position, unknown objective, invalid pattern, ...)
	 * become an errored result instead of a silently failed command — the engine swallows
	 * exceptions thrown by command bodies, and nothing reads command results in a test. An errored
	 * result fails {@code assert}/{@code assert not} and keeps {@code await} polling.
	 */
	@FunctionalInterface
	interface ResultSupplier {
		TestExecutor.AssertResult getOrThrow() throws CommandSyntaxException;

		default TestExecutor.AssertResult get() {
			try {
				return getOrThrow();
			} catch (CommandSyntaxException e) {
				return TestExecutor.AssertResult.error(ComponentUtils.fromMessage(e.getRawMessage()));
			}
		}
	}
}
