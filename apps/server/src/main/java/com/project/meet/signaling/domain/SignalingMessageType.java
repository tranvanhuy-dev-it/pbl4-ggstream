package com.project.meet.signaling.domain;

public enum SignalingMessageType {
	MEETING_JOIN,
	MEETING_LEAVE,

	PARTICIPANT_JOINED,
	PARTICIPANT_LEFT,
	/** Sent to everyone else when a dropped participant reconnects within the grace period — same identity, new transport, no LEFT/JOINED flicker. Milestone 8. */
	PARTICIPANT_RECONNECTED,

	/** Relayed point-to-point via {@code targetId}; payload is opaque SDP/ICE data. Wired up starting Milestone 4. */
	WEBRTC_OFFER,
	WEBRTC_ANSWER,
	ICE_CANDIDATE,

	MIC_STATE_CHANGED,
	CAMERA_STATE_CHANGED,

	SCREEN_SHARE_STARTED,
	SCREEN_SHARE_STOPPED,

	CHAT_MESSAGE,

	PARTICIPANT_WAITING,
	PARTICIPANT_APPROVED,
	PARTICIPANT_REJECTED,

	HOST_MUTE_PARTICIPANT,
	HOST_REMOVE_PARTICIPANT,
	MEETING_ENDED,

	PING,
	PONG,

	ERROR
}
