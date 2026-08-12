package com.project.meet.sfu.api;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.sfu.application.IssueSfuTokenUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/sfu-token")
public class SfuController {

	private final IssueSfuTokenUseCase issueSfuTokenUseCase;

	public SfuController(IssueSfuTokenUseCase issueSfuTokenUseCase) {
		this.issueSfuTokenUseCase = issueSfuTokenUseCase;
	}

	@PostMapping
	public SfuTokenResponse issueToken(@PathVariable UUID meetingId, @AuthenticationPrincipal AuthenticatedUser principal) {
		return issueSfuTokenUseCase.execute(meetingId, principal.userId());
	}
}
