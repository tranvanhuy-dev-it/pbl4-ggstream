package com.project.meet.meeting;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.common.exception.ApiError;
import com.project.meet.meeting.api.CreateMeetingRequest;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.MeetingStatus;
import com.project.meet.participant.api.ParticipantResponse;
import com.project.meet.participant.domain.ParticipantRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class MeetingFlowIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	private String registerAndGetToken(String emailPrefix, String displayName) {
		String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
				"/api/v1/auth/register", new RegisterRequest(email, "correct-horse-battery", displayName), AuthResponse.class);
		return response.getBody().accessToken();
	}

	private HttpHeaders authHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	@Test
	void hostCreatesMeetingAndParticipantJoinsAndLeaves() {
		String hostToken = registerAndGetToken("host", "Host");
		String guestToken = registerAndGetToken("guest", "Guest");

		ResponseEntity<MeetingResponse> createResponse = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Team sync", null), authHeaders(hostToken)),
				MeetingResponse.class);

		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		MeetingResponse meeting = createResponse.getBody();
		assertThat(meeting.status()).isEqualTo(MeetingStatus.CREATED);
		assertThat(meeting.code()).matches("[a-z]{3}-[a-z]{4}-[a-z]{3}");

		ResponseEntity<MeetingResponse> byCode = restTemplate.exchange(
				"/api/v1/meetings/code/" + meeting.code(), HttpMethod.GET,
				new HttpEntity<>(authHeaders(guestToken)), MeetingResponse.class);
		assertThat(byCode.getBody().id()).isEqualTo(meeting.id());

		ResponseEntity<ParticipantResponse> hostJoin = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/join", HttpMethod.POST,
				new HttpEntity<>(authHeaders(hostToken)), ParticipantResponse.class);
		assertThat(hostJoin.getBody().role()).isEqualTo(ParticipantRole.HOST);

		ResponseEntity<ParticipantResponse> guestJoin = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/join", HttpMethod.POST,
				new HttpEntity<>(authHeaders(guestToken)), ParticipantResponse.class);
		assertThat(guestJoin.getBody().role()).isEqualTo(ParticipantRole.PARTICIPANT);

		ResponseEntity<MeetingResponse> afterJoin = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id(), HttpMethod.GET,
				new HttpEntity<>(authHeaders(hostToken)), MeetingResponse.class);
		assertThat(afterJoin.getBody().status()).isEqualTo(MeetingStatus.ACTIVE);

		ResponseEntity<ParticipantResponse[]> participants = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/participants", HttpMethod.GET,
				new HttpEntity<>(authHeaders(hostToken)), ParticipantResponse[].class);
		assertThat(participants.getBody()).hasSize(2);

		restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/leave", HttpMethod.POST,
				new HttpEntity<>(authHeaders(guestToken)), Void.class);

		ResponseEntity<ParticipantResponse[]> afterLeave = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/participants", HttpMethod.GET,
				new HttpEntity<>(authHeaders(hostToken)), ParticipantResponse[].class);
		assertThat(afterLeave.getBody()).hasSize(1);
	}

	@Test
	void joinIsIdempotentForAnAlreadyActiveParticipant() {
		String hostToken = registerAndGetToken("idempotent-host", "Host");

		MeetingResponse meeting = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Standup", null), authHeaders(hostToken)),
				MeetingResponse.class).getBody();

		ParticipantResponse first = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/join", HttpMethod.POST,
				new HttpEntity<>(authHeaders(hostToken)), ParticipantResponse.class).getBody();
		ParticipantResponse second = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/join", HttpMethod.POST,
				new HttpEntity<>(authHeaders(hostToken)), ParticipantResponse.class).getBody();

		assertThat(second.id()).isEqualTo(first.id());
	}

	@Test
	void onlyHostCanEndMeeting() {
		String hostToken = registerAndGetToken("end-host", "Host");
		String guestToken = registerAndGetToken("end-guest", "Guest");

		MeetingResponse meeting = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Planning", null), authHeaders(hostToken)),
				MeetingResponse.class).getBody();

		ResponseEntity<ApiError> forbidden = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/end", HttpMethod.POST,
				new HttpEntity<>(authHeaders(guestToken)), ApiError.class);
		assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbidden.getBody().code()).isEqualTo("NOT_MEETING_HOST");

		restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/join", HttpMethod.POST,
				new HttpEntity<>(authHeaders(hostToken)), ParticipantResponse.class);

		ResponseEntity<MeetingResponse> ended = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/end", HttpMethod.POST,
				new HttpEntity<>(authHeaders(hostToken)), MeetingResponse.class);
		assertThat(ended.getBody().status()).isEqualTo(MeetingStatus.ENDED);

		ResponseEntity<ApiError> joinAfterEnd = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/join", HttpMethod.POST,
				new HttpEntity<>(authHeaders(guestToken)), ApiError.class);
		assertThat(joinAfterEnd.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(joinAfterEnd.getBody().code()).isEqualTo("MEETING_ENDED");
	}

	@Test
	void leavingWithoutHavingJoinedIsRejected() {
		String hostToken = registerAndGetToken("leave-host", "Host");

		MeetingResponse meeting = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("No-show", null), authHeaders(hostToken)),
				MeetingResponse.class).getBody();

		ResponseEntity<ApiError> response = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/leave", HttpMethod.POST,
				new HttpEntity<>(authHeaders(hostToken)), ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().code()).isEqualTo("NOT_IN_MEETING");
	}

	@Test
	void gettingUnknownMeetingCodeReturns404() {
		String token = registerAndGetToken("lookup", "Lookup");

		ResponseEntity<ApiError> response = restTemplate.exchange(
				"/api/v1/meetings/code/does-not-exist", HttpMethod.GET,
				new HttpEntity<>(authHeaders(token)), ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().code()).isEqualTo("MEETING_NOT_FOUND");
	}
}
