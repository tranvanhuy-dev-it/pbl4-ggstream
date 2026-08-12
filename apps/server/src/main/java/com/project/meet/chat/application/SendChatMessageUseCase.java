package com.project.meet.chat.application;

import com.project.meet.chat.api.ChatMessageResponse;
import com.project.meet.chat.domain.ChatMessage;
import com.project.meet.chat.domain.InvalidChatMessageException;
import com.project.meet.chat.infrastructure.ChatMessageRepository;
import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class SendChatMessageUseCase {

	private static final int MAX_BODY_LENGTH = 4000;

	private final MeetingRepository meetingRepository;
	private final UserRepository userRepository;
	private final ChatMessageRepository chatMessageRepository;

	public SendChatMessageUseCase(
			MeetingRepository meetingRepository,
			UserRepository userRepository,
			ChatMessageRepository chatMessageRepository
	) {
		this.meetingRepository = meetingRepository;
		this.userRepository = userRepository;
		this.chatMessageRepository = chatMessageRepository;
	}

	@Transactional
	public ChatMessageResponse execute(UUID meetingId, UUID senderUserId, String body) {
		String trimmed = body == null ? "" : body.trim();
		if (trimmed.isEmpty()) {
			throw new InvalidChatMessageException("Tin nhắn không được để trống");
		}
		if (trimmed.length() > MAX_BODY_LENGTH) {
			throw new InvalidChatMessageException("Tin nhắn tối đa " + MAX_BODY_LENGTH + " ký tự");
		}

		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		User sender = userRepository.findById(senderUserId)
				.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Người dùng không còn tồn tại"));

		ChatMessage message = new ChatMessage(meeting, sender, trimmed);
		chatMessageRepository.save(message);

		return ChatMessageResponse.from(message);
	}
}
