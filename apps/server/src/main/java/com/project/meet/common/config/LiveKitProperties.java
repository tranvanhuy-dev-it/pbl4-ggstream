package com.project.meet.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.livekit")
public record LiveKitProperties(
		/** WebSocket URL the frontend connects to directly (e.g. ws://localhost:7880) — not a secret. */
		String url,
		String apiKey,
		/** Signs participant access tokens — never sent to the client, only the derived JWT is. */
		String apiSecret
) {

	public boolean isConfigured() {
		return url != null && !url.isBlank() && apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
	}
}
