package dev.mcbookshelf.ward;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import org.jspecify.annotations.Nullable;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import dev.mcbookshelf.ward.accessor.GameTestHelperAccessor;
import dev.mcbookshelf.ward.dummy.Dummy;

/**
 * Executes test commands and manages asynchronous await conditions.
 *
 * <p>Commands execute sequentially, pausing when awaits are encountered. Once all commands and
 * awaits have completed, the test succeeds.
 */
public class TestExecutor {
	private static final SimpleCommandExceptionType ERROR_NOT_IN_TEST = new SimpleCommandExceptionType(
			Component.translatable("ward.not_in_test"));

	/**
	 * The executor whose commands are currently being executed. Commands run synchronously on the
	 * server thread, so a single reference is enough to expose the executor to Ward commands (/fail,
	 * /assert, /await, ...) even across /execute modifiers and nested function calls.
	 */
	private static @Nullable TestExecutor current;

	private final List<Supplier<Boolean>> awaits = new ArrayList<>();
	private final List<Dummy> dummies = new ArrayList<>();
	private final GameTestHelper helper;
	private final int timeout;
	private long chatSequence = ChatRecorder.sequence();
	private int line = 0;
	private boolean done = false;

	public TestExecutor(GameTestHelper helper, int timeout) {
		this.helper = helper;
		this.timeout = timeout;
		// Dummies spawned by the test must not outlive it and leak into other
		// tests; the listener fires on every completion path, timeouts included
		((GameTestHelperAccessor) helper).ward$getTestInfo().addListener(new DummyCleanup());
	}

	/**
	 * Returns the executor of the test currently executing commands.
	 */
	public static TestExecutor current() throws CommandSyntaxException {
		if (current == null) throw ERROR_NOT_IN_TEST.create();
		return current;
	}

	/**
	 * Registers a dummy spawned by the test currently executing commands: it is removed once that
	 * test finishes. Dummies spawned outside of a test are left alone.
	 */
	public static void trackDummy(Dummy dummy) {
		if (current != null) {
			current.dummies.add(dummy);
		}
	}

	public void run(TestFunction function) {
		CommandSourceStack sender = createCommandSourceStack(function);
		Queue<TestFunction.Entry> commands = new ArrayDeque<>(function.commands());

		Runnable tick = () -> {
			current = this;

			try {
				// Check if the first await condition is satisfied
				if (!this.awaits.isEmpty() && this.awaits.getFirst().get()) {
					this.awaits.removeFirst();
				}

				// Execute commands while no awaits are blocking
				while (!commands.isEmpty() && !this.done && this.awaits.isEmpty()) {
					TestFunction.Entry entry = commands.poll();
					this.line = entry.line();
					Commands.executeCommandInContext(sender, ctx ->
							ExecutionContext.queueInitialCommandExecution(
									ctx,
									entry.command(),
									entry.chain(),
									sender,
									CommandResultCallback.EMPTY));
				}

				// If all commands and awaits complete, the test succeeds
				if (!this.done && this.awaits.isEmpty()) {
					succeed();
				}
			} finally {
				current = null;
				// Messages processed by this tick are no longer visible to this test
				this.chatSequence = ChatRecorder.sequence();
			}
		};

		this.helper.onEachTick(tick);
		this.helper.runAtTickTime(this.timeout, tick);
	}

	/**
	 * Immediately fails the test with the given message.
	 */
	public void fail(Component message) {
		this.done = true;
		throw new TestException(message, this.line, this.helper.getTick());
	}

	/**
	 * Immediately succeeds the test and stops execution.
	 */
	public void succeed() {
		this.done = true;
		this.helper.succeed();
	}

	/**
	 * Asserts that a condition is true (count > 0). Fails immediately if the check returns 0 or
	 * errors.
	 *
	 * @return the count from the check
	 */
	public int assertTrue(Supplier<AssertResult> check) {
		AssertResult result = check.get();
		if (result.count() > 0) return result.count();
		fail(result.message().apply(false));
		return 0;
	}

	/**
	 * Asserts that a condition is false (count == 0). Fails immediately if the check returns > 0 or
	 * errors.
	 *
	 * @return 1 if the check passed (count was 0)
	 */
	public int assertFalse(Supplier<AssertResult> check) {
		AssertResult result = check.get();
		if (result.count() == 0 && !result.errored()) return 1;
		fail(result.message().apply(true));
		return 0;
	}

	/**
	 * Awaits a condition to become true (count > 0). Tries immediately, then retries every tick until
	 * timeout.
	 */
	public void awaitTrue(Supplier<AssertResult> check) {
		AssertResult result = check.get();
		if (result.count() > 0) return;

		this.awaits.add(() -> {
			AssertResult retry = check.get();
			if (retry.count() > 0) return true;
			if (!isLastTick()) return false;
			throw new TestException(retry.message().apply(false), this.line, this.helper.getTick());
		});
	}

