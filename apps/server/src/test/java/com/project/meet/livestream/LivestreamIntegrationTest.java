package com.project.meet.livestream;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.livestream.api.LivestreamStatusResponse;
import com.project.meet.livestream.api.StartLivestreamRequest;
import com.project.meet.livestream.application.FakeLivestreamProcessLauncher;
import com.project.meet.livestream.application.LivestreamProcessLauncher;
import com.project.meet.livestream.domain.LivestreamStatus;
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
 * Exercises the standalone livestream REST surface over real HTTP — no
 * meeting created or touched anywhere, see docs/networking/livestream.md.
 * The FFmpeg process launcher is swapped for {@link FakeLivestreamProcessLauncher}
 * (see {@link Config}) so this suite never depends on FFmpeg actually being
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

	@Test
	void broadcasterStartsAndStopsALivestreamWithNoMeetingInvolved() {
		String token = registerAndGetToken("live-host", "Host");

		ResponseEntity<LivestreamStatusResponse> start = restTemplate.exchange(
				"/api/v1/live/start", HttpMethod.POST,
				new HttpEntity<>(new StartLivestreamRequest("Buổi phát của tôi"), authHeaders(token)), LivestreamStatusResponse.class);
		assertThat(start.getStatusCode()).isEqualTo(HttpStatus.OK);
		LivestreamStatusResponse started = start.getBody();
		assertThat(started.status()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(started.id()).isNotBlank();
		assertThat(started.hlsUrl()).isEqualTo("/livestreams/" + started.id() + "/stream.m3u8");

		// Public — a viewer isn't an account of any kind, no auth header sent.
		ResponseEntity<LivestreamStatusResponse> statusWhileLive = restTemplate.getForEntity(
				"/api/v1/live/" + started.code() + "/status", LivestreamStatusResponse.class);
		assertThat(statusWhileLive.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(statusWhileLive.getBody().status()).isEqualTo(LivestreamStatus.LIVE);
		assertThat(statusWhileLive.getBody().title()).isEqualTo("Buổi phát của tôi");
		// The public endpoint never leaks the control id needed to stop it.
		assertThat(statusWhileLive.getBody().id()).isNull();

		ResponseEntity<Void> stop = restTemplate.exchange(
				"/api/v1/live/" + started.id() + "/stop", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(token)), Void.class);
		assertThat(stop.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<LivestreamStatusResponse> statusAfterStop = restTemplate.getForEntity(
				"/api/v1/live/" + started.code() + "/status", LivestreamStatusResponse.class);
		assertThat(statusAfterStop.getBody().status()).isEqualTo(LivestreamStatus.ENDED);
	}

	@Test
	void onlyTheBroadcasterWhoStartedItCanStopIt() {
		String hostToken = registerAndGetToken("live-owner", "Owner");
		String otherToken = registerAndGetToken("live-other", "Other");

		LivestreamStatusResponse started = restTemplate.exchange(
				"/api/v1/live/start", HttpMethod.POST,
				new HttpEntity<>(new StartLivestreamRequest(null), authHeaders(hostToken)), LivestreamStatusResponse.class).getBody();

		ResponseEntity<String> response = restTemplate.exchange(
				"/api/v1/live/" + started.id() + "/stop", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(otherToken)), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void statusForAnUnknownCodeIsEndedNot404() {
		ResponseEntity<LivestreamStatusResponse> response = restTemplate.getForEntity(
				"/api/v1/live/never-existed-code/status", LivestreamStatusResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().status()).isEqualTo(LivestreamStatus.ENDED);
	}

	@Test
	void startingWithNoBodyDefaultsTheTitle() {
		String token = registerAndGetToken("live-nobody", "Nobody");

		ResponseEntity<LivestreamStatusResponse> response = restTemplate.exchange(
				"/api/v1/live/start", HttpMethod.POST,
				new HttpEntity<>(null, authHeaders(token)), LivestreamStatusResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().title()).isEqualTo("Livestream");
	}
}
