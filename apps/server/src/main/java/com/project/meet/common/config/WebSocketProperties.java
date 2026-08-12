package com.project.meet.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.websocket")
public record WebSocketProperties(
		int heartbeatIntervalSeconds,
		int heartbeatTimeoutSeconds,
		int reconnectGraceSeconds
) {
}
