package com.project.meet.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.livestream")
public record LivestreamProperties(
		/** Absolute path or bare command name resolved via PATH — see docs/networking/livestream-setup.md. */
		String ffmpegPath,
		/** Where FFmpeg writes HLS output (.m3u8/.ts), one subfolder per meeting id — transient, gitignored. */
		String outputDir
) {
}
