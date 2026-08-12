package com.project.meet.chat.infrastructure;

import com.project.meet.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

	List<ChatMessage> findByMeetingIdOrderByCreatedAtAsc(UUID meetingId);
}
