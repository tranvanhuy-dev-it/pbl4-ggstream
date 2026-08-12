package com.project.meet.meeting.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class NotMeetingHostException extends ApiException {

	public NotMeetingHostException() {
		super("NOT_MEETING_HOST", HttpStatus.FORBIDDEN, "Only the meeting host can perform this action");
	}
}
