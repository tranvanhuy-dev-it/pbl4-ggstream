package com.project.meet.meeting.domain;

/**
 * Which WebRTC transport a meeting's participants use. Fixed at creation
 * time, like {@link MeetingAccessType} — switching an in-progress meeting's
 * transport mid-call is out of scope (see docs/networking/webrtc.md).
 */
public enum MediaMode {
	/** Every participant connects directly to every other — Milestone 4/6. */
	MESH,
	/** Every participant connects once to a LiveKit SFU, which forwards to everyone else — Milestone 11. */
	SFU
}
