package dev.mcbookshelf.ward.commands.arguments;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

public class DirectionArgument implements ArgumentType<Direction> {
	private static final DynamicCommandExceptionType ERROR_INVALID = new DynamicCommandExceptionType(
			direction -> Component.translatableEscape("ward.argument.direction.invalid", direction));

	@Override
	public Direction parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		String name = reader.readUnquotedString();
		Direction direction = Direction.byName(name);

		if (direction == null) {
			reader.setCursor(start);
			throw ERROR_INVALID.createWithContext(reader, name);
		}

		return direction;
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
		Direction.stream().forEach(d -> builder.suggest(d.getName()));
		return builder.buildFuture();
	}
}
