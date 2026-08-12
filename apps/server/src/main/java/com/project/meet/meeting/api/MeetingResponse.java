package com.project.meet.meeting.api;

import com.project.meet.meeting.domain.MediaMode;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingAccessType;
import com.project.meet.meeting.domain.MeetingStatus;

import java.time.Instant;
import java.util.UUID;

public record MeetingResponse(
		UUID id,
		String code,
		String title,
		UUID hostId,
		String hostDisplayName,
		MeetingStatus status,
		MeetingAccessType accessType,
		MediaMode mediaMode,
		Instant createdAt,
		Instant startedAt,
		Instant endedAt,
		Instant scheduledStartAt,
		Instant scheduledEndAt,
		String timezone
) {

	public static MeetingResponse from(Meeting meeting) {
		return new MeetingResponse(
				meeting.getId(),
				meeting.getCode(),
				meeting.getTitle(),
				meeting.getHost().getId(),
				meeting.getHost().getDisplayName(),
				meeting.getStatus(),
				meeting.getAccessType(),
				meeting.getMediaMode(),
				meeting.getCreatedAt(),
				meeting.getStartedAt(),
				meeting.getEndedAt(),
				meeting.getScheduledStartAt(),
				meeting.getScheduledEndAt(),
				meeting.getTimezone()
		);
	}
}
