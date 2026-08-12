package com.project.meet.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for domain/application exceptions that should be translated
 * into a stable {@link ApiError} response instead of a raw stack trace.
 */
public class ApiException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public ApiException(String code, HttpStatus status, String message) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
