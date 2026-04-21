package dev.mcbookshelf.ward.commands.assertions;

import java.util.Collection;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import dev.mcbookshelf.ward.TestExecutor;

/**
 * Asserts that entities matching a selector exist, optionally only inside the test bounds.
 */
class EntityAssertion implements Assertion {
	@Override
	public void attach(LiteralArgumentBuilder<CommandSourceStack> root, Context context) {
		root.then(Commands.literal("entity")
				.then(Commands.argument("entities", EntityArgument.entities())
						.executes(ctx -> assertEntity(ctx, context, false))
						.then(Commands.literal("inside")
								.executes(ctx -> assertEntity(ctx, context, true)))));
	}

	private static int assertEntity(
			CommandContext<CommandSourceStack> context,
			Context assertion,
			boolean inside) throws CommandSyntaxException {
		EntitySelector selector = context.getArgument("entities", EntitySelector.class);
		TestExecutor executor = TestExecutor.current();
		AABB bounds = executor.getBounds().inflate(1);

		return assertion.apply(() -> {
			Collection<? extends Entity> entities = selector.findEntities(context.getSource());
			int count = inside
					? (int) entities.stream().filter(e -> bounds.contains(e.position())).count()
					: entities.size();

			return new TestExecutor.AssertResult(count, negated -> {
				String key = Assertions.getTranslationKey("entity" + (inside ? "_inside" : ""), negated);
				String input = Assertions.getRawArgument(context, "entities");
				return Component.translatable(key, input, count);
			});
		});
	}
}
