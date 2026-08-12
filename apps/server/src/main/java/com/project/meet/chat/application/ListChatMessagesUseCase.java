package com.project.meet.chat.application;

import com.project.meet.chat.api.ChatMessageResponse;
import com.project.meet.chat.infrastructure.ChatMessageRepository;
import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class ListChatMessagesUseCase {

	private final MeetingRepository meetingRepository;
	private final ChatMessageRepository chatMessageRepository;

	public ListChatMessagesUseCase(MeetingRepository meetingRepository, ChatMessageRepository chatMessageRepository) {
		this.meetingRepository = meetingRepository;
		this.chatMessageRepository = chatMessageRepository;
	}

	@Transactional(readOnly = true)
	public List<ChatMessageResponse> execute(UUID meetingId) {
		if (!meetingRepository.existsById(meetingId)) {
			throw new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp");
		}
		return chatMessageRepository.findByMeetingIdOrderByCreatedAtAsc(meetingId).stream()
				.map(ChatMessageResponse::from)
				.toList();
	}
}
