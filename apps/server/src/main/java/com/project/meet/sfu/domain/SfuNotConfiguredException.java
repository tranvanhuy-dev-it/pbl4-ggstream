package com.project.meet.sfu.domain;

import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Thrown when an SFU-mode meeting needs a token but LIVEKIT_URL/LIVEKIT_API_KEY/LIVEKIT_API_SECRET aren't set. */
public class SfuNotConfiguredException extends ApiException {

	public SfuNotConfiguredException() {
		super("SFU_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE, "Máy chủ SFU chưa được cấu hình");
	}
}
