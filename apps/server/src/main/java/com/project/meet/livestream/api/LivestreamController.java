package com.project.meet.livestream.api;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.livestream.application.GetLivestreamStatusUseCase;
import com.project.meet.livestream.application.StartLivestreamUseCase;
import com.project.meet.livestream.application.StopLivestreamUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/livestream")
public class LivestreamController {

	private final StartLivestreamUseCase startLivestreamUseCase;
	private final StopLivestreamUseCase stopLivestreamUseCase;
	private final GetLivestreamStatusUseCase getLivestreamStatusUseCase;

	public LivestreamController(
			StartLivestreamUseCase startLivestreamUseCase,
			StopLivestreamUseCase stopLivestreamUseCase,
			GetLivestreamStatusUseCase getLivestreamStatusUseCase
	) {
		this.startLivestreamUseCase = startLivestreamUseCase;
		this.stopLivestreamUseCase = stopLivestreamUseCase;
		this.getLivestreamStatusUseCase = getLivestreamStatusUseCase;
	}

	@PostMapping("/start")
	public LivestreamStatusResponse start(@PathVariable UUID meetingId, @AuthenticationPrincipal AuthenticatedUser principal) {
		return startLivestreamUseCase.execute(meetingId, principal.userId());
	}

	@PostMapping("/stop")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void stop(@PathVariable UUID meetingId, @AuthenticationPrincipal AuthenticatedUser principal) {
		stopLivestreamUseCase.execute(meetingId, principal.userId());
	}

	@GetMapping
	public LivestreamStatusResponse status(@PathVariable UUID meetingId) {
		return getLivestreamStatusUseCase.execute(meetingId);
	}
}
