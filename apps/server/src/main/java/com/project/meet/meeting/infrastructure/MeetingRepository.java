package com.project.meet.meeting.infrastructure;

import com.project.meet.meeting.domain.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

	Optional<Meeting> findByCode(String code);

	boolean existsByCode(String code);
}
