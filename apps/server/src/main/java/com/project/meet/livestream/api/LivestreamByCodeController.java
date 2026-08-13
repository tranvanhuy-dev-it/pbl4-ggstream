package com.project.meet.livestream.api;

import com.project.meet.livestream.application.GetLivestreamStatusUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public — backs the /watch/{code} viewer page, which only knows the join code (see docs/api/rest.md). */
@RestController
public class LivestreamByCodeController {

	private final GetLivestreamStatusUseCase getLivestreamStatusUseCase;

	public LivestreamByCodeController(GetLivestreamStatusUseCase getLivestreamStatusUseCase) {
		this.getLivestreamStatusUseCase = getLivestreamStatusUseCase;
	}

	@GetMapping("/api/v1/meetings/code/{code}/livestream")
	public LivestreamStatusResponse status(@PathVariable String code) {
		return getLivestreamStatusUseCase.executeByCode(code);
	}
}
