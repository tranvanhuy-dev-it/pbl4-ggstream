package com.project.meet.livestream.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class LivestreamNotLiveException extends ApiException {

	public LivestreamNotLiveException() {
		super("LIVESTREAM_NOT_LIVE", HttpStatus.NOT_FOUND, "Cuộc họp này hiện không livestream");
	}
}
