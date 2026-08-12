package com.project.meet.meeting.api;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.meeting.application.CreateMeetingUseCase;
import com.project.meet.meeting.application.EndMeetingUseCase;
import com.project.meet.meeting.application.GetMeetingUseCase;
import com.project.meet.meeting.application.ListScheduledMeetingsUseCase;
import com.project.meet.meeting.application.StartMeetingUseCase;
import com.project.meet.meeting.application.CancelMeetingUseCase;
import com.project.meet.participant.api.ParticipantResponse;
import com.project.meet.participant.application.JoinMeetingUseCase;
import com.project.meet.participant.application.LeaveMeetingUseCase;
import com.project.meet.participant.application.ListParticipantsUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/meetings")
public class MeetingController {

	private final CreateMeetingUseCase createMeetingUseCase;
	private final GetMeetingUseCase getMeetingUseCase;
	private final EndMeetingUseCase endMeetingUseCase;
	private final JoinMeetingUseCase joinMeetingUseCase;
	private final LeaveMeetingUseCase leaveMeetingUseCase;
	private final ListParticipantsUseCase listParticipantsUseCase;
	private final ListScheduledMeetingsUseCase listScheduledMeetingsUseCase;
	private final StartMeetingUseCase startMeetingUseCase;
	private final CancelMeetingUseCase cancelMeetingUseCase;

	public MeetingController(
			CreateMeetingUseCase createMeetingUseCase,
			GetMeetingUseCase getMeetingUseCase,
			EndMeetingUseCase endMeetingUseCase,
			JoinMeetingUseCase joinMeetingUseCase,
			LeaveMeetingUseCase leaveMeetingUseCase,
			ListParticipantsUseCase listParticipantsUseCase,
			ListScheduledMeetingsUseCase listScheduledMeetingsUseCase,
			StartMeetingUseCase startMeetingUseCase,
			CancelMeetingUseCase cancelMeetingUseCase
	) {
		this.createMeetingUseCase = createMeetingUseCase;
		this.getMeetingUseCase = getMeetingUseCase;
		this.endMeetingUseCase = endMeetingUseCase;
		this.joinMeetingUseCase = joinMeetingUseCase;
		this.leaveMeetingUseCase = leaveMeetingUseCase;
		this.listParticipantsUseCase = listParticipantsUseCase;
		this.listScheduledMeetingsUseCase = listScheduledMeetingsUseCase;
		this.startMeetingUseCase = startMeetingUseCase;
		this.cancelMeetingUseCase = cancelMeetingUseCase;
	}

	@PostMapping
	public ResponseEntity<MeetingResponse> create(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody CreateMeetingRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED).body(createMeetingUseCase.execute(principal.userId(), request));
	}

	@GetMapping("/{meetingId}")
	public MeetingResponse getById(@PathVariable UUID meetingId) {
		return getMeetingUseCase.byId(meetingId);
	}

	@GetMapping("/mine")
	public List<MeetingResponse> mine(
			@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam Instant from,
			@RequestParam Instant to
	) {
		return listScheduledMeetingsUseCase.execute(principal.userId(), from, to);
	}

	@PostMapping("/{meetingId}/start")
	public MeetingResponse start(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID meetingId) {
		return startMeetingUseCase.execute(meetingId, principal.userId());
	}

	@PostMapping("/{meetingId}/cancel")
	public MeetingResponse cancel(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID meetingId) {
		return cancelMeetingUseCase.execute(meetingId, principal.userId());
	}

	@GetMapping("/code/{code}")
	public MeetingResponse getByCode(@PathVariable String code) {
		return getMeetingUseCase.byCode(code);
	}

	@PostMapping("/{meetingId}/join")
	public ParticipantResponse join(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID meetingId) {
		return joinMeetingUseCase.execute(meetingId, principal.userId());
	}

	@PostMapping("/{meetingId}/leave")
	public ResponseEntity<Void> leave(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID meetingId) {
		leaveMeetingUseCase.execute(meetingId, principal.userId());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{meetingId}/end")
	public MeetingResponse end(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID meetingId) {
		return endMeetingUseCase.execute(meetingId, principal.userId());
	}

	@GetMapping("/{meetingId}/participants")
	public List<ParticipantResponse> participants(@PathVariable UUID meetingId) {
		return listParticipantsUseCase.activeParticipants(meetingId);
	}
}
