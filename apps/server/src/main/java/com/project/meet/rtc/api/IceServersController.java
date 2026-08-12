package com.project.meet.rtc.api;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.rtc.application.GetIceServersUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IceServersController {

	private final GetIceServersUseCase getIceServersUseCase;

	public IceServersController(GetIceServersUseCase getIceServersUseCase) {
		this.getIceServersUseCase = getIceServersUseCase;
	}

	@GetMapping("/api/v1/rtc/ice-servers")
	public IceServersResponse iceServers(@AuthenticationPrincipal AuthenticatedUser principal) {
		return getIceServersUseCase.execute(principal.userId());
	}
}
