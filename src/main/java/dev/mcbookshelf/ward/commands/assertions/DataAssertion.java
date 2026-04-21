package dev.mcbookshelf.ward.commands.assertions;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;

import dev.mcbookshelf.ward.TestExecutor;

/**
 * Asserts that an NBT path exists on a block entity, entity or command storage.
 */
class DataAssertion implements Assertion {
	@Override
	public void attach(LiteralArgumentBuilder<CommandSourceStack> root, Context context) {
		for (DataCommands.DataProvider provider : DataCommands.SOURCE_PROVIDERS) {
			root.then(provider.wrap(Commands.literal("data"), p -> p
					.then(Commands.argument("path", NbtPathArgument.nbtPath())
							.executes(ctx -> assertData(ctx, context, provider)))));
		}
	}

	private static int assertData(
			CommandContext<CommandSourceStack> context,
			Context assertion,
			DataCommands.DataProvider provider) throws CommandSyntaxException {
		return assertion.apply(() -> {
			NbtPathArgument.NbtPath path = NbtPathArgument.getPath(context, "path");
			DataAccessor accessor = provider.access(context);
			CompoundTag data = accessor.getData();

			return new TestExecutor.AssertResult(path.countMatching(data), negated -> {
				String key = Assertions.getTranslationKey("data", negated);
				return Component.translatable(key, path.asString(), data.toString());
			});
		});
	}
}
