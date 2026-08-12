package com.project.meet.meeting.domain;

public enum MeetingAccessType {
	PUBLIC,
	INVITED,
	/** Enforced starting with the waiting room flow (Milestone 7); stored but not yet enforced. */
	APPROVAL_REQUIRED
}
