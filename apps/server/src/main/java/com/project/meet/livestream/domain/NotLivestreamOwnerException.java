package com.project.meet.livestream.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class NotLivestreamOwnerException extends ApiException {

	public NotLivestreamOwnerException() {
		super("NOT_LIVESTREAM_OWNER", HttpStatus.FORBIDDEN, "Chỉ người đã bắt đầu buổi phát này mới có thể thực hiện hành động này");
	}
}
