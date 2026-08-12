package com.project.meet.meeting.domain;

public enum MeetingStatus {
	CREATED,
	/** Reserved for the approval-required waiting room flow (Milestone 7). */
	WAITING,
	ACTIVE,
	ENDED
}
