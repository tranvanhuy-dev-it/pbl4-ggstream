package com.project.meet.signaling.infrastructure;

import com.project.meet.chat.api.ChatMessageResponse;
import com.project.meet.chat.application.SendChatMessageUseCase;
import com.project.meet.common.config.MeshProperties;
import com.project.meet.common.config.WebSocketProperties;
import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.meeting.domain.MediaMode;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingAccessType;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.participant.domain.ParticipantRole;
import com.project.meet.signaling.application.MeetingRuntime;
import com.project.meet.signaling.application.MeetingRuntimeRegistry;
import com.project.meet.signaling.application.RoomCommandDispatcher;
import com.project.meet.signaling.application.RuntimeParticipant;
import com.project.meet.signaling.application.WaitingParticipant;
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
import java.util.concurrent.TimeUnit;

public class SignalingWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(SignalingWebSocketHandler.class);

	private final MeetingRuntimeRegistry runtimeRegistry;
	private final RoomCommandDispatcher dispatcher;
	private final MeetingRepository meetingRepository;
	private final UserRepository userRepository;
	private final SendChatMessageUseCase sendChatMessageUseCase;
	private final ObjectMapper objectMapper;
	private final WebSocketProperties properties;
	private final MeshProperties meshProperties;

	public SignalingWebSocketHandler(
			MeetingRuntimeRegistry runtimeRegistry,
			RoomCommandDispatcher dispatcher,
			MeetingRepository meetingRepository,
			UserRepository userRepository,
			SendChatMessageUseCase sendChatMessageUseCase,
			ObjectMapper objectMapper,
			WebSocketProperties properties,
			MeshProperties meshProperties
	) {
		this.runtimeRegistry = runtimeRegistry;
		this.dispatcher = dispatcher;
		this.meetingRepository = meetingRepository;
		this.userRepository = userRepository;
		this.sendChatMessageUseCase = sendChatMessageUseCase;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.meshProperties = meshProperties;
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
		// A clean, client-initiated close (code 1000 — voluntary leave,
		// host-remove, waiting-room rejection all use this) skips the grace
		// period and is removed immediately. Anything else (network drop,
		// tab crash, heartbeat timeout) is treated as a possibly-temporary
		// disconnect that a reconnect within the grace window can undo — see
		// handleDisconnect.
		boolean clientInitiated = status.getCode() == CloseStatus.NORMAL.getCode();
		dispatcher.dispatch(meetingId, () -> handleDisconnect(session, meetingId, clientInitiated));
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
		if (meeting.isEnded() || meeting.isScheduledAndNotStarted()) {
			closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("Meeting is not active"));
			return;
		}
		User dbUser = userRepository.findById(user.userId()).orElse(null);
		String displayName = dbUser != null ? dbUser.getDisplayName() : user.email();
		boolean isHost = meeting.isHostedBy(user.userId());

		MeetingRuntime runtime = runtimeRegistry.getOrCreate(meetingId);

		// If this user was already an active participant — either still
		// connected via another session, or (far more commonly) mid-grace-
		// period after an unexpected disconnect — this is a reconnect, not a
		// fresh join: same identity, new transport. Re-point their existing
		// RuntimeParticipant at the new session instead of adding a second
		// roster entry, and skip the JOIN broadcast that would otherwise
		// flicker PARTICIPANT_LEFT/JOINED for everyone else.
		RuntimeParticipant reconnected = runtime.reconnect(user.userId(), session);
		if (reconnected != null) {
			log.info("WEBSOCKET_RECONNECTED meetingId={} userId={}", meetingId, user.userId());
			// The reconnecting client's own in-memory state may be gone
			// entirely (e.g. a full page reload after the network came
			// back), so it can't assume it still knows the roster — resend
			// the same snapshot a fresh join would get.
			for (RuntimeParticipant existing : runtime.participants()) {
				if (existing == reconnected) {
					continue;
				}
				send(session, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId,
						existing.getUserId(), participantPayload(existing)));
			}
			// Everyone else never saw a LEFT for this user, so no JOINED
			// either — just a signal that this specific peer's WebRTC
			// connection needs an ICE restart, since the underlying network
			// path just changed.
			broadcast(runtime, session.getId(),
					SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_RECONNECTED, meetingId, user.userId(), participantPayload(reconnected)));
			if (reconnected.getRole() == ParticipantRole.HOST) {
				for (WaitingParticipant waiting : runtime.waitingParticipants()) {
					send(session, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_WAITING, meetingId, waiting.getUserId(), waitingPayload(waiting)));
				}
			}
			return;
		}

		// Approval-required meetings hold non-host joiners in a waiting lane —
		// no roster snapshot, no broadcast to others — until the host approves
		// them (see handleApprovalDecision). This is purely a runtime/signaling
		// concept: the REST /join call already persisted their
		// meeting_participants row (Milestone 2 semantics are unchanged), this
		// only gates when they actually get to see/be seen in the room.
		if (!isHost && meeting.getAccessType() == MeetingAccessType.APPROVAL_REQUIRED) {
			WaitingParticipant waiting = new WaitingParticipant(session, user.userId(), displayName);
			runtime.addWaiting(session.getId(), waiting);
			send(session, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_WAITING, meetingId, user.userId(), waitingPayload(waiting)));
			sendToHosts(runtime, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_WAITING, meetingId, user.userId(), waitingPayload(waiting)));
			return;
		}

		// Mesh doesn't scale past a small room — every participant opens one
		// RTCPeerConnection per other participant (see
		// docs/architecture/scalability-plan.md). SFU-mode meetings have no
		// such limit here; LiveKit handles its own capacity separately.
		if (meeting.getMediaMode() == MediaMode.MESH && runtime.participants().size() >= meshProperties.maxParticipants()) {
			send(session, SignalingEnvelope.error(meetingId, "MESH_ROOM_FULL",
					"Phòng đã đạt giới hạn " + meshProperties.maxParticipants()
							+ " người cho chế độ kết nối trực tiếp — hãy tạo cuộc họp mới ở chế độ SFU cho phòng đông người."));
			closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("Mesh room full"));
			return;
		}

		// Snapshot the room for the newly-joined client: one PARTICIPANT_JOINED
		// per existing participant, sent only to them (not broadcast).
		for (RuntimeParticipant existing : runtime.participants()) {
			send(session, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId,
					existing.getUserId(), participantPayload(existing)));
		}

		ParticipantRole role = isHost ? ParticipantRole.HOST : ParticipantRole.PARTICIPANT;
		RuntimeParticipant participant = new RuntimeParticipant(session, user.userId(), displayName, role);
		runtime.add(session.getId(), participant);

		broadcast(runtime, session.getId(),
				SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId, user.userId(), participantPayload(participant)));

		// A host who just connected needs to see anyone already waiting —
		// they may have requested to join before the host's own socket was up.
		if (role == ParticipantRole.HOST) {
			for (WaitingParticipant waiting : runtime.waitingParticipants()) {
				send(session, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_WAITING, meetingId, waiting.getUserId(), waitingPayload(waiting)));
			}
		}
	}

	private void handleDisconnect(WebSocketSession session, UUID meetingId, boolean clientInitiated) {
		MeetingRuntime runtime = runtimeRegistry.get(meetingId);
		if (runtime == null) {
			return;
		}

		WaitingParticipant waiting = runtime.removeWaiting(session.getId());
		if (waiting != null) {
			sendToHosts(runtime, SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_LEFT, meetingId, waiting.getUserId(), waitingPayload(waiting)));
			evictRoomIfEmpty(runtime, meetingId);
			return;
		}

		RuntimeParticipant participant = runtime.get(session.getId());
		if (participant == null) {
			return;
		}

		if (clientInitiated || properties.reconnectGraceSeconds() <= 0) {
			finalizeRemoval(runtime, meetingId, session.getId());
			return;
		}

		// Keep the participant in the roster (so their tile doesn't vanish
		// for anyone) but mark them pending removal, and schedule the actual
		// removal for after the grace period. A reconnect before then
		// (MeetingRuntime.reconnect) re-points this same RuntimeParticipant
		// at a new session id, so when expireDisconnect eventually runs and
		// looks up this session id, it will find nothing there — the
		// reconnect already moved the roster entry to a different key — and
		// no-ops instead of removing them.
		participant.markDisconnected();
		String sessionId = session.getId();
		dispatcher.scheduleDispatch(meetingId, () -> expireDisconnect(meetingId, sessionId),
				properties.reconnectGraceSeconds(), TimeUnit.SECONDS);
	}

	private void expireDisconnect(UUID meetingId, String sessionId) {
		MeetingRuntime runtime = runtimeRegistry.get(meetingId);
		if (runtime == null) {
			return;
		}
		RuntimeParticipant participant = runtime.get(sessionId);
		if (participant == null || !participant.isPendingRemoval()) {
			// Either already removed, or a reconnect already re-keyed this
			// participant under a new session id — nothing to do.
			return;
		}
		finalizeRemoval(runtime, meetingId, sessionId);
	}

	private void finalizeRemoval(MeetingRuntime runtime, UUID meetingId, String sessionId) {
		RuntimeParticipant participant = runtime.remove(sessionId);
		if (participant == null) {
			return;
		}
		broadcast(runtime, sessionId,
				SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_LEFT, meetingId, participant.getUserId(), participantPayload(participant)));
		evictRoomIfEmpty(runtime, meetingId);
	}

	private void evictRoomIfEmpty(MeetingRuntime runtime, UUID meetingId) {
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
			send(session, SignalingEnvelope.error(meetingId, "MALFORMED_MESSAGE", "Không thể xử lý tin nhắn tín hiệu"));
			return;
		}

		AuthenticatedUser user = authenticatedUser(session);
		// senderId/meetingId are always stamped server-side — a client can never speak as someone else.
		SignalingEnvelope envelope = incoming.withServerMetadata(meetingId, user.userId());

		switch (envelope.type()) {
			case PING -> handlePing(session, runtime, meetingId, user, envelope);
			case MEETING_JOIN, MEETING_LEAVE -> {
				// Presence is driven by the WebSocket connection lifecycle itself
				// (afterConnectionEstablished / afterConnectionClosed); accepted
				// for protocol symmetry but don't trigger extra action.
			}
			case CHAT_MESSAGE -> handleChatMessage(session, runtime, meetingId, user, envelope);
			case PARTICIPANT_APPROVED, PARTICIPANT_REJECTED -> handleApprovalDecision(session, runtime, meetingId, envelope);
			case HOST_MUTE_PARTICIPANT -> relayIfSenderIsHost(session, runtime, envelope);
			case HOST_REMOVE_PARTICIPANT -> handleRemoveParticipant(session, runtime, meetingId, envelope);
			case MEETING_ENDED -> broadcastIfSenderIsHost(session, runtime, envelope);
			default -> {
				if (envelope.targetId() != null) {
					sendToTarget(runtime, envelope);
				} else {
					broadcast(runtime, session.getId(), envelope);
				}
			}
		}
	}

	private void handlePing(WebSocketSession session, MeetingRuntime runtime, UUID meetingId, AuthenticatedUser user, SignalingEnvelope envelope) {
		RuntimeParticipant participant = runtime.get(session.getId());
		if (participant != null) {
			participant.recordHeartbeat();
		}
		send(session, new SignalingEnvelope(SignalingMessageType.PONG, envelope.requestId(), meetingId,
				user.userId(), null, System.currentTimeMillis(), null));
	}

	private void handleChatMessage(WebSocketSession session, MeetingRuntime runtime, UUID meetingId, AuthenticatedUser user, SignalingEnvelope envelope) {
		String body = envelope.payload() != null && envelope.payload().has("body")
				? envelope.payload().get("body").asString()
				: null;
		if (body == null) {
			send(session, SignalingEnvelope.error(meetingId, "INVALID_CHAT_MESSAGE", "Nội dung tin nhắn là bắt buộc"));
			return;
		}

		ChatMessageResponse saved;
		try {
			saved = sendChatMessageUseCase.execute(meetingId, user.userId(), body);
		} catch (Exception ex) {
			send(session, SignalingEnvelope.error(meetingId, "INVALID_CHAT_MESSAGE", ex.getMessage()));
			return;
		}

		ObjectNode payload = JsonNodeFactory.instance.objectNode()
				.put("id", saved.id().toString())
				.put("senderId", saved.senderId().toString())
				.put("senderDisplayName", saved.senderDisplayName())
				.put("body", saved.body())
				.put("createdAt", saved.createdAt().toString());
		SignalingEnvelope out = SignalingEnvelope.broadcast(SignalingMessageType.CHAT_MESSAGE, meetingId, user.userId(), payload);

		// Everyone including the sender gets this back — the persisted id/
		// timestamp is the single source of truth for the message, rather
		// than the sender optimistically rendering its own unconfirmed copy.
		for (RuntimeParticipant participant : runtime.participants()) {
			send(participant.getSession(), out);
		}
	}

	private void handleApprovalDecision(WebSocketSession session, MeetingRuntime runtime, UUID meetingId, SignalingEnvelope envelope) {
		if (!isHost(runtime, session) || envelope.targetId() == null) {
			return;
		}

		WaitingParticipant waiting = runtime.removeWaitingByUserId(envelope.targetId());
		if (waiting == null) {
			return;
		}

		if (envelope.type() == SignalingMessageType.PARTICIPANT_REJECTED) {
			send(waiting.getSession(), SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_REJECTED, meetingId, waiting.getUserId(), null));
			closeQuietly(waiting.getSession(), CloseStatus.NORMAL.withReason("Join request denied"));
			if (runtime.isEmpty()) {
				runtimeRegistry.evictIfEmpty(meetingId);
				dispatcher.dropRoom(meetingId);
			}
			return;
		}

		for (RuntimeParticipant existing : runtime.participants()) {
			send(waiting.getSession(), SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId,
					existing.getUserId(), participantPayload(existing)));
		}

		RuntimeParticipant participant = new RuntimeParticipant(waiting.getSession(), waiting.getUserId(), waiting.getDisplayName(), ParticipantRole.PARTICIPANT);
		runtime.add(waiting.getSession().getId(), participant);

		send(waiting.getSession(), SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_APPROVED, meetingId, waiting.getUserId(), participantPayload(participant)));
		broadcast(runtime, waiting.getSession().getId(),
				SignalingEnvelope.broadcast(SignalingMessageType.PARTICIPANT_JOINED, meetingId, waiting.getUserId(), participantPayload(participant)));
	}

	private void relayIfSenderIsHost(WebSocketSession session, MeetingRuntime runtime, SignalingEnvelope envelope) {
		if (!isHost(runtime, session) || envelope.targetId() == null) {
			return;
		}
		sendToTarget(runtime, envelope);
	}

	private void broadcastIfSenderIsHost(WebSocketSession session, MeetingRuntime runtime, SignalingEnvelope envelope) {
		if (!isHost(runtime, session)) {
			return;
		}
		broadcast(runtime, session.getId(), envelope);
	}

	private void handleRemoveParticipant(WebSocketSession session, MeetingRuntime runtime, UUID meetingId, SignalingEnvelope envelope) {
		if (!isHost(runtime, session) || envelope.targetId() == null) {
			return;
		}
		for (RuntimeParticipant participant : runtime.participants()) {
			if (participant.getUserId().equals(envelope.targetId())) {
				send(participant.getSession(), SignalingEnvelope.broadcast(SignalingMessageType.HOST_REMOVE_PARTICIPANT, meetingId, envelope.senderId(), null));
				closeQuietly(participant.getSession(), CloseStatus.NORMAL.withReason("Removed by host"));
				return;
			}
		}
	}

	// --- helpers ---

	private boolean isHost(MeetingRuntime runtime, WebSocketSession session) {
		RuntimeParticipant participant = runtime.get(session.getId());
		return participant != null && participant.getRole() == ParticipantRole.HOST;
	}

	private void sendToTarget(MeetingRuntime runtime, SignalingEnvelope envelope) {
		for (RuntimeParticipant participant : runtime.participants()) {
			if (participant.getUserId().equals(envelope.targetId())) {
				send(participant.getSession(), envelope);
				return;
			}
		}
	}

	private void sendToHosts(MeetingRuntime runtime, SignalingEnvelope envelope) {
		for (RuntimeParticipant participant : runtime.participants()) {
			if (participant.getRole() == ParticipantRole.HOST) {
				send(participant.getSession(), envelope);
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

	private ObjectNode waitingPayload(WaitingParticipant waiting) {
		return JsonNodeFactory.instance.objectNode()
				.put("userId", waiting.getUserId().toString())
				.put("displayName", waiting.getDisplayName());
	}

	private AuthenticatedUser authenticatedUser(WebSocketSession session) {
		return (AuthenticatedUser) session.getAttributes().get(SignalingHandshakeInterceptor.ATTR_USER);
	}

	private UUID meetingId(WebSocketSession session) {
		return (UUID) session.getAttributes().get(SignalingHandshakeInterceptor.ATTR_MEETING_ID);
	}
}
