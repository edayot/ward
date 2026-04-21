package dev.mcbookshelf.ward.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;

import dev.mcbookshelf.ward.TestExecutor;

/**
 * The fail command for explicitly failing a test.
 */
public final class FailCommand {
	private FailCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		dispatcher.register(Commands.literal("fail")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(FailCommand::failWithoutMessage)
				.then(Commands.argument("message", ComponentArgument.textComponent(context))
						.executes(FailCommand::failWithMessage)));
	}

	private static int failWithoutMessage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		TestExecutor.current().fail(Component.translatable("ward.fail"));
		return 0;
	}

	private static int failWithMessage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		TestExecutor executor = TestExecutor.current();

		Component message;

		try {
			message = ComponentArgument.getResolvedComponent(context, "message");
		} catch (CommandSyntaxException e) {
			message = ComponentUtils.fromMessage(e.getRawMessage());
		}

		executor.fail(message);
		return 0;
	}
}