	/**
	 * Awaits a condition to become false (count == 0). Tries immediately, then retries every tick
	 * until timeout. A check that errors counts as unsatisfied and keeps polling.
	 */
	public void awaitFalse(Supplier<AssertResult> check) {
		AssertResult result = check.get();
		if (result.count() == 0 && !result.errored()) return;

		this.awaits.add(() -> {
			AssertResult retry = check.get();
			if (retry.count() == 0 && !retry.errored()) return true;
			if (!isLastTick()) return false;
			throw new TestException(retry.message().apply(true), this.line, this.helper.getTick());
		});
	}

	/**
	 * Queues a delay for the specified number of ticks. Test execution pauses until the delay
	 * completes.
	 *
	 * @param delay number of ticks to wait
	 */
	public void await(int delay) {
		AtomicInteger remaining = new AtomicInteger(delay);
		this.awaits.add(() -> {
			if (remaining.decrementAndGet() <= 0) return true;
			// A delay knows its future: it fails with its line as soon as it
			// cannot complete by the timeout tick, and keeps counting otherwise
			if (this.helper.getTick() + remaining.get() <= this.timeout) return false;
			throw new TestException(Component.translatable("ward.timeout", this.timeout), this.line, this.helper.getTick());
		});
	}

	/**
	 * Returns true on the last tick a pending condition can fail with its descriptive message.
	 *
	 * <p>The executor ticks through the timeout tick, but a failure raised there would only
	 * finish on the next tick, where the framework overwrites it with its generic timeout;
	 * conditions fail one tick early instead, keeping their message.
	 */
	private boolean isLastTick() {
		return this.helper.getTick() + 1 >= this.timeout;
	}

	/**
	 * Returns the axis-aligned bounding box representing the test structure bounds.
	 */
	public AABB getBounds() {
		return this.helper.getBounds();
	}

	/**
	 * Returns all chat messages recorded since this test last processed its commands.
	 */
	public Stream<String> chatMessages() {
		return ChatRecorder.since(this.chatSequence);
	}

	/**
	 * Returns chat messages received by the given player since this test last processed its commands.
	 */
	public Stream<String> chatMessages(UUID recipient) {
		return ChatRecorder.since(this.chatSequence, recipient);
	}

	private CommandSourceStack createCommandSourceStack(TestFunction function) {
		CommandSourceStack source = this.helper.getLevel()
				.getServer()
				.createCommandSourceStack()
				.withPosition(this.helper.absoluteVec(Vec3.ZERO))
				.withSuppressedOutput();

		Optional<Coordinates> coordinates = function.directives().dummy();

		if (coordinates.isPresent()) {
			try {
				Vec3 pos = coordinates.get().getPosition(source);
				Vec2 rot = coordinates.get().getRotation(source);
				Dummy dummy = Dummy.create(helper.getLevel(), pos, rot);
				dummy.setOnGround(true);
				this.dummies.add(dummy);
				source = source.withEntity(dummy);
			} catch (IllegalArgumentException e) {
				this.helper.fail(Component.literal("Failed to initialize test with dummy"));
			}
		}

		return source;
	}

	/**
	 * Result of an assertion check.
	 *
	 * @param count   number of matching items (>0 indicates condition met)
	 * @param errored true if the check itself failed to evaluate
	 * @param message builder function that creates error messages based on negation state
	 */
	public record AssertResult(int count, boolean errored, Function<Boolean, Component> message) {
		public AssertResult(int count, Function<Boolean, Component> message) {
			this(count, false, message);
		}

		public static AssertResult error(Component message) {
			return new AssertResult(0, true, _ -> message);
		}
	}

	/**
	 * Removes the dummies spawned by this test once it finishes, whichever way it ends.
	 */
	private final class DummyCleanup implements GameTestListener {
		@Override
		public void testStructureLoaded(GameTestInfo testInfo) {
		}

		@Override
		public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
			removeDummies();
		}

		@Override
		public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
			removeDummies();
		}

		@Override
		public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {
		}

		private void removeDummies() {
			PlayerList players = TestExecutor.this.helper.getLevel().getServer().getPlayerList();

			for (Dummy dummy : TestExecutor.this.dummies) {
				// Deaths respawn a fresh instance and /dummy leave may already
				// have removed it: resolve the live player by id first
				if (players.getPlayer(dummy.getUUID()) instanceof Dummy connected) {
					connected.leave(Component.literal("Test finished"));
				}
			}

			TestExecutor.this.dummies.clear();
		}
	}
}
