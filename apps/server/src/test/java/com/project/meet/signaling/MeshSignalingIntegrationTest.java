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
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 6 (group Mesh) doesn't add any new backend routing — broadcast
 * (excluding sender) and the per-room ordered dispatcher were already
 * generic over N participants since Milestone 3. These tests exist to lock
 * that in explicitly for 3-4 simultaneous participants rather than just
 * trust it by inspection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MeshSignalingIntegrationTest {

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
				new HttpEntity<>(new CreateMeetingRequest("Mesh test", null), headers),
				MeetingResponse.class).getBody().id();
	}

	private WebSocketSession connect(UUID meetingId, String token) throws Exception {
		return connect(meetingId, token, new RecordingHandler());
	}

	private WebSocketSession connect(UUID meetingId, String token, RecordingHandler handler) throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?token=" + token);
		return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
	}

	@Test
	void fourthParticipantReceivesASnapshotOfAllThreeExistingParticipants() throws Exception {
		String hostToken = registerAndGetToken("mesh-a", "A");
		UUID meetingId = createMeeting(hostToken);

		WebSocketSession a = connect(meetingId, hostToken);
		WebSocketSession b = connect(meetingId, registerAndGetToken("mesh-b", "B"));
		WebSocketSession c = connect(meetingId, registerAndGetToken("mesh-c", "C"));
		try {
			RecordingHandler dHandler = new RecordingHandler();
			WebSocketSession d = connect(meetingId, registerAndGetToken("mesh-d", "D"), dHandler);
			try {
				List<SignalingEnvelope> snapshot = List.of(
						readEnvelope(dHandler.awaitMessage()),
						readEnvelope(dHandler.awaitMessage()),
						readEnvelope(dHandler.awaitMessage()));

				assertThat(snapshot).allMatch(e -> e.type() == SignalingMessageType.PARTICIPANT_JOINED);
				Set<String> names = snapshot.stream()
						.map(e -> e.payload().get("displayName").asString())
						.collect(Collectors.toSet());
				assertThat(names).containsExactlyInAnyOrder("A", "B", "C");
			} finally {
				d.close();
			}
		} finally {
			a.close();
			b.close();
			c.close();
		}
	}

	@Test
	void everyExistingParticipantIsNotifiedWhenAFourthJoins() throws Exception {
		RecordingHandler aHandler = new RecordingHandler();
		RecordingHandler bHandler = new RecordingHandler();
		RecordingHandler cHandler = new RecordingHandler();

		UUID meetingId = createMeeting(registerAndGetToken("mesh2-a", "A"));
		// createMeeting already registered+used a token for the host; reconnect
		// deliberately with fresh users so all three below are plain participants
		// joining the same room.
		WebSocketSession a = connect(meetingId, registerAndGetToken("mesh2-a2", "A"), aHandler);
		WebSocketSession b = connect(meetingId, registerAndGetToken("mesh2-b", "B"), bHandler);
		WebSocketSession c = connect(meetingId, registerAndGetToken("mesh2-c", "C"), cHandler);
		try {
			aHandler.awaitMessage(); // A sees the broadcast of B joining
			bHandler.awaitMessage(); // B's snapshot of A
			aHandler.awaitMessage(); // A sees the broadcast of C joining
			bHandler.awaitMessage(); // B sees the broadcast of C joining
			cHandler.awaitMessage(); // C's snapshot of A
			cHandler.awaitMessage(); // C's snapshot of B

			WebSocketSession d = connect(meetingId, registerAndGetToken("mesh2-d", "D"));
			try {
				SignalingEnvelope aSawD = readEnvelope(aHandler.awaitMessage());
				SignalingEnvelope bSawD = readEnvelope(bHandler.awaitMessage());
				SignalingEnvelope cSawD = readEnvelope(cHandler.awaitMessage());

				for (SignalingEnvelope e : List.of(aSawD, bSawD, cSawD)) {
					assertThat(e.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);
					assertThat(e.payload().get("displayName").asString()).isEqualTo("D");
				}
			} finally {
				d.close();
			}
		} finally {
			a.close();
			b.close();
			c.close();
		}
	}

	@Test
	void whenAParticipantLeavesEveryoneRemainingIsNotified() throws Exception {
		RecordingHandler aHandler = new RecordingHandler();
		RecordingHandler bHandler = new RecordingHandler();
		RecordingHandler cHandler = new RecordingHandler();

		UUID meetingId = createMeeting(registerAndGetToken("mesh3-seed", "Seed"));
		WebSocketSession a = connect(meetingId, registerAndGetToken("mesh3-a", "A"), aHandler);
		WebSocketSession b = connect(meetingId, registerAndGetToken("mesh3-b", "B"), bHandler);
		WebSocketSession c = connect(meetingId, registerAndGetToken("mesh3-c", "C"), cHandler);

		aHandler.awaitMessage(); // A sees the broadcast of B joining
		bHandler.awaitMessage(); // B's snapshot of A
		aHandler.awaitMessage(); // A sees the broadcast of C joining
		bHandler.awaitMessage(); // B sees the broadcast of C joining
		cHandler.awaitMessage(); // C's snapshot of A
		cHandler.awaitMessage(); // C's snapshot of B

		try {
			a.close();

			SignalingEnvelope bSawALeave = readEnvelope(bHandler.awaitMessage());
			SignalingEnvelope cSawALeave = readEnvelope(cHandler.awaitMessage());

			assertThat(bSawALeave.type()).isEqualTo(SignalingMessageType.PARTICIPANT_LEFT);
			assertThat(bSawALeave.payload().get("displayName").asString()).isEqualTo("A");
			assertThat(cSawALeave.type()).isEqualTo(SignalingMessageType.PARTICIPANT_LEFT);
			assertThat(cSawALeave.payload().get("displayName").asString()).isEqualTo("A");
		} finally {
			if (b.isOpen()) b.close();
			if (c.isOpen()) c.close();
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
