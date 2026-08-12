package com.project.meet.signaling.domain;

import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Uniform message shape for every signaling event, both directions. See
 * docs/api/websocket-protocol.md for the full event catalogue.
 */
public record SignalingEnvelope(
		SignalingMessageType type,
		String requestId,
		UUID meetingId,
		UUID senderId,
		UUID targetId,
		// Boxed rather than primitive: the server always overwrites this on
		// every outgoing message anyway (see withServerMetadata and the
		// factory methods below), so a client omitting it entirely must not
		// break deserialization — a primitive `long` here would fail to
		// bind against a JSON object with no "timestamp" key at all.
		Long timestamp,
		JsonNode payload
) {

	public SignalingEnvelope withServerMetadata(UUID meetingId, UUID senderId) {
		return new SignalingEnvelope(type, requestId, meetingId, senderId, targetId, System.currentTimeMillis(), payload);
	}

	public static SignalingEnvelope broadcast(SignalingMessageType type, UUID meetingId, UUID senderId, JsonNode payload) {
		return new SignalingEnvelope(type, null, meetingId, senderId, null, System.currentTimeMillis(), payload);
	}

	public static SignalingEnvelope toTarget(SignalingMessageType type, UUID meetingId, UUID senderId, UUID targetId, JsonNode payload) {
		return new SignalingEnvelope(type, null, meetingId, senderId, targetId, System.currentTimeMillis(), payload);
	}

	public static SignalingEnvelope error(UUID meetingId, String code, String message) {
		return new SignalingEnvelope(SignalingMessageType.ERROR, null, meetingId, null, null, System.currentTimeMillis(),
				tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()
						.put("code", code)
						.put("message", message));
	}
}
