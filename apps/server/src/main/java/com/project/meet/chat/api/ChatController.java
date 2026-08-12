package com.project.meet.chat.api;

import com.project.meet.chat.application.ListChatMessagesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/messages")
public class ChatController {

	private final ListChatMessagesUseCase listChatMessagesUseCase;

	public ChatController(ListChatMessagesUseCase listChatMessagesUseCase) {
		this.listChatMessagesUseCase = listChatMessagesUseCase;
	}

	@GetMapping
	public List<ChatMessageResponse> history(@PathVariable UUID meetingId) {
		return listChatMessagesUseCase.execute(meetingId);
	}
}
