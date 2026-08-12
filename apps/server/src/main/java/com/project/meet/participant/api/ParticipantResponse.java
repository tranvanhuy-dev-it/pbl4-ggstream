package com.project.meet.participant.api;

import com.project.meet.participant.domain.MeetingParticipant;
import com.project.meet.participant.domain.ParticipantRole;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
		UUID id,
		UUID userId,
		String displayName,
		ParticipantRole role,
		Instant joinedAt,
		Instant leftAt
) {

	public static ParticipantResponse from(MeetingParticipant participant) {
		return new ParticipantResponse(
				participant.getId(),
				participant.getUser() != null ? participant.getUser().getId() : null,
				participant.getDisplayName(),
				participant.getRole(),
				participant.getJoinedAt(),
				participant.getLeftAt()
		);
	}
}
