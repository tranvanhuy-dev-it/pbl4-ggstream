package com.project.meet.meeting.application;

import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ListScheduledMeetingsUseCase {
	private final MeetingRepository repository;
	public ListScheduledMeetingsUseCase(MeetingRepository repository) { this.repository = repository; }

	@Transactional(readOnly = true)
	public List<MeetingResponse> execute(UUID userId, Instant from, Instant to) {
		return repository.findByHostIdAndScheduledStartAtBetweenOrderByScheduledStartAtAsc(userId, from, to)
				.stream().map(MeetingResponse::from).toList();
	}
}
