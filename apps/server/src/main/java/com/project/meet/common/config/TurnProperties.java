package com.project.meet.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.turn")
public record TurnProperties(
		String host,
		int port,
		/** Shared secret with the Coturn server's static-auth-secret — never sent to the client. */
		String secret,
		long credentialTtlSeconds,
		/** Grouped here rather than a separate properties class since GET /api/v1/rtc/ice-servers always returns both together. */
		String stunUrl
) {

	public boolean isTurnConfigured() {
		return host != null && !host.isBlank() && secret != null && !secret.isBlank();
	}
}
