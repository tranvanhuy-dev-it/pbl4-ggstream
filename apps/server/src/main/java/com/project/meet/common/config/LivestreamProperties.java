package com.project.meet.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.livestream")
public record LivestreamProperties(
		/** Absolute path or bare command name resolved via PATH — see docs/networking/livestream-setup.md. */
		String ffmpegPath,
		/** Where FFmpeg writes HLS output (.m3u8/.ts), one subfolder per meeting id — transient, gitignored. */
		String outputDir,
		/**
		 * Space-separated FFmpeg video encoder args, e.g. "-c:v libx264
		 * -preset veryfast" (default, software — works on any machine) or
		 * "-c:v h264_nvenc -preset p4" / "-c:v h264_amf" / "-c:v h264_mf"
		 * for hardware encoding (all built into the FFmpeg build this
		 * project installs — see docs/networking/livestream-setup.md and
		 * docs/architecture/scalability-plan.md). Software encoding is
		 * CPU-bound and becomes the bottleneck well before ~10 concurrent
		 * livestreams; hardware encoding is a config change, not a code
		 * change.
		 */
		String videoCodecArgs
) {
}
