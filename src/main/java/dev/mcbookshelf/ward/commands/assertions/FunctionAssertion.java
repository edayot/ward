package dev.mcbookshelf.ward.commands.assertions;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.execution.tasks.IsolatedCall;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.commands.FunctionCommand;

import dev.mcbookshelf.ward.TestExecutor;

/**
 * The function condition, mirroring the semantics of /execute if function: satisfied when the
 * function returns a nonzero result, unsatisfied when it returns zero, fails, or never returns.
 */
class FunctionAssertion implements Assertion {
	@Override
	public void attach(LiteralArgumentBuilder<CommandSourceStack> root, Context context) {
		RequiredArgumentBuilder<CommandSourceStack, FunctionArgument.Result> function = Commands
				.argument("function", FunctionArgument.functions())
				.suggests(FunctionCommand.SUGGEST_FUNCTION);

		if (context.immediate()) {
			function.executes(new AssertFunction(context));
		} else {
			function.executes(ctx -> awaitFunction(ctx, context));
		}

		root.then(Commands.literal("function").then(function));
	}

	private static InstantiatedFunction<CommandSourceStack> instantiate(
			CommandFunction<CommandSourceStack> function,
			CommandDispatcher<CommandSourceStack> dispatcher) throws CommandSyntaxException {
		try {
			return function.instantiate(null, dispatcher);
		} catch (FunctionInstantiationException e) {
			throw ExecuteCommand.ERROR_FUNCTION_CONDITION_INSTANTATION_FAILURE.create(function.id(), e.messageComponent());
		}
	}

	private static Component functionMessage(Identifier function, Object result, boolean negated) {
		return Component.translatable(Assertions.getTranslationKey("function", negated), Component.translationArg(function), result);
	}

	/**
	 * Immediate variant of the function condition. Terminal commands only reach the execution
	 * queue through this vanilla extension point, which is what allows queueing the isolated
	 * function calls and checking their outcome afterwards.
	 */
	private record AssertFunction(Context assertion)
			implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
		@Override
		public void run(
				CommandSourceStack sender,
				ContextChain<CommandSourceStack> currentStep,
				ChainModifiers modifiers,
				ExecutionControl<CommandSourceStack> output) {
			try {
				this.runGuarded(sender, currentStep, output);
			} catch (CommandSyntaxException e) {
				sender.handleError(e, modifiers.isForked(), output.tracer());
				sender.callback().onFailure();
			}
		}

		private void runGuarded(
				CommandSourceStack sender,
				ContextChain<CommandSourceStack> currentStep,
				ExecutionControl<CommandSourceStack> output) throws CommandSyntaxException {
			TestExecutor test = TestExecutor.current();
			boolean negated = this.assertion.mode() == Mode.ASSERT_FALSE;
			CommandContext<CommandSourceStack> context = currentStep.getTopContext().copyFor(sender);
			CommandSourceStack functionContext = FunctionCommand.modifySenderForExecution(sender.clearCallbacks());

			for (CommandFunction<CommandSourceStack> function : FunctionArgument.getFunctions(context, "function")) {
				InstantiatedFunction<CommandSourceStack> instantiated = instantiate(function, this.assertion.dispatcher());
				boolean[] fired = {false};

				output.queueNext(new IsolatedCall<>(control -> {
					control.queueNext(new CallFunction<>(instantiated, control.currentFrame().returnValueConsumer(), true).bind(functionContext));
					control.queueNext(FallthroughTask.instance());
				}, (success, result) -> {
					fired[0] = true;

					if ((result != 0) == negated) {
						test.fail(functionMessage(function.id(), result, negated));
					}
				}));

				// A function that never returns is unsatisfied, like for
				// /execute if function; the check runs once the call completed
				if (!negated) {
					output.queueNext(new IsolatedCall<>(control -> {
						if (!fired[0]) {
							test.fail(Component.translatable("ward.assert.function_no_result", Component.translationArg(function.id())));
						}

						control.queueNext(FallthroughTask.instance());
					}, CommandResultCallback.EMPTY));
				}
			}
		}
	}

	/**
	 * Polling variant of the function condition: the functions are re-executed in their own
	 * context every tick until their results satisfy the mode. Polling starts the tick after
	 * registration, since the registration check runs inside the active execution context.
	 */
	private static int awaitFunction(CommandContext<CommandSourceStack> context, Context assertion) throws CommandSyntaxException {
		CommandSourceStack functionContext = FunctionCommand.modifySenderForExecution(context.getSource().clearCallbacks());
		String name = Assertions.getRawArgument(context, "function");
		List<InstantiatedFunction<CommandSourceStack>> functions = new ArrayList<>();

		for (CommandFunction<CommandSourceStack> function : FunctionArgument.getFunctions(context, "function")) {
			functions.add(instantiate(function, assertion.dispatcher()));
		}

		boolean[] registering = {true};

		return assertion.apply(() -> {
			// An errored result keeps both await modes polling
			if (registering[0]) {
				registering[0] = false;
				return TestExecutor.AssertResult.error(Component.translatable("ward.assert.function_no_result", name));
			}

			int satisfied = 0;
			int result = 0;

			for (InstantiatedFunction<CommandSourceStack> function : functions) {
				int[] value = {0};
				boolean[] fired = {false};

				// The function reports its return through the sender's callback
				CommandSourceStack capturing = functionContext.withCallback((success, functionResult) -> {
					fired[0] = true;
					value[0] = functionResult;
				});

				Commands.executeCommandInContext(capturing, ctx ->
						ExecutionContext.queueInitialFunctionCall(ctx, function, capturing, CommandResultCallback.EMPTY));

				satisfied += fired[0] && value[0] != 0 ? 1 : 0;
				result = value[0];
			}

			int found = result;
			return new TestExecutor.AssertResult(satisfied, negated -> functionMessage(
					Identifier.parse(name.startsWith("#") ? name.substring(1) : name),
					found,
					negated));
		});
	}
}
