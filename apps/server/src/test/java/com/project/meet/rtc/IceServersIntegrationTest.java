package com.project.meet.rtc;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.rtc.api.IceServersResponse;
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
class IceServersIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void authenticatedUserReceivesStunAndTurnServers() {
		String email = "ice+" + System.nanoTime() + "@example.com";
		String token = restTemplate.postForEntity(
				"/api/v1/auth/register", new RegisterRequest(email, "correct-horse-battery", "Ice Tester"), AuthResponse.class)
				.getBody().accessToken();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<IceServersResponse> response = restTemplate.exchange(
				"/api/v1/rtc/ice-servers", HttpMethod.GET, new HttpEntity<>(headers), IceServersResponse.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().iceServers()).hasSizeGreaterThanOrEqualTo(1);
	}

	@Test
	void unauthenticatedRequestIsRejected() {
		ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/rtc/ice-servers", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
