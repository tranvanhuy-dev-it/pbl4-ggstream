package com.project.meet.signaling;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.meeting.api.CreateMeetingRequest;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.MeetingAccessType;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class WaitingRoomAndHostControlsIntegrationTest {

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

	private UUID createMeeting(String hostToken, MeetingAccessType accessType) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(hostToken);
		return restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Controls test", accessType), headers),
				MeetingResponse.class).getBody().id();
	}

	private void joinViaRest(UUID meetingId, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		restTemplate.exchange("/api/v1/meetings/" + meetingId + "/join", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
	}

	private WebSocketSession connect(UUID meetingId, String token, RecordingHandler handler) throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		URI uri = URI.create("ws://localhost:" + port + "/ws/meetings/" + meetingId + "?token=" + token);
		return client.execute(handler, uri.toString()).get(5, TimeUnit.SECONDS);
	}

	@Test
	void guestIsHeldInWaitingRoomUntilHostApproves() throws Exception {
		String hostToken = registerAndGetToken("wait-host", "Host");
		String guestToken = registerAndGetToken("wait-guest", "Guest");
		UUID meetingId = createMeeting(hostToken, MeetingAccessType.APPROVAL_REQUIRED);
		joinViaRest(meetingId, guestToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession host = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guest = connect(meetingId, guestToken, guestHandler);
			try {
				SignalingEnvelope guestSelfAck = readEnvelope(guestHandler.awaitMessage());
				assertThat(guestSelfAck.type()).isEqualTo(SignalingMessageType.PARTICIPANT_WAITING);

				SignalingEnvelope hostNotified = readEnvelope(hostHandler.awaitMessage());
				assertThat(hostNotified.type()).isEqualTo(SignalingMessageType.PARTICIPANT_WAITING);
				assertThat(hostNotified.payload().get("displayName").asString()).isEqualTo("Guest");
				UUID guestUserId = UUID.fromString(hostNotified.payload().get("userId").asString());

				String approve = objectMapper.writeValueAsString(new SignalingEnvelope(
						SignalingMessageType.PARTICIPANT_APPROVED, null, null, null, guestUserId, null, null));
				host.sendMessage(new TextMessage(approve));

				guestHandler.awaitMessage(); // guest's snapshot of the host (sent before the approval confirmation)
				SignalingEnvelope guestApproved = readEnvelope(guestHandler.awaitMessage());
				assertThat(guestApproved.type()).isEqualTo(SignalingMessageType.PARTICIPANT_APPROVED);

				SignalingEnvelope hostSawGuestJoin = readEnvelope(hostHandler.awaitMessage());
				assertThat(hostSawGuestJoin.type()).isEqualTo(SignalingMessageType.PARTICIPANT_JOINED);
			} finally {
				guest.close();
			}
		} finally {
			host.close();
		}
	}

	@Test
	void hostCanRejectAWaitingGuest() throws Exception {
		String hostToken = registerAndGetToken("reject-host", "Host");
		String guestToken = registerAndGetToken("reject-guest", "Guest");
		UUID meetingId = createMeeting(hostToken, MeetingAccessType.APPROVAL_REQUIRED);
		joinViaRest(meetingId, guestToken);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession host = connect(meetingId, hostToken, hostHandler);
		try {
			RecordingHandler guestHandler = new RecordingHandler();
			WebSocketSession guest = connect(meetingId, guestToken, guestHandler);
			try {
				guestHandler.awaitMessage(); // guest's own waiting ack
				SignalingEnvelope hostNotified = readEnvelope(hostHandler.awaitMessage());
				UUID guestUserId = UUID.fromString(hostNotified.payload().get("userId").asString());

				String reject = objectMapper.writeValueAsString(new SignalingEnvelope(
						SignalingMessageType.PARTICIPANT_REJECTED, null, null, null, guestUserId, null, null));
				host.sendMessage(new TextMessage(reject));

				SignalingEnvelope guestRejected = readEnvelope(guestHandler.awaitMessage());
				assertThat(guestRejected.type()).isEqualTo(SignalingMessageType.PARTICIPANT_REJECTED);
			} finally {
				if (guest.isOpen()) guest.close();
			}
		} finally {
			host.close();
		}
	}

	@Test
	void hostMuteIsRelayedOnlyToTheTargetedParticipant() throws Exception {
		String hostToken = registerAndGetToken("mute-host", "Host");
		String guestToken = registerAndGetToken("mute-guest", "Guest");
		UUID meetingId = createMeeting(hostToken, null);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession host = connect(meetingId, hostToken, hostHandler);
		RecordingHandler guestHandler = new RecordingHandler();
		WebSocketSession guest = connect(meetingId, guestToken, guestHandler);
		try {
			SignalingEnvelope hostSawGuestJoin = readEnvelope(hostHandler.awaitMessage());
			UUID guestUserId = UUID.fromString(hostSawGuestJoin.payload().get("userId").asString());
			guestHandler.awaitMessage(); // guest's snapshot of the host

			String mute = objectMapper.writeValueAsString(new SignalingEnvelope(
					SignalingMessageType.HOST_MUTE_PARTICIPANT, null, null, null, guestUserId, null, null));
			host.sendMessage(new TextMessage(mute));

			SignalingEnvelope guestSaw = readEnvelope(guestHandler.awaitMessage());
			assertThat(guestSaw.type()).isEqualTo(SignalingMessageType.HOST_MUTE_PARTICIPANT);
		} finally {
			host.close();
			guest.close();
		}
	}

	@Test
	void nonHostCannotMuteAnotherParticipant() throws Exception {
		String hostToken = registerAndGetToken("nomute-host", "Host");
		String guestToken = registerAndGetToken("nomute-guest", "Guest");
		UUID meetingId = createMeeting(hostToken, null);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession host = connect(meetingId, hostToken, hostHandler);
		RecordingHandler guestHandler = new RecordingHandler();
		WebSocketSession guest = connect(meetingId, guestToken, guestHandler);
		try {
			SignalingEnvelope guestSawHostJoin = readEnvelope(guestHandler.awaitMessage());
			UUID hostUserId = UUID.fromString(guestSawHostJoin.payload().get("userId").asString());
			hostHandler.awaitMessage(); // host sees guest join

			String mute = objectMapper.writeValueAsString(new SignalingEnvelope(
					SignalingMessageType.HOST_MUTE_PARTICIPANT, null, null, null, hostUserId, null, null));
			guest.sendMessage(new TextMessage(mute));

			// Nothing further should arrive for the host — poll with a short timeout instead of awaitMessage's 5s.
			String stray = hostHandler.poll(1);
			assertThat(stray).isNull();
		} finally {
			host.close();
			guest.close();
		}
	}

	@Test
	void hostRemoveDisconnectsTheTargetAndNotifiesEveryoneElse() throws Exception {
		String hostToken = registerAndGetToken("remove-host", "Host");
		String guestToken = registerAndGetToken("remove-guest", "Guest");
		UUID meetingId = createMeeting(hostToken, null);

		RecordingHandler hostHandler = new RecordingHandler();
		WebSocketSession host = connect(meetingId, hostToken, hostHandler);
		RecordingHandler guestHandler = new RecordingHandler();
		WebSocketSession guest = connect(meetingId, guestToken, guestHandler);
		try {
			SignalingEnvelope hostSawGuestJoin = readEnvelope(hostHandler.awaitMessage());
			UUID guestUserId = UUID.fromString(hostSawGuestJoin.payload().get("userId").asString());
			guestHandler.awaitMessage(); // guest's snapshot of the host

			String remove = objectMapper.writeValueAsString(new SignalingEnvelope(
					SignalingMessageType.HOST_REMOVE_PARTICIPANT, null, null, null, guestUserId, null, null));
			host.sendMessage(new TextMessage(remove));

			SignalingEnvelope guestSaw = readEnvelope(guestHandler.awaitMessage());
			assertThat(guestSaw.type()).isEqualTo(SignalingMessageType.HOST_REMOVE_PARTICIPANT);

			SignalingEnvelope hostSawGuestLeave = readEnvelope(hostHandler.awaitMessage());
			assertThat(hostSawGuestLeave.type()).isEqualTo(SignalingMessageType.PARTICIPANT_LEFT);

			awaitClose(guest);
			assertThat(guest.isOpen()).isFalse();
		} finally {
			host.close();
			if (guest.isOpen()) guest.close();
		}
	}

	private void awaitClose(WebSocketSession session) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 3000;
		while (session.isOpen() && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
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

		String poll(long timeoutSeconds) throws InterruptedException {
			return messages.poll(timeoutSeconds, TimeUnit.SECONDS);
		}
	}
}
