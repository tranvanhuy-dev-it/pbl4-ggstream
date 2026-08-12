package com.project.meet.sfu;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.common.exception.ApiError;
import com.project.meet.meeting.api.CreateMeetingRequest;
import com.project.meet.meeting.api.MeetingResponse;
import com.project.meet.meeting.domain.MediaMode;
import com.project.meet.sfu.api.SfuTokenResponse;
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

/**
 * Exercises the SFU token endpoint over real HTTP — the test config's
 * LiveKit key/secret (src/test/resources/application.yml) are enough for
 * {@code IssueSfuTokenUseCase} to sign a real token; no live LiveKit
 * server is needed since token issuance is pure local JWT signing (see
 * LiveKitMediaServerTest).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class SfuIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	private String registerAndGetToken(String emailPrefix, String displayName) {
		String email = emailPrefix + "+" + System.nanoTime() + "@example.com";
		AuthResponse response = restTemplate.postForEntity(
				"/api/v1/auth/register", new RegisterRequest(email, "correct-horse-battery", displayName), AuthResponse.class).getBody();
		return response.accessToken();
	}

	private HttpHeaders authHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return headers;
	}

	@Test
	void sfuModeMeetingIssuesAUsableToken() {
		String hostToken = registerAndGetToken("sfu-host", "Host");

		MeetingResponse meeting = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Big all-hands", null, null, null, null, MediaMode.SFU), authHeaders(hostToken)),
				MeetingResponse.class).getBody();
		assertThat(meeting.mediaMode()).isEqualTo(MediaMode.SFU);

		ResponseEntity<SfuTokenResponse> tokenResponse = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/sfu-token", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(hostToken)), SfuTokenResponse.class);

		assertThat(tokenResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(tokenResponse.getBody().url()).isNotBlank();
		assertThat(tokenResponse.getBody().token().split("\\.")).hasSize(3);
	}

	@Test
	void meshModeMeetingRejectsTheSfuTokenRequest() {
		String hostToken = registerAndGetToken("mesh-host", "Host");

		MeetingResponse meeting = restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Small standup", null), authHeaders(hostToken)),
				MeetingResponse.class).getBody();
		assertThat(meeting.mediaMode()).isEqualTo(MediaMode.MESH);

		ResponseEntity<ApiError> response = restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/sfu-token", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(hostToken)), ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().code()).isEqualTo("NOT_AN_SFU_MEETING");
	}
}
