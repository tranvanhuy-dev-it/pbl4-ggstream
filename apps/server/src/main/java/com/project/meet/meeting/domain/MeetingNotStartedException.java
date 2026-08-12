package com.project.meet.meeting.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class MeetingNotStartedException extends ApiException {
	public MeetingNotStartedException() {
		super("MEETING_NOT_STARTED", HttpStatus.CONFLICT, "Chủ phòng chưa bắt đầu cuộc họp");
	}
}
