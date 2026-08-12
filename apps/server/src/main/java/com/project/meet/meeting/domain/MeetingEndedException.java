package com.project.meet.meeting.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class MeetingEndedException extends ApiException {

	public MeetingEndedException() {
		super("MEETING_ENDED", HttpStatus.CONFLICT, "This meeting has already ended");
	}
}
