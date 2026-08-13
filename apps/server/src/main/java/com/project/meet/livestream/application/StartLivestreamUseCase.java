package com.project.meet.livestream.application;

import com.project.meet.common.config.LivestreamProperties;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Starts an independent livestream — no meeting involved, see
 * docs/networking/livestream.md. Spawns an FFmpeg process and registers
 * the session; the actual media bytes arrive later over
 * {@code LivestreamIngestWebSocketHandler}, not here.
 */
@Component
public class StartLivestreamUseCase {

	private final LivestreamRegistry registry;
	private final LivestreamProcessLauncher processLauncher;
	private final LivestreamProperties properties;
	private final LivestreamCodeGenerator codeGenerator;

	public StartLivestreamUseCase(
			LivestreamRegistry registry,
			LivestreamProcessLauncher processLauncher,
			LivestreamProperties properties,
			LivestreamCodeGenerator codeGenerator
	) {
		this.registry = registry;
		this.processLauncher = processLauncher;
		this.properties = properties;
		this.codeGenerator = codeGenerator;
	}

	public LivestreamStatusResponse execute(UUID hostUserId, String title) {
		UUID id = UUID.randomUUID();
		String code = generateUniqueCode();
		String resolvedTitle = (title == null || title.isBlank()) ? "Livestream" : title.trim();

		Path playlistPath = Path.of(properties.outputDir(), id.toString(), "stream.m3u8");
		try {
			Files.createDirectories(playlistPath.getParent());
		} catch (IOException ex) {
			throw new IllegalStateException("Không thể tạo thư mục output cho livestream", ex);
		}

		LivestreamProcessHandle process;
		try {
			process = processLauncher.launch(id, playlistPath);
		} catch (IOException ex) {
			throw new IllegalStateException("Không thể khởi động tiến trình FFmpeg", ex);
		}

		String hlsUrl = "/livestreams/" + id + "/stream.m3u8";
		registry.put(new LivestreamSession(id, code, hostUserId, resolvedTitle, hlsUrl, process));
		return LivestreamStatusResponse.started(id.toString(), code, resolvedTitle, hlsUrl);
	}

	private String generateUniqueCode() {
		String code;
		do {
			code = codeGenerator.generate();
		} while (registry.codeInUse(code));
		return code;
	}
}
