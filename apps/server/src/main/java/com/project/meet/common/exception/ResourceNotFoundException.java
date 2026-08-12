package com.project.meet.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException(String code, String message) {
		super(code, HttpStatus.NOT_FOUND, message);
	}
}
