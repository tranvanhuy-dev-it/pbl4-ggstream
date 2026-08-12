package com.project.meet.signaling.infrastructure;

import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.participant.domain.ParticipantRole;
import com.project.meet.signaling.application.MeetingRuntime;
import com.project.meet.signaling.application.MeetingRuntimeRegistry;
import com.project.meet.signaling.application.RoomCommandDispatcher;
import com.project.meet.signaling.application.RuntimeParticipant;
import com.project.meet.signaling.domain.SignalingEnvelope;
import com.project.meet.signaling.domain.SignalingMessageType;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.UUID;

public class SignalingWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(SignalingWebSocketHandler.class);

	private final MeetingRuntimeRegistry runtimeRegistry;
	private final RoomCommandDispatcher dispatcher;
	private final MeetingRepository meetingRepository;
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public SignalingWebSocketHandler(
			MeetingRuntimeRegistry runtimeRegistry,
			RoomCommandDispatcher dispatcher,
			MeetingRepository meetingRepository,
			UserRepository userRepository,
			ObjectMapper objectMapper
	) {
		this.runtimeRegistry = runtimeRegistry;
		this.dispatcher = dispatcher;
		this.meetingRepository = meetingRepository;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		AuthenticatedUser user = authenticatedUser(session);
		UUID meetingId = meetingId(session);
		log.info("WEBSOCKET_CONNECTED meetingId={} userId={}", meetingId, user.userId());
		dispatcher.dispatch(meetingId, () -> handleJoin(session, meetingId, user));
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		UUID meetingId = meetingId(session);
		dispatcher.dispatch(meetingId, () -> routeMessage(session, meetingId, message.getPayload()));
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		UUID meetingId = meetingId(session);
		log.info("WEBSOCKET_DISCONNECTED meetingId={} status={}", meetingId, status);
		dispatcher.dispatch(meetingId, () -> handleLeave(session, meetingId));
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
	}

	// --- room commands (always run inside the room's serialized dispatcher queue) ---

	private void handleJoin(WebSocketSession session, UUID meetingId, AuthenticatedUser user) {
		Meeting meeting = meetingRepository.findById(meetingId).orElse(null);
		if (meeting == null) {
			closeQuietly(session, CloseStatus.NOT_ACCEPTABLE.withReason("Meeting not found"));
			return;
		}
		User dbUser = userRepository.findById(user.userId()).orElse(null);
		String displayName = dbUser != null ? dbUser.getDisplayName() : user.email();
		ParticipantRole role = meeting.isHostedBy(user.userId()) ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT;

		MeetingRuntime runtime = runtimeRegistry.getOrCreate(meetingId);

		// Snapshot the room for the newly-joined client: one PARTICIPANT_JOINED
		// per existing participant, sent only to them (not broadcast).
		for (RuntimeParticipant existing : runtime.participants()) {
			send(session, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId,
					existing.getUserId(), participantPayload(existing)));
		}

		RuntimeParticipant participant = new RuntimeParticipant(session, user.userId(), displayName, role);
		runtime.add(session.getId(), participant);

		broadcast(runtime, session.getId(),
				SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId, user.userId(), participantPayload(participant)));
	}

	private void handleLeave(WebSocketSession session, UUID meetingId) {
		MeetingRuntime runtime = runtimeRegistry.get(meetingId);
		if (runtime == null) {
			return;
		}
		RuntimeParticipant participant = runtime.remove(session.getId());
		if (participant == null) {
			return;
		}

		broadcast(runtime, session.getId(),
				SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_LEFT, meetingId, participant.getUserId(), participantPayload(participant)));

		if (runtime.isEmpty()) {
			runtimeRegistry.evictIfEmpty(meetingId);
			dispatcher.dropRoom(meetingId);
		}
	}

	private void routeMessage(WebSocketSession session, UUID meetingId, String rawJson) {
		MeetingRuntime runtime = runtimeRegistry.get(meetingId);
		if (runtime == null) {
			return;
		}

		SignalingEnvelope incoming;
		try {
			incoming = objectMapper.readValue(rawJson, SignalingEnvelope.class);
		} catch (Exception ex) {
			send(session, SignalingEnvelope.error(meetingId, "MALFORMED_MESSAGE", "Could not parse signaling message"));
			return;
		}

		AuthenticatedUser user = authenticatedUser(session);
		// senderId/meetingId are always stamped server-side — a client can never speak as someone else.
		SignalingEnvelope envelope = incoming.withServerMetadata(meetingId, user.userId());

		if (envelope.type() == SignalingMessageType.PING) {
			RuntimeParticipant participant = runtime.get(session.getId());
			if (participant != null) {
				participant.recordHeartbeat();
			}
			send(session, new SignalingEnvelope(SignalingMessageType.PONG, envelope.requestId(), meetingId,
					user.userId(), null, System.currentTimeMillis(), null));
			return;
		}

		if (envelope.type() == SignalingMessageType.MEETING_JOIN || envelope.type() == SignalingMessageType.MEETING_LEAVE) {
			// Presence is driven by the WebSocket connection lifecycle itself
			// (afterConnectionEstablished / afterConnectionClosed); these are
			// accepted for protocol symmetry but don't trigger extra action.
			return;
		}

		if (envelope.targetId() != null) {
			sendToTarget(runtime, envelope);
		} else {
			broadcast(runtime, session.getId(), envelope);
		}
	}

	// --- helpers ---

	private void sendToTarget(MeetingRuntime runtime, SignalingEnvelope envelope) {
		for (RuntimeParticipant participant : runtime.participants()) {
			if (participant.getUserId().equals(envelope.targetId())) {
				send(participant.getSession(), envelope);
				return;
			}
		}
	}

	private void broadcast(MeetingRuntime runtime, String excludingSessionId, SignalingEnvelope envelope) {
		for (RuntimeParticipant participant : runtime.participants()) {
			if (!participant.getSession().getId().equals(excludingSessionId)) {
				send(participant.getSession(), envelope);
			}
		}
	}

	private void send(WebSocketSession session, SignalingEnvelope envelope) {
		if (!session.isOpen()) {
			return;
		}
		try {
			String json = objectMapper.writeValueAsString(envelope);
			// WebSocketSession.sendMessage is not safe for concurrent calls
			// from multiple threads on the same session (Spring's own
			// caveat) — synchronize defensively even though, in practice,
			// per-room command ordering already serializes these calls.
			synchronized (session) {
				session.sendMessage(new TextMessage(json));
			}
		} catch (IOException ex) {
			log.debug("Failed to send to session {}: {}", session.getId(), ex.getMessage());
		} catch (IllegalStateException ex) {
			// The isOpen() check above is inherently racy — a session can
			// finish closing in the gap between that check and this send
			// (e.g. two participants disconnecting at nearly the same
			// instant). Tomcat's WsSession signals that as
			// IllegalStateException rather than IOException; either way it
			// just means "this recipient is gone", not a real failure.
			log.debug("Session {} closed before send completed: {}", session.getId(), ex.getMessage());
		}
	}

	private void closeQuietly(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		} catch (IOException ignored) {
			// session already closing/closed
		}
	}

	private ObjectNode participantPayload(RuntimeParticipant participant) {
		return JsonNodeFactory.instance.objectNode()
				.put("userId", participant.getUserId().toString())
				.put("displayName", participant.getDisplayName())
				.put("role", participant.getRole().name());
	}

	private AuthenticatedUser authenticatedUser(WebSocketSession session) {
		return (AuthenticatedUser) session.getAttributes().get(SignalingHandshakeInterceptor.ATTR_USER);
	}

	private UUID meetingId(WebSocketSession session) {
		return (UUID) session.getAttributes().get(SignalingHandshakeInterceptor.ATTR_MEETING_ID);
	}
}
