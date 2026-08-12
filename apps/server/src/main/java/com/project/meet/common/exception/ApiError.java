package com.project.meet.common.exception;

import java.time.Instant;
import java.util.List;

public record ApiError(
		String code,
		String message,
		Instant timestamp,
		String requestId,
		List<FieldViolation> violations
) {

	public record FieldViolation(String field, String message) {
	}

	public static ApiError of(String code, String message, String requestId) {
		return new ApiError(code, message, Instant.now(), requestId, null);
	}

	public static ApiError of(String code, String message, String requestId, List<FieldViolation> violations) {
		return new ApiError(code, message, Instant.now(), requestId, violations);
	}
}
