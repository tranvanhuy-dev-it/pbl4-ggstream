package com.project.meet.auth.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
	}
}
