package com.project.meet.chat;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.chat.api.ChatMessageResponse;
import com.project.meet.meeting.api.CreateMeetingRequest;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.signaling.domain.SignalingEnvelope;
import com.project.meet.signaling.domain.SignalingMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ChatIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${local.server.port}")
	private int port;

	private String registerAndGetToken(String emailPrefix, String displayName) {
		String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
		AuthResponse response = restTemplate.postForEntity(
				"/api/v1/auth/register", new RegisterRequest(email, "correct-horse-battery", displayName), AuthResponse.class).getBody();
		return response.accessToken();
	}

	private UUID createMeeting(String hostToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(hostToken);
		return restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Chat test", null), headers),
				MeetingResponse.class).getBody().id();
	}

	private WebSocketSession connect(UUID meetingId, String token, RecordingHandler handler) throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?token=" + token);
		return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
	}

	@Test
	void chatMessageIsPersistedAndBroadcastToEveryoneIncludingTheSender() throws Exception {
		String hostToken = registerAndGetToken("chat-host", "Host");
		String guestToken = registerAndGetToken("chat-guest", "Guest");
		UUID meetingId = createMeeting(hostToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession host = connect(meetingId, hostToken, hostHandler);
		RecordingHandler guestHandler = new RecordingHandler();
		WebSocketSession guest = connect(meetingId, guestToken, guestHandler);
		try {
			hostHandler.awaitMessage(); // host sees guest's PARTICIPANT_JOINED broadcast
			guestHandler.awaitMessage(); // guest's snapshot of host

			String chatEnvelope = objectMapper.writeValueAsString(new SignalingEnvelope(
					SignalingMessageType.CHAT_MESSAGE, null, null, null, null, null,
					JsonNodeFactory.instance.objectNode().put("body", "hello room")));
			guest.sendMessage(new TextMessage(chatEnvelope));

			SignalingEnvelope hostSaw = readEnvelope(hostHandler.awaitMessage());
			SignalingEnvelope guestSaw = readEnvelope(guestHandler.awaitMessage());

			for (SignalingEnvelope e : new SignalingEnvelope[]{hostSaw, guestSaw}) {
				assertThat(e.type()).isEqualTo(SignalingMessageType.CHAT_MESSAGE);
				assertThat(e.payload().get("body").asString()).isEqualTo("hello room");
				assertThat(e.payload().get("senderDisplayName").asString()).isEqualTo("Guest");
			}

			HttpHeaders headers = new HttpHeaders();
			headers.setBearerAuth(hostToken);
			ResponseEntity<ChatMessageResponse[]> history = restTemplate.exchange(
					"/api/v1/meetings/" + meetingId + "/messages", HttpMethod.GET,
					new HttpEntity<>(headers), ChatMessageResponse[].class);

			assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(history.getBody()).hasSize(1);
			assertThat(history.getBody()[0].body()).isEqualTo("hello room");
		} finally {
			host.close();
			guest.close();
		}
	}

	@Test
	void blankChatMessageIsRejectedWithAnErrorEnvelope() throws Exception {
		String token = registerAndGetToken("chat-blank", "Solo");
		UUID meetingId = createMeeting(token);

		RecordingHandler handler = new RecordingHandler();
		WebSocketSession session = connect(meetingId, token, handler);
		try {
			String chatEnvelope = objectMapper.writeValueAsString(new SignalingEnvelope(
					SignalingMessageType.CHAT_MESSAGE, null, null, null, null, null,
					JsonNodeFactory.instance.objectNode().put("body", "   ")));
			session.sendMessage(new TextMessage(chatEnvelope));

			SignalingEnvelope response = readEnvelope(handler.awaitMessage());
			assertThat(response.type()).isEqualTo(SignalingMessageType.ERROR);
		} finally {
			session.close();
		}
	}

	private SignalingEnvelope readEnvelope(String json) {
		return objectMapper.readValue(json, SignalingEnvelope.class);
	}

	private static class RecordingHandler extends TextWebSocketHandler {
		private final LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();

		@Override
		protected void handleTextMessage(WebSocketSession session, TextMessage message) {
			messages.add(message.getPayload());
		}

		String awaitMessage() throws InterruptedException {
			String message = messages.poll(5, TimeUnit.SECONDS);
			if (message == null) {
				throw new AssertionError("Timed out waiting for a signaling message");
			}
			return message;
		}
	}
}
