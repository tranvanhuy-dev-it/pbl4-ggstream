package com.project.meet.livestream.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class LivestreamAlreadyLiveException extends ApiException {

	public LivestreamAlreadyLiveException() {
		super("LIVESTREAM_ALREADY_LIVE", HttpStatus.CONFLICT, "Cuộc họp này đang livestream rồi");
	}
}
