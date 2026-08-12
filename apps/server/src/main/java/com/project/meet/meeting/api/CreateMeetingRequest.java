package com.project.meet.meeting.api;

import com.project.meet.meeting.domain.MeetingAccessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMeetingRequest(
		@NotBlank @Size(max = 200) String title,
		MeetingAccessType accessType
) {
}
