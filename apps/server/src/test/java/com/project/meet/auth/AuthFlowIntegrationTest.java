package com.project.meet.auth;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.LoginRequest;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.common.exception.ApiError;
import com.project.meet.user.api.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthFlowIntegrationTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Value("${local.server.port}")
	private int port;

	@Test
	void registerThenLoginThenAccessProtectedEndpoint() {
		String email = "alice+" + System.nanoTime() + "@example.com";
		RegisterRequest registerRequest = new RegisterRequest(email, "correct-horse-battery", "Alice");

		ResponseEntity<AuthResponse> registerResponse =
				restTemplate.postForEntity("/api/v1/auth/register", registerRequest, AuthResponse.class);

		assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(registerResponse.getBody()).isNotNull();
		assertThat(registerResponse.getBody().user().email()).isEqualTo(email);
		assertThat(registerResponse.getBody().accessToken()).isNotBlank();

		LoginRequest loginRequest = new LoginRequest(email, "correct-horse-battery");
		ResponseEntity<AuthResponse> loginResponse =
				restTemplate.postForEntity("/api/v1/auth/login", loginRequest, AuthResponse.class);

		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String token = loginResponse.getBody().accessToken();

		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		ResponseEntity<UserProfileResponse> meResponse = restTemplate.exchange(
				"/api/v1/users/me", org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), UserProfileResponse.class);

		assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(meResponse.getBody().email()).isEqualTo(email);
	}

	@Test
	void registerRejectsDuplicateEmail() {
		String email = "bob+" + System.nanoTime() + "@example.com";
		RegisterRequest request = new RegisterRequest(email, "correct-horse-battery", "Bob");

		restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);
		ResponseEntity<ApiError> secondAttempt =
				restTemplate.postForEntity("/api/v1/auth/register", request, ApiError.class);

		assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(secondAttempt.getBody().code()).isEqualTo("EMAIL_ALREADY_IN_USE");
	}

	@Test
	void loginRejectsWrongPassword() {
		String email = "carol+" + System.nanoTime() + "@example.com";
		restTemplate.postForEntity("/api/v1/auth/register",
				new RegisterRequest(email, "correct-horse-battery", "Carol"), AuthResponse.class);

		ResponseEntity<ApiError> response = restTemplate.postForEntity(
				"/api/v1/auth/login", new LoginRequest(email, "wrong-password"), ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void protectedEndpointRejectsMissingToken() {
		ResponseEntity<ApiError> response = restTemplate.getForEntity("/api/v1/users/me", ApiError.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
	}

	/**
	 * Regression test: a browser sends an unauthenticated OPTIONS preflight
	 * before any cross-origin request that needs one (POST with a JSON body,
	 * or any request carrying an Authorization header) — including requests
	 * targeting protected routes. If Spring Security rejects that preflight
	 * for lacking a JWT, the browser never sends the real request at all, so
	 * every authenticated call from the frontend silently fails CORS. Uses
	 * raw HttpClient because TestRestTemplate's default JDK
	 * HttpURLConnection-based request factory silently drops the "Origin"
	 * header (it's on that client's restricted-header list).
	 */
	@Test
	void preflightForProtectedEndpointSucceedsWithoutAuthentication() throws Exception {
		HttpRequest preflight = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/api/v1/users/me"))
				.header("Origin", "http://localhost:3000")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
				.send(preflight, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
				.contains("http://localhost:3000");
	}
}
