package com.project.meet.livestream;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import com.project.meet.livestream.application.FakeLivestreamProcessLauncher;
import com.project.meet.livestream.application.LivestreamProcessLauncher;
import com.project.meet.livestream.domain.LivestreamStatus;
import com.project.meet.meeting.api.CreateMeetingRequest;
import com.project.meet.meeting.api.MeetingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the livestream REST surface over real HTTP. The FFmpeg process
 * launcher is swapped for {@link FakeLivestreamProcessLauncher} (see
 * {@link Config}) so this suite never depends on FFmpeg actually being
 * installed on the machine running it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LivestreamIntegrationTest {

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		LivestreamProcessLauncher fakeLivestreamProcessLauncher() {
			return new FakeLivestreamProcessLauncher();
		}
	}

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

	private MeetingResponse createMeeting(String hostToken) {
		return restTemplate.exchange(
				"/api/v1/meetings", HttpMethod.POST,
				new HttpEntity<>(new CreateMeetingRequest("Livestream test", null), authHeaders(hostToken)),
				MeetingResponse.class).getBody();
	}

	@Test
	void hostStartsAndStopsALivestream() {
		String hostToken = registerAndGetToken("livestream-host", "Host");
		String meetingId = createMeeting(hostToken).id().toString();

		ResponseEntity<LivestreamStatusResponse> start = restTemplate.exchange(
				"/api/v1/meetings/" + meetingId + "/livestream/start", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(hostToken)), LivestreamStatusResponse.class);
		assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(start.getBody().status()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(start.getBody().hlsUrl()).isEqualTo("/livestreams/" + meetingId + "/stream.m3u8");

		// Public — a viewer isn't a meeting participant, no auth header sent.
		ResponseEntity<LivestreamStatusResponse> statusWhileLive = restTemplate.getForEntity(
				"/api/v1/meetings/" + meetingId + "/livestream", LivestreamStatusResponse.class);
		assertThat(statusWhileLive.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(statusWhileLive.getBody().status()).isEqualTo(LivestreamStatus.LIVE);

		ResponseEntity<Void> stop = restTemplate.exchange(
				"/api/v1/meetings/" + meetingId + "/livestream/stop", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(hostToken)), Void.class);
		assertThat(stop.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<LivestreamStatusResponse> statusAfterStop = restTemplate.getForEntity(
				"/api/v1/meetings/" + meetingId + "/livestream", LivestreamStatusResponse.class);
		assertThat(statusAfterStop.getBody().status()).isEqualTo(LivestreamStatus.ENDED);
	}

	@Test
	void nonHostCannotStartALivestream() {
		String hostToken = registerAndGetToken("livestream-host2", "Host");
		String guestToken = registerAndGetToken("livestream-guest2", "Guest");
		String meetingId = createMeeting(hostToken).id().toString();

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/meetings/" + meetingId + "/livestream/start", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(guestToken)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void statusForAMeetingThatNeverWentLiveIsEnded() {
		String hostToken = registerAndGetToken("livestream-host3", "Host");
		String meetingId = createMeeting(hostToken).id().toString();

		ResponseEntity<LivestreamStatusResponse> response = restTemplate.getForEntity(
				"/api/v1/meetings/" + meetingId + "/livestream", LivestreamStatusResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().status()).isEqualTo(LivestreamStatus.ENDED);
	}

	@Test
	void viewerCanResolveLivestreamStatusByJoinCode() {
		String hostToken = registerAndGetToken("livestream-host4", "Host");
		MeetingResponse meeting = createMeeting(hostToken);

		restTemplate.exchange(
				"/api/v1/meetings/" + meeting.id() + "/livestream/start", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(hostToken)), LivestreamStatusResponse.class);

		// Public — resolved purely from the join code, no auth, no meeting UUID known up front.
		ResponseEntity<LivestreamStatusResponse> response = restTemplate.getForEntity(
				"/api/v1/meetings/code/" + meeting.code() + "/livestream", LivestreamStatusResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().status()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(response.getBody().hlsUrl()).isEqualTo("/livestreams/" + meeting.id() + "/stream.m3u8");
	}
}
