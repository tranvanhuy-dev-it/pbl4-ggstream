package com.project.meet.meeting.application;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.meeting.api.CreateMeetingRequest;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.Meeting;
import com.project.meet.meeting.domain.MeetingAccessType;
import com.project.meet.meeting.infrastructure.MeetingRepository;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import com.project.meet.common.exception.ApiException;
import org.springframework.http.HttpStatus;

@Component
public class CreateMeetingUseCase {

	private final MeetingRepository meetingRepository;
	private final UserRepository userRepository;
	private final MeetingCodeGenerator codeGenerator;

	public CreateMeetingUseCase(MeetingRepository meetingRepository, UserRepository userRepository, MeetingCodeGenerator codeGenerator) {
		this.meetingRepository = meetingRepository;
		this.userRepository = userRepository;
		this.codeGenerator = codeGenerator;
	}

	@Transactional
	public MeetingResponse execute(UUID hostUserId, CreateMeetingRequest request) {
		User host = userRepository.findById(hostUserId)
				.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "Người dùng không còn tồn tại"));

		MeetingAccessType accessType = request.accessType() != null ? request.accessType() : MeetingAccessType.PUBLIC;

		validateSchedule(request);
		Meeting meeting = new Meeting(generateUniqueCode(), request.title().trim(), host, accessType,
				request.scheduledStartAt(), request.scheduledEndAt(),
				request.scheduledStartAt() == null ? null : (request.timezone() == null ? "UTC" : request.timezone()));
		meetingRepository.save(meeting);

		return MeetingResponse.from(meeting);
	}

	private void validateSchedule(CreateMeetingRequest request) {
		if (request.scheduledStartAt() == null) {
			if (request.scheduledEndAt() != null || request.timezone() != null) {
				throw new ApiException("INVALID_SCHEDULE", HttpStatus.BAD_REQUEST, "Thời gian bắt đầu là bắt buộc");
			}
			return;
		}
		if (!request.scheduledStartAt().isAfter(Instant.now())) {
			throw new ApiException("INVALID_SCHEDULE", HttpStatus.BAD_REQUEST, "Thời gian bắt đầu phải ở tương lai");
		}
		if (request.scheduledEndAt() == null || !request.scheduledEndAt().isAfter(request.scheduledStartAt())) {
			throw new ApiException("INVALID_SCHEDULE", HttpStatus.BAD_REQUEST, "Thời gian kết thúc phải sau thời gian bắt đầu");
		}
		try {
			ZoneId.of(request.timezone() == null ? "UTC" : request.timezone());
		} catch (Exception ex) {
			throw new ApiException("INVALID_TIMEZONE", HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ");
		}
	}

	private String generateUniqueCode() {
		String code;
		do {
			code = codeGenerator.generate();
		} while (meetingRepository.existsByCode(code));
		return code;
	}
}
