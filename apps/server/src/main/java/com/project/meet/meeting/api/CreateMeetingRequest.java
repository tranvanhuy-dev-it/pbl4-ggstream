package com.project.meet.meeting.api;

import com.project.meet.meeting.domain.MediaMode;
import com.project.meet.meeting.domain.MeetingAccessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateMeetingRequest(
		@NotBlank @Size(max = 200) String title,
		MeetingAccessType accessType,
		Instant scheduledStartAt,
		Instant scheduledEndAt,
		@Size(max = 80) String timezone,
		MediaMode mediaMode
) {
	public CreateMeetingRequest(String title, MeetingAccessType accessType) {
		this(title, accessType, null, null, null, null);
	}
}
