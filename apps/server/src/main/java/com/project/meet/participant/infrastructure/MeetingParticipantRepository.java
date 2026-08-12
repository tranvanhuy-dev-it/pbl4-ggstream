package com.project.meet.participant.infrastructure;

import com.project.meet.participant.domain.MeetingParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, UUID> {

	Optional<MeetingParticipant> findFirstByMeetingIdAndUserIdAndLeftAtIsNull(UUID meetingId, UUID userId);

	List<MeetingParticipant> findByMeetingIdAndLeftAtIsNull(UUID meetingId);

	List<MeetingParticipant> findByMeetingId(UUID meetingId);
}
