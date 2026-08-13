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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Livestream is its own top-level feature — a broadcaster goes live
 * directly (TikTok/YouTube-Live-style), with no meeting involved at any
 * point. See docs/networking/livestream.md.
 */
@RestController
@RequestMapping("/api/v1/live")
public class LiveController {

	private final StartLivestreamUseCase startLivestreamUseCase;
	private final StopLivestreamUseCase stopLivestreamUseCase;
	private final GetLivestreamStatusUseCase getLivestreamStatusUseCase;

	public LiveController(
			StartLivestreamUseCase startLivestreamUseCase,
			StopLivestreamUseCase stopLivestreamUseCase,
			GetLivestreamStatusUseCase getLivestreamStatusUseCase
	) {
		this.startLivestreamUseCase = startLivestreamUseCase;
		this.stopLivestreamUseCase = stopLivestreamUseCase;
		this.getLivestreamStatusUseCase = getLivestreamStatusUseCase;
	}

	@PostMapping("/start")
	public LivestreamStatusResponse start(@RequestBody(required = false) StartLivestreamRequest request, @AuthenticationPrincipal AuthenticatedUser principal) {
		String title = request != null ? request.title() : null;
		return startLivestreamUseCase.execute(principal.userId(), title);
	}

	@PostMapping("/{id}/stop")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void stop(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
		stopLivestreamUseCase.execute(id, principal.userId());
	}

	/** Public — no auth, a viewer isn't an account holder in any meeting/livestream sense. */
	@GetMapping("/{code}/status")
	public LivestreamStatusResponse status(@PathVariable String code) {
		return getLivestreamStatusUseCase.execute(code);
	}
}
