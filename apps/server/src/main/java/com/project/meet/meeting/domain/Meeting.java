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

	// Not DB-NOT-NULL: this column was added to a table with pre-existing
	// rows, and Postgres refuses ALTER TABLE ... ADD COLUMN ... NOT NULL
	// when existing rows would violate it (ddl-auto=update can't backfill
	// data, only structure — see docs/database/schema.md). Every row this
	// application writes always has a value (CreateMeetingUseCase defaults
	// to MESH), so this is enforced at the Java/service layer instead.
	@Enumerated(EnumType.STRING)
	@Column(name = "media_mode", length = 10)
	private MediaMode mediaMode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "scheduled_start_at")
	private Instant scheduledStartAt;

	@Column(name = "scheduled_end_at")
	private Instant scheduledEndAt;

	@Column(name = "timezone", length = 80)
	private String timezone;

	protected Meeting() {
		// JPA
	}

	public Meeting(String code, String title, User host, MeetingAccessType accessType) {
		this(code, title, host, accessType, null, null, null, MediaMode.MESH);
	}

	public Meeting(String code, String title, User host, MeetingAccessType accessType,
				   Instant scheduledStartAt, Instant scheduledEndAt, String timezone, MediaMode mediaMode) {
		this.code = code;
		this.title = title;
		this.host = host;
		this.accessType = accessType;
		this.scheduledStartAt = scheduledStartAt;
		this.scheduledEndAt = scheduledEndAt;
		this.timezone = timezone;
		this.mediaMode = mediaMode;
		this.status = scheduledStartAt == null ? MeetingStatus.CREATED : MeetingStatus.SCHEDULED;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void markActive() {
		if (this.status == MeetingStatus.CREATED || this.status == MeetingStatus.SCHEDULED) {
			this.status = MeetingStatus.ACTIVE;
			this.startedAt = Instant.now();
		}
	}

	public void end() {
		this.status = MeetingStatus.ENDED;
		this.endedAt = Instant.now();
	}

	public boolean isEnded() {
		return this.status == MeetingStatus.ENDED || this.status == MeetingStatus.CANCELLED;
	}

	public boolean isScheduledAndNotStarted() {
		return this.status == MeetingStatus.SCHEDULED;
	}

	public void cancel() {
		if (this.status == MeetingStatus.SCHEDULED) {
			this.status = MeetingStatus.CANCELLED;
			this.endedAt = Instant.now();
		}
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

	public MediaMode getMediaMode() {
		return mediaMode;
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

	public Instant getScheduledStartAt() { return scheduledStartAt; }

	public Instant getScheduledEndAt() { return scheduledEndAt; }

	public String getTimezone() { return timezone; }
}
