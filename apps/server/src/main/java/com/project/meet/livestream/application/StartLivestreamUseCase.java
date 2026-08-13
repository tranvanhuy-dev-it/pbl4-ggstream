package com.project.meet.livestream.application;

import com.project.meet.common.config.LivestreamProperties;
import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import com.project.meet.livestream.domain.LivestreamAlreadyLiveException;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingEndedException;
import com.project.meet.meeting.domain.NotMeetingHostException;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Host-only — starts an FFmpeg process that will transcode whatever the
 * broadcaster streams to the ingest WebSocket into HLS. This only spawns
 * the process and registers the session; the actual media bytes arrive
 * later over {@code LivestreamIngestWebSocketHandler}, not here.
 */
@Component
public class StartLivestreamUseCase {

	private final MeetingRepository meetingRepository;
	private final LivestreamRegistry registry;
	private final LivestreamProcessLauncher processLauncher;
	private final LivestreamProperties properties;

	public StartLivestreamUseCase(
			MeetingRepository meetingRepository,
			LivestreamRegistry registry,
			LivestreamProcessLauncher processLauncher,
			LivestreamProperties properties
	) {
		this.meetingRepository = meetingRepository;
		this.registry = registry;
		this.processLauncher = processLauncher;
		this.properties = properties;
	}

	public LivestreamStatusResponse execute(UUID meetingId, UUID requesterUserId) {
		Meeting meeting = meetingRepository.findById(meetingId)
				.orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Không tìm thấy cuộc họp"));
		if (!meeting.isHostedBy(requesterUserId)) {
			throw new NotMeetingHostException();
		}
		if (meeting.isEnded()) {
			throw new MeetingEndedException();
		}
		if (registry.get(meetingId) != null) {
			throw new LivestreamAlreadyLiveException();
		}

		Path playlistPath = Path.of(properties.outputDir(), meetingId.toString(), "stream.m3u8");
		try {
			Files.createDirectories(playlistPath.getParent());
		} catch (IOException ex) {
			throw new IllegalStateException("Không thể tạo thư mục output cho livestream", ex);
		}

		LivestreamProcessHandle process;
		try {
			process = processLauncher.launch(meetingId, playlistPath);
		} catch (IOException ex) {
			throw new IllegalStateException("Không thể khởi động tiến trình FFmpeg", ex);
		}

		String hlsUrl = "/livestreams/" + meetingId + "/stream.m3u8";
		registry.put(meetingId, new LivestreamSession(meetingId, requesterUserId, hlsUrl, process));
		return LivestreamStatusResponse.live(hlsUrl);
	}
}
