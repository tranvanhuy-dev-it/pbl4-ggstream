package com.project.meet.livestream.application;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test double for {@link LivestreamProcessLauncher} — captures written
 * bytes in memory instead of spawning a real `ffmpeg` process, so the test
 * suite never depends on FFmpeg actually being installed on the machine
 * running it (mirrors how SFU tests avoid needing a live LiveKit server).
 */
public class FakeLivestreamProcessLauncher implements LivestreamProcessLauncher {

	private final ConcurrentHashMap<UUID, ByteArrayOutputStream> writtenBytes = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, AtomicBoolean> stopped = new ConcurrentHashMap<>();

	@Override
	public LivestreamProcessHandle launch(UUID meetingId, Path playlistPath) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		AtomicBoolean stopFlag = new AtomicBoolean(false);
		writtenBytes.put(meetingId, buffer);
		stopped.put(meetingId, stopFlag);
		return new LivestreamProcessHandle() {
			@Override
			public OutputStream stdin() {
				return buffer;
			}

			@Override
			public void stop() {
				stopFlag.set(true);
			}
		};
	}

	public byte[] bytesWrittenFor(UUID meetingId) {
		ByteArrayOutputStream buffer = writtenBytes.get(meetingId);
		return buffer != null ? buffer.toByteArray() : new byte[0];
	}

	public boolean wasStopped(UUID meetingId) {
		AtomicBoolean flag = stopped.get(meetingId);
		return flag != null && flag.get();
	}
}
