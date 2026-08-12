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
import org.springframework.web.socket.CloseStatus;
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
 * Milestone 8: a dropped connection (anything other than a clean,
 * client-initiated close — see SignalingWebSocketHandler#afterConnectionClosed)
 * gets a grace period (test config: reconnect-grace-seconds=1) during which
 * the same user reconnecting resumes their existing roster slot instead of
 * being treated as a fresh join.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ReconnectIntegrationTest {

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
				new HttpEntity<>(new CreateMeetingRequest("Reconnect test", null), headers),
				MeetingResponse.class).getBody();
		return meeting.id();
	}

	private WebSocketSession connect(UUID meetingId, String token, RecordingHandler handler) throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?token=" + token);
		return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
	}

	@Test
	void reconnectingWithinTheGracePeriodResumesWithoutALeaveJoinFlicker() throws Exception {
		String hostToken = registerAndGetToken("reconnect-host", "Host");
		String guestToken = registerAndGetToken("reconnect-guest", "Guest");
		UUID meetingId = createMeeting(hostToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession hostSession = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guestSession = connect(meetingId, guestToken, guestHandler);

			SignalingEnvelope hostSawGuestJoin = readEnvelope(hostHandler.awaitMessage());
			assertThat(hostSawGuestJoin.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);

			// Simulate an unexpected drop (network blip, tab crash) — not the
			// client's own "leave" button, which always closes with NORMAL.
			guestSession.close(CloseStatus.GOING_AWAY);
			// Give the server a moment to actually process the close frame
			// before reconnecting, so the disconnect is enqueued first —
			// comfortably inside the 1s test grace period.
			Thread.sleep(150);

			RecordingHandler reconnectedGuestHandler = new RecordingHandler();
			WebSocketSession reconnectedGuestSession = connect(meetingId, guestToken, reconnectedGuestHandler);
			try {
				// The reconnected client gets a fresh roster snapshot (it may
				// have lost all local state, e.g. a full page reload).
				SignalingEnvelope snapshot = readEnvelope(reconnectedGuestHandler.awaitMessage());
				assertThat(snapshot.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);
				assertThat(snapshot.payload().get("displayName").asString()).isEqualTo("Host");

				// The host never saw a LEFT for the guest, so no JOINED
				// either — just a signal to re-establish the WebRTC leg.
				SignalingEnvelope hostSawReconnect = readEnvelope(hostHandler.awaitMessage());
				assertThat(hostSawReconnect.type()).isEqualTo(SignalingMessageType.PARTICIPANT_RECONNECTED);
				assertThat(hostSawReconnect.payload().get("displayName").asString()).isEqualTo("Guest");
			} finally {
				if (reconnectedGuestSession.isOpen()) reconnectedGuestSession.close();
			}
		} finally {
			hostSession.close();
		}
	}

	@Test
	void disconnectWithoutAReconnectIsRemovedOnceTheGracePeriodExpires() throws Exception {
		String hostToken = registerAndGetToken("reconnect-timeout-host", "Host");
		String guestToken = registerAndGetToken("reconnect-timeout-guest", "Guest");
		UUID meetingId = createMeeting(hostToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession hostSession = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guestSession = connect(meetingId, guestToken, guestHandler);

			SignalingEnvelope hostSawGuestJoin = readEnvelope(hostHandler.awaitMessage());
			assertThat(hostSawGuestJoin.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);

			guestSession.close(CloseStatus.GOING_AWAY);

			SignalingEnvelope hostSawGuestLeave = readEnvelope(hostHandler.awaitMessage());
			assertThat(hostSawGuestLeave.type()).isEqualTo(SignalingMessageType.PARTICIPANT_LEFT);
		} finally {
			hostSession.close();
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
