package com.project.meet.sfu.domain;

import java.util.UUID;

/**
 * Seam between the meeting domain and whichever SFU actually forwards
 * media for {@code SFU}-mode meetings (see {@link com.project.meet.meeting.domain.MediaMode}).
 * {@code Meeting}/its use cases depend on this interface, not a concrete
 * media server — {@code LiveKitMediaServer} (package {@code sfu.infrastructure})
 * is the only implementation today, but nothing outside that class
 * references LiveKit's SDK types directly.
 */
public interface MediaServer {

	/**
	 * Idempotent — safe to call even if the room already exists (e.g.
	 * LiveKit's own auto-room-creation-on-join already created it). Network
	 * calls to the SFU are best-effort: a failure here is logged, not
	 * thrown, since it must never block a REST request that otherwise
	 * succeeded.
	 */
	MediaRoom createRoom(UUID meetingId);

	/** Also best-effort/non-throwing — see {@link #createRoom(UUID)}. */
	void closeRoom(UUID meetingId);

	/**
	 * Issues a short-lived, per-user credential for joining this meeting's
	 * SFU room — the SFU equivalent of {@code TurnCredentialService}'s
	 * derived TURN credentials. The shared secret this depends on never
	 * reaches the client, only the signed token does.
	 */
	MediaParticipantConfig createParticipant(UUID meetingId, UUID userId, String displayName);
}
