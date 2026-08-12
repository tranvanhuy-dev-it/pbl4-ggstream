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
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SignalingIntegrationTest {

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
		MeetingResponse meeting = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Signaling test", null), headers),
				MeetingResponse.class).getBody();
		return meeting.id();
	}

	private WebSocketSession connect(UUID meetingId, String token, RecordingHandler handler) throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?token=" + token);
		return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
	}

	@Test
	void secondParticipantJoiningBroadcastsToTheFirst() throws Exception {
		String hostToken = registerAndGetToken("sig-host", "Host");
		String guestToken = registerAndGetToken("sig-guest", "Guest");
		UUID meetingId = createMeeting(hostToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession hostSession = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guestSession = connect(meetingId, guestToken, guestHandler);
			try {
				SignalingEnvelope hostSawGuestJoin = readEnvelope(hostHandler.awaitMessage());
				assertThat(hostSawGuestJoin.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);
				assertThat(hostSawGuestJoin.payload().get("displayName").asString()).isEqualTo("Guest");

				guestSession.close();
				SignalingEnvelope hostSawGuestLeave = readEnvelope(hostHandler.awaitMessage());
				assertThat(hostSawGuestLeave.type()).isEqualTo(SignalingMessageType.PARTICIPANT_LEFT);
			} finally {
				if (guestSession.isOpen()) guestSession.close();
			}
		} finally {
			hostSession.close();
		}
	}

	@Test
	void joiningLateReceivesASnapshotOfExistingParticipants() throws Exception {
		String hostToken = registerAndGetToken("sig-snap-host", "Host");
		String guestToken = registerAndGetToken("sig-snap-guest", "Guest");
		UUID meetingId = createMeeting(hostToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession hostSession = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guestSession = connect(meetingId, guestToken, guestHandler);
			try {
				SignalingEnvelope snapshot = readEnvelope(guestHandler.awaitMessage());
				assertThat(snapshot.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);
				assertThat(snapshot.payload().get("displayName").asString()).isEqualTo("Host");
			} finally {
				guestSession.close();
			}
		} finally {
			hostSession.close();
		}
	}

	@Test
	void pingReceivesPong() throws Exception {
		String token = registerAndGetToken("sig-ping", "Pinger");
		UUID meetingId = createMeeting(token);

		RecordingHandler handler = new RecordingHandler();
		WebSocketSession session = connect(meetingId, token, handler);
		try {
			String ping = objectMapper.writeValueAsString(
					new SignalingEnvelope(SignalingMessageType.PING, "req-1", null, null, null, 0L, null));
			session.sendMessage(new TextMessage(ping));

			SignalingEnvelope pong = readEnvelope(handler.awaitMessage());
			assertThat(pong.type()).isEqualTo(SignalingMessageType.PONG);
			assertThat(pong.requestId()).isEqualTo("req-1");
		} finally {
			session.close();
		}
	}

	@Test
	void pingWithoutATimestampFieldIsStillAccepted() throws Exception {
		// Regression test: a client that omits "timestamp" entirely (it's
		// server-assigned anyway, see SignalingEnvelope) must not fail
		// deserialization — reproduced a real bug where the field was a
		// primitive long, which Jackson refuses to bind from an absent key.
		String token = registerAndGetToken("sig-no-ts", "NoTimestamp");
		UUID meetingId = createMeeting(token);

		RecordingHandler handler = new RecordingHandler();
		WebSocketSession session = connect(meetingId, token, handler);
		try {
			session.sendMessage(new TextMessage("{\"type\":\"PING\",\"requestId\":\"no-ts\"}"));

			SignalingEnvelope pong = readEnvelope(handler.awaitMessage());
			assertThat(pong.type()).isEqualTo(SignalingMessageType.PONG);
			assertThat(pong.requestId()).isEqualTo("no-ts");
		} finally {
			session.close();
		}
	}

	@Test
	void staleSessionIsReapedAfterMissedHeartbeats() throws Exception {
		String hostToken = registerAndGetToken("sig-stale-host", "Host");
		String guestToken = registerAndGetToken("sig-stale-guest", "Guest");
		UUID meetingId = createMeeting(hostToken);

		// Host never sends PING and is expected to be reaped by HeartbeatReaper
		// (test config: heartbeat-timeout-seconds=4, check-interval-ms=300).
		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession hostSession = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guestSession = connect(meetingId, guestToken, guestHandler);
			try {
				guestHandler.awaitMessage(); // initial snapshot (host's presence)

				SignalingEnvelope participantLeft = null;
				long deadline = System.currentTimeMillis() + 8000;
				while (System.currentTimeMillis() < deadline && participantLeft == null) {
					String ping = objectMapper.writeValueAsString(
							new SignalingEnvelope(SignalingMessageType.PING, null, null, null, null, 0L, null));
					guestSession.sendMessage(new TextMessage(ping));

					String raw = guestHandler.poll(1);
					if (raw != null) {
						SignalingEnvelope envelope = readEnvelope(raw);
						if (envelope.type() == SignalingMessageType.PARTICIPANT_LEFT) {
							participantLeft = envelope;
						}
					}
				}

				assertThat(participantLeft).as("guest should observe host's stale session being reaped").isNotNull();
			} finally {
				if (guestSession.isOpen()) guestSession.close();
			}
		} finally {
			if (hostSession.isOpen()) hostSession.close();
		}
	}

	@Test
	void handshakeIsRejectedWithoutAToken() {
		String hostToken = registerAndGetToken("sig-noauth-host", "Host");
		UUID meetingId = createMeeting(hostToken);

		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId);

		assertThatThrownBy(() -> client.execute(new RecordingHandler(), uri.toString()).get(5, TimeUnit.SECONDS))
				.isInstanceOf(Exception.class);
	}

	@Test
	void handshakeIsRejectedForAnUnknownMeeting() {
		String token = registerAndGetToken("sig-unknown", "Someone");

		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + UUID.randomUUID() + "?token=" + token);

		assertThatThrownBy(() -> client.execute(new RecordingHandler(), uri.toString()).get(5, TimeUnit.SECONDS))
				.isInstanceOf(Exception.class);
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

		String poll(long timeoutSeconds) throws InterruptedException {
			return messages.poll(timeoutSeconds, TimeUnit.SECONDS);
		}
	}
}
