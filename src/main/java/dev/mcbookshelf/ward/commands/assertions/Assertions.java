package dev.mcbookshelf.ward.commands.assertions;

import java.util.List;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.ArgumentCommandNode;

import net.minecraft.commands.CommandSourceStack;

/**
 * The assert/await condition registry.
 */
public final class Assertions {
	private static final List<Assertion> CONDITIONS = List.of(
			new BiomeAssertion(),
			new BlockAssertion(),
			new ChatAssertion(),
			new DataAssertion(),
			new EntityAssertion(),
			new FunctionAssertion(),
			new ItemsAssertion(),
			new PredicateAssertion(),
			new RunAssertion(),
			new ScoreAssertion());

	private Assertions() {
	}

	/**
	 * Attaches every condition to the given assert/await literal.
	 */
	public static LiteralArgumentBuilder<CommandSourceStack> build(
			LiteralArgumentBuilder<CommandSourceStack> root,
			Assertion.Context context) {
		for (Assertion condition : CONDITIONS) {
			condition.attach(root, context);
		}

		return root;
	}

	/**
	 * Formats a message key for an assertion type.
	 */
	static String getTranslationKey(String type, boolean negated) {
		return "ward.assert." + (negated ? "not_" : "") + type;
	}

	/**
	 * Extracts the original input text for a command argument.
	 *
	 * @return the user's input string as typed
	 */
	static String getRawArgument(CommandContext<?> ctx, String name) {
		for (ParsedCommandNode<?> node : ctx.getNodes()) {
			if (node.getNode() instanceof ArgumentCommandNode<?, ?> argNode) {
				if (argNode.getName().equals(name)) {
					StringRange range = node.getRange();
					return ctx.getInput().substring(range.getStart(), range.getEnd());
				}
			}
		}

		throw new IllegalArgumentException("No such argument '" + name + "' exists on this command");
	}
}
