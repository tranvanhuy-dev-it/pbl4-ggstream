package com.project.meet.livestream.application;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

/**
 * One independent livestream — in-memory only, like {@code MeetingRuntime}
 * for signaling presence (see docs/database/schema.md#what-is-not-persisted):
 * it has no value once the stream ends, so it never touches PostgreSQL.
 *
 * Deliberately has no relationship to {@code Meeting} — a livestream is its
 * own feature (a broadcaster going live, like TikTok/YouTube Live), not a
 * side effect of a meeting existing. {@code id} identifies it for the
 * broadcaster's own start/stop calls; {@code code} is the short public
 * identifier viewers use (see docs/networking/livestream.md).
 */
public class LivestreamSession {

	private final UUID id;
	private final String code;
	private final UUID hostUserId;
	private final String title;
	private final String hlsUrl;
	private final LivestreamProcessHandle process;
	private final Instant startedAt = Instant.now();

	public LivestreamSession(UUID id, String code, UUID hostUserId, String title, String hlsUrl, LivestreamProcessHandle process) {
		this.id = id;
		this.code = code;
		this.hostUserId = hostUserId;
		this.title = title;
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

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getTitle() {
		return title;
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
