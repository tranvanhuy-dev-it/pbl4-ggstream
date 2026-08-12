package com.project.meet.sfu.infrastructure;

import com.project.meet.common.config.LiveKitProperties;
import com.project.meet.sfu.domain.MediaParticipantConfig;
import com.project.meet.sfu.domain.MediaRoom;
import com.project.meet.sfu.domain.MediaServer;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import retrofit2.Response;

import java.util.UUID;

/**
 * Drives a self-hosted LiveKit server (see docs/networking/sfu-setup.md) —
 * the only class in this codebase that touches LiveKit's SDK types
 * directly, everything else depends on {@link MediaServer}.
 *
 * Room lifecycle calls ({@link #createRoom}/{@link #closeRoom}) go over the
 * network to the LiveKit server and are treated as best-effort: LiveKit
 * would auto-create a room on first join anyway, and a meeting ending
 * shouldn't fail just because the SFU happens to be unreachable at that
 * moment. Token issuance ({@link #createParticipant}) is pure local JWT
 * signing — no network call, so it can't fail for that reason.
 */
@Component
public class LiveKitMediaServer implements MediaServer {

	private static final Logger log = LoggerFactory.getLogger(LiveKitMediaServer.class);

	private final LiveKitProperties properties;
	private final RoomServiceClient roomServiceClient;

	public LiveKitMediaServer(LiveKitProperties properties) {
		this.properties = properties;
		this.roomServiceClient = properties.isConfigured()
				? RoomServiceClient.create(toHttpUrl(properties.url()), properties.apiKey(), properties.apiSecret(), true)
				: null;
	}

	@Override
	public MediaRoom createRoom(UUID meetingId) {
		String roomName = roomName(meetingId);
		if (roomServiceClient != null) {
			try {
				Response<?> response = roomServiceClient.createRoom(roomName).execute();
				if (!response.isSuccessful()) {
					log.warn("LiveKit createRoom for meeting {} returned HTTP {}", meetingId, response.code());
				}
			} catch (Exception ex) {
				log.warn("LiveKit createRoom failed for meeting {}: {}", meetingId, ex.getMessage());
			}
		}
		return new MediaRoom(meetingId, roomName);
	}

	@Override
	public void closeRoom(UUID meetingId) {
		if (roomServiceClient == null) {
			return;
		}
		try {
			roomServiceClient.deleteRoom(roomName(meetingId)).execute();
		} catch (Exception ex) {
			log.warn("LiveKit closeRoom failed for meeting {}: {}", meetingId, ex.getMessage());
		}
	}

	@Override
	public MediaParticipantConfig createParticipant(UUID meetingId, UUID userId, String displayName) {
		AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
		token.setIdentity(userId.toString());
		token.setName(displayName);
		token.addGrants(new RoomJoin(true), new RoomName(roomName(meetingId)));
		return new MediaParticipantConfig(properties.url(), token.toJwt());
	}

	private String roomName(UUID meetingId) {
		return meetingId.toString();
	}

	/** RoomServiceClient talks REST, not WebSocket — LIVEKIT_URL is the ws(s):// URL the browser connects with. */
	private String toHttpUrl(String wsUrl) {
		if (wsUrl.startsWith("wss://")) {
			return "https://" + wsUrl.substring("wss://".length());
		}
		if (wsUrl.startsWith("ws://")) {
			return "http://" + wsUrl.substring("ws://".length());
		}
		return wsUrl;
	}
}
