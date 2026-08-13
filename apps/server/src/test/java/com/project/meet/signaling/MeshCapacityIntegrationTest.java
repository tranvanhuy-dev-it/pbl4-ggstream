package com.project.meet.signaling;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesh (MESH-mode meetings) is capped — see
 * docs/architecture/scalability-plan.md and MeshProperties. Overrides the
 * limit down to 2 (test yml default is 50, so existing Mesh tests with 4
 * participants keep working) to exercise the rejection path without
 * needing dozens of real WebSocket connections.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(properties = "app.mesh.max-participants=2")
class MeshCapacityIntegrationTest {

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

	private UUID createMeshMeeting(String hostToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(hostToken);
		return restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Mesh capacity test", null), headers),
				MeetingResponse.class).getBody().id();
	}

	private WebSocketSession connect(UUID meetingId, String token, RecordingHandler handler) throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?token=" + token);
		return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
	}

	@Test
	void joiningAFullMeshRoomIsRejectedWithAnErrorEnvelope() throws Exception {
		String hostToken = registerAndGetToken("mesh-cap-host", "Host");
		UUID meetingId = createMeshMeeting(hostToken);

		// Fills the room to the overridden limit of 2.
		WebSocketSession a = connect(meetingId, hostToken, new RecordingHandler());
		WebSocketSession b = connect(meetingId, registerAndGetToken("mesh-cap-b", "B"), new RecordingHandler());
		try {
			RecordingHandler cHandler = new RecordingHandler();
			WebSocketSession c = connect(meetingId, registerAndGetToken("mesh-cap-c", "C"), cHandler);
			try {
				SignalingEnvelope error = readEnvelope(cHandler.awaitMessage());
				assertThat(error.type()).isEqualTo(SignalingMessageType.ERROR);
				assertThat(error.payload().get("code").asString()).isEqualTo("MESH_ROOM_FULL");
			} finally {
				if (c.isOpen()) c.close();
			}
		} finally {
			a.close();
			b.close();
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
