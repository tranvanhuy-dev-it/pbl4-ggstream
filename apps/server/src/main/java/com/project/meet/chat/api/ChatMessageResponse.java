package com.project.meet.chat.api;

import com.project.meet.chat.domain.ChatMessage;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
		UUID id,
		UUID meetingId,
		UUID senderId,
		String senderDisplayName,
		String body,
		Instant createdAt
) {

	public static ChatMessageResponse from(ChatMessage message) {
		return new ChatMessageResponse(
				message.getId(),
				message.getMeeting().getId(),
				message.getSender().getId(),
				message.getSender().getDisplayName(),
				message.getBody(),
				message.getCreatedAt()
		);
	}
}
