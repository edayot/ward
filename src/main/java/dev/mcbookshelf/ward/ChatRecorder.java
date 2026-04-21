package dev.mcbookshelf.ward;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Records system messages sent to players so tests can assert on chat output.
 *
 * <p>Messages are kept in a global append-only log identified by a monotonically increasing
 * sequence number. Each running test remembers the last sequence it has processed and only queries
 * messages recorded after it, so tests running concurrently in the same batch cannot discard each
 * other's messages.
 *
 * <p>Only accessed from the server thread.
 */
public final class ChatRecorder {
	/**
	 * How many ticks a recorded message stays observable. Tests only look at messages recorded since
	 * their previous tick, so anything older than a couple of ticks can no longer be observed by
	 * anyone.
	 */
	private static final int RETENTION_TICKS = 2;

	private record Message(UUID recipient, long sequence, long time, String text) {
	}

	private static final List<Message> MESSAGES = new ArrayList<>();
	private static long sequence = 0;

	private ChatRecorder() {
	}

	public static void record(UUID recipient, long gameTime, String text) {
		MESSAGES.removeIf(message -> message.time() < gameTime - RETENTION_TICKS);
		MESSAGES.add(new Message(recipient, ++sequence, gameTime, text));
	}

	/**
	 * Drops every recorded message. Called between runs: game time restarts on a fresh world, so
	 * the time-based retention can never expire messages recorded near the end of a previous run.
	 */
	public static void clear() {
		MESSAGES.clear();
	}

	/**
	 * Returns the latest sequence number; messages recorded later compare strictly greater.
	 */
	public static long sequence() {
		return sequence;
	}

	/**
	 * Returns all messages recorded after the given sequence number.
	 */
	public static Stream<String> since(long sequence) {
		return MESSAGES.stream().filter(message -> message.sequence() > sequence).map(Message::text);
	}

	/**
	 * Returns messages received by the given player after the given sequence number.
	 */
	public static Stream<String> since(long sequence, UUID recipient) {
		return MESSAGES.stream().filter(message -> message.sequence() > sequence && message.recipient().equals(recipient)).map(Message::text);
	}
}
