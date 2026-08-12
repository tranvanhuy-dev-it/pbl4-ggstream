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

		Meeting meeting = new Meeting(generateUniqueCode(), request.title().trim(), host, accessType);
		meetingRepository.save(meeting);

		return MeetingResponse.from(meeting);
	}

	private String generateUniqueCode() {
		String code;
		do {
			code = codeGenerator.generate();
		} while (meetingRepository.existsByCode(code));
		return code;
	}
}
