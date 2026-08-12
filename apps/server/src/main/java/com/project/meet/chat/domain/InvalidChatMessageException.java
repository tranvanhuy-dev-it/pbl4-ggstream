package com.project.meet.chat.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidChatMessageException extends ApiException {

	public InvalidChatMessageException(String message) {
		super("INVALID_CHAT_MESSAGE", HttpStatus.BAD_REQUEST, message);
	}
}
