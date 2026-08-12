package com.project.meet.meeting.domain;

import com.project.meet.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "meetings", uniqueConstraints = @UniqueConstraint(name = "uk_meetings_code", columnNames = "code"))
public class Meeting {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String code;

	@Column(nullable = false)
	private String title;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "host_id", nullable = false)
	private User host;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MeetingStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "access_type", nullable = false, length = 20)
	private MeetingAccessType accessType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	protected Meeting() {
		// JPA
	}

	public Meeting(String code, String title, User host, MeetingAccessType accessType) {
		this.code = code;
		this.title = title;
		this.host = host;
		this.accessType = accessType;
		this.status = MeetingStatus.CREATED;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void markActive() {
		if (this.status == MeetingStatus.CREATED) {
			this.status = MeetingStatus.ACTIVE;
			this.startedAt = Instant.now();
		}
	}

	public void end() {
		this.status = MeetingStatus.ENDED;
		this.endedAt = Instant.now();
	}

	public boolean isEnded() {
		return this.status == MeetingStatus.ENDED;
	}

	public boolean isHostedBy(UUID userId) {
		return this.host.getId().equals(userId);
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getTitle() {
		return title;
	}

	public User getHost() {
		return host;
	}

	public MeetingStatus getStatus() {
		return status;
	}

	public MeetingAccessType getAccessType() {
		return accessType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}
}
