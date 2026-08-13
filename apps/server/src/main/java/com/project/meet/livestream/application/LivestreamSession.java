package com.project.meet.livestream.application;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

/**
 * One meeting's active livestream — in-memory only, like {@code MeetingRuntime}
 * for signaling presence (see docs/database/schema.md#what-is-not-persisted):
 * it has no value once the stream ends, so it never touches PostgreSQL.
 */
public class LivestreamSession {

	private final UUID meetingId;
	private final UUID hostUserId;
	private final String hlsUrl;
	private final LivestreamProcessHandle process;
	private final Instant startedAt = Instant.now();

	public LivestreamSession(UUID meetingId, UUID hostUserId, String hlsUrl, LivestreamProcessHandle process) {
		this.meetingId = meetingId;
		this.hostUserId = hostUserId;
		this.hlsUrl = hlsUrl;
		this.process = process;
	}

	/**
	 * Writes one encoded chunk from the broadcaster's MediaRecorder straight
	 * to FFmpeg's stdin. Synchronized because WebSocket binary frames for a
	 * single ingest connection are already delivered one at a time, but a
	 * future reconnect-and-resume path could add a second writer — this
	 * keeps that safe without having to remember to add it later.
	 */
	public synchronized void writeChunk(ByteBuffer payload) throws IOException {
		byte[] bytes = new byte[payload.remaining()];
		payload.get(bytes);
		process.stdin().write(bytes);
		process.stdin().flush();
	}

	public void stop() {
		process.stop();
	}

	public UUID getMeetingId() {
		return meetingId;
	}

	public UUID getHostUserId() {
		return hostUserId;
	}

	public String getHlsUrl() {
		return hlsUrl;
	}

	public Instant getStartedAt() {
		return startedAt;
	}
}
