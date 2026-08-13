package com.project.meet.participant.domain;

import com.project.meet.meeting.domain.Meeting;
import com.project.meet.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single join/leave session for a user in a meeting. Re-joining after
 * leaving creates a new row rather than reusing one, so the history of a
 * meeting's attendance is fully reconstructable.
 */
@Entity
@Table(name = "meeting_participants",
		indexes = @Index(name = "idx_meeting_participants_meeting_id", columnList = "meeting_id"))
public class MeetingParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meeting_id", nullable = false)
	private Meeting meeting;

	/** Nullable to leave room for anonymous/guest participants in a future milestone. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ParticipantRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Column(name = "left_at")
	private Instant leftAt;

	protected MeetingParticipant() {
		// JPA
	}

	public MeetingParticipant(Meeting meeting, User user, String displayName, ParticipantRole role) {
		this.meeting = meeting;
		this.user = user;
		this.displayName = displayName;
		this.role = role;
	}

	@PrePersist
	void onCreate() {
		this.joinedAt = Instant.now();
	}

	public void leave() {
		if (this.leftAt == null) {
			this.leftAt = Instant.now();
		}
	}

	public boolean isActive() {
		return this.leftAt == null;
	}

	public UUID getId() {
		return id;
	}

	public Meeting getMeeting() {
		return meeting;
	}

	public User getUser() {
		return user;
	}

	public String getDisplayName() {
		return displayName;
	}

	public ParticipantRole getRole() {
		return role;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}

	public Instant getLeftAt() {
		return leftAt;
	}
}
