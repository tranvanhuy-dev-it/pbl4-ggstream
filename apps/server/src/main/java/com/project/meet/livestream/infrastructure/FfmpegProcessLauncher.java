package com.project.meet.livestream.infrastructure;

import com.project.meet.common.config.LivestreamProperties;
import com.project.meet.livestream.application.LivestreamProcessHandle;
import com.project.meet.livestream.application.LivestreamProcessLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Real implementation of {@link LivestreamProcessLauncher} — spawns FFmpeg
 * reading raw encoded chunks (whatever MediaRecorder produced in the
 * browser, typically WebM/VP8+Opus) from stdin and writing H.264/AAC HLS
 * segments to {@code playlistPath}. See docs/networking/livestream-setup.md
 * for how FFmpeg itself gets installed.
 */
@Component
public class FfmpegProcessLauncher implements LivestreamProcessLauncher {

	private static final Logger log = LoggerFactory.getLogger(FfmpegProcessLauncher.class);

	private final LivestreamProperties properties;

	public FfmpegProcessLauncher(LivestreamProperties properties) {
		this.properties = properties;
	}

	@Override
	public LivestreamProcessHandle launch(UUID meetingId, Path playlistPath) throws IOException {
		List<String> command = new ArrayList<>();
		command.add(properties.ffmpegPath());
		command.add("-i");
		command.add("pipe:0");
		// Configurable so switching to hardware encoding (nvenc/amf/mf) is
		// an env var change, not a code change — see LivestreamProperties.
		command.addAll(Arrays.asList(properties.videoCodecArgs().trim().split("\\s+")));
		command.add("-c:a");
		command.add("aac");
		command.add("-f");
		command.add("hls");
		command.add("-hls_time");
		command.add("2");
		command.add("-hls_list_size");
		command.add("6");
		command.add("-hls_flags");
		command.add("delete_segments");
		command.add(playlistPath.toString());

		ProcessBuilder builder = new ProcessBuilder(command);
		// Keep stderr separate from stdout — FFmpeg writes its (very
		// verbose) progress/diagnostics to stderr, which we log but never
		// mix into anything meant to be a data stream.
		Process process = builder.start();
		logStderrInBackground(meetingId, process);
		return new FfmpegProcessHandle(process);
	}

	private void logStderrInBackground(UUID meetingId, Process process) {
		Thread.ofVirtual().start(() -> {
			try (var reader = process.errorReader()) {
				String line;
				while ((line = reader.readLine()) != null) {
					log.debug("ffmpeg[{}]: {}", meetingId, line);
				}
			} catch (IOException ignored) {
				// process exited — nothing left to read
			}
		});
	}

	private static class FfmpegProcessHandle implements LivestreamProcessHandle {
		private final Process process;

		FfmpegProcessHandle(Process process) {
			this.process = process;
		}

		@Override
		public OutputStream stdin() {
			return process.getOutputStream();
		}

		@Override
		public void stop() {
			try {
				process.getOutputStream().close();
			} catch (IOException ignored) {
				// already closed/broken pipe — fine, we're stopping anyway
			}
			if (process.isAlive()) {
				process.destroy();
			}
		}
	}
}
