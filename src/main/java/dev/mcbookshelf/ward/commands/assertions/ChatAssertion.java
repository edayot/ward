package dev.mcbookshelf.ward.commands.assertions;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

import dev.mcbookshelf.ward.TestExecutor;

/**
 * Asserts that chat messages matching a pattern were received, optionally by specific players.
 */
class ChatAssertion implements Assertion {
	private static final DynamicCommandExceptionType ERROR_INVALID_PATTERN = new DynamicCommandExceptionType(
			pattern -> Component.translatableEscape("ward.assert.invalid_pattern", pattern));

	@Override
	public void attach(LiteralArgumentBuilder<CommandSourceStack> root, Context context) {
		root.then(Commands.literal("chat")
				.then(Commands.argument("pattern", StringArgumentType.string())
						.executes(ctx -> assertChat(ctx, context))
						.then(Commands.argument("players", EntityArgument.players())
								.executes(ctx -> assertChatPlayers(ctx, context)))));
	}

	private static int assertChat(CommandContext<CommandSourceStack> context, Context assertion) throws CommandSyntaxException {
		TestExecutor executor = TestExecutor.current();
		String patternString = StringArgumentType.getString(context, "pattern");
		Pattern pattern = compilePattern(patternString);

		return assertion.apply(() -> {
			int count = (int) executor.chatMessages().filter(msg -> pattern.matcher(msg).find()).count();

			return new TestExecutor.AssertResult(count, negated -> {
				String key = Assertions.getTranslationKey("chat", negated);
				return Component.translatable(key, patternString, count);
			});
		});
	}

	private static int assertChatPlayers(CommandContext<CommandSourceStack> context, Context assertion) throws CommandSyntaxException {
		TestExecutor executor = TestExecutor.current();
		String patternString = StringArgumentType.getString(context, "pattern");
		Pattern pattern = compilePattern(patternString);

		return assertion.apply(() -> {
			int count = (int) EntityArgument.getPlayers(context, "players")
					.stream()
					.flatMap(player -> executor.chatMessages(player.getUUID()))
					.filter(msg -> pattern.matcher(msg).find()).count();

			return new TestExecutor.AssertResult(count, negated -> {
				String key = Assertions.getTranslationKey("chat", negated);
				return Component.translatable(key, patternString, count);
			});
		});
	}

	/**
	 * Compiles a regex pattern, converting invalid patterns into a readable command error.
	 */
	private static Pattern compilePattern(String pattern) throws CommandSyntaxException {
		try {
			return Pattern.compile(pattern);
		} catch (PatternSyntaxException e) {
			throw ERROR_INVALID_PATTERN.create(pattern);
		}
	}
}
