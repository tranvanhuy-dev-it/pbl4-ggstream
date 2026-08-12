package com.project.meet.auth.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyInUseException extends ApiException {

	public EmailAlreadyInUseException(String email) {
		super("EMAIL_ALREADY_IN_USE", HttpStatus.CONFLICT, "Tài khoản với email '" + email + "' đã tồn tại");
	}
}
