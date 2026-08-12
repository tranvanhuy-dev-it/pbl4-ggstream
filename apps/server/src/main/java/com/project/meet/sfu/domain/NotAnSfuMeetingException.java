package com.project.meet.sfu.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class NotAnSfuMeetingException extends ApiException {

	public NotAnSfuMeetingException() {
		super("NOT_AN_SFU_MEETING", HttpStatus.CONFLICT, "Cuộc họp này không sử dụng chế độ SFU");
	}
}
