package com.project.meet.signaling.application;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Concurrency model for signaling commands (JOIN, LEAVE, OFFER, ANSWER,
 * ICE_CANDIDATE, MUTE, ...): different meeting rooms process commands fully
 * concurrently, but within one room, commands run strictly in the order
 * they were dispatched — otherwise two participants acting at the same
 * instant (e.g. both joining, or a join racing a leave) could observe or
 * produce an inconsistent {@link MeetingRuntime}.
 *
 * Implemented as one chained {@link CompletableFuture} tail per room rather
 * than a dedicated thread per room: appending a command is
 * {@code tail = tail.thenRunAsync(command, executor)}, which serializes
 * execution for that room without pinning a thread to it for the room's
 * entire lifetime. All rooms share one virtual-thread-per-task executor
 * (Java 21) — cheap enough that "one virtual thread per in-flight command"
 * comfortably scales far beyond what one JVM would realistically host.
 *
 * A failing command is logged and does not break the chain — the next
 * command in that room still runs.
 */
@Component
public class RoomCommandDispatcher {

	private static final Logger log = LoggerFactory.getLogger(RoomCommandDispatcher.class);

	private final Map<UUID, CompletableFuture<Void>> roomTails = new ConcurrentHashMap<>();
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final ScheduledExecutorService delayedDispatchExecutor = Executors.newSingleThreadScheduledExecutor();

	public void dispatch(UUID meetingId, Runnable command) {
		roomTails.compute(meetingId, (id, tail) -> {
			CompletableFuture<Void> previous = (tail != null) ? tail : CompletableFuture.completedFuture(null);
			return previous.thenRunAsync(guarded(meetingId, command), executor);
		});
	}

	/**
	 * Runs {@code command} through this room's normal serialized queue, but
	 * only after {@code delay} — used for the reconnect grace period (see
	 * {@code SignalingWebSocketHandler#handleDisconnect}): schedule the
	 * "actually remove this participant" command for later, so a fast
	 * reconnect can cancel it (by having already replaced the participant's
	 * session) before it ever runs. The delay itself happens on a tiny
	 * dedicated timer thread — only the resulting command executes on the
	 * shared per-room queue, same as every other command.
	 */
	public void scheduleDispatch(UUID meetingId, Runnable command, long delay, TimeUnit unit) {
		delayedDispatchExecutor.schedule(() -> dispatch(meetingId, command), delay, unit);
	}

	/** Called once a room's runtime is empty, so its tail future doesn't linger forever. Safe to call even if new commands race in right after — dispatch() just starts a fresh chain. */
	public void dropRoom(UUID meetingId) {
		roomTails.remove(meetingId);
	}

	private Runnable guarded(UUID meetingId, Runnable command) {
		return () -> {
			try {
				command.run();
			} catch (Exception ex) {
				log.error("Signaling command failed for meeting {}", meetingId, ex);
			}
		};
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
		delayedDispatchExecutor.shutdown();
	}
}
