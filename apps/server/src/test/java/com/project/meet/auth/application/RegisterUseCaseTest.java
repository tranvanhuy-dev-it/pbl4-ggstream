package com.project.meet.auth.application;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.RegisterRequest;
import com.project.meet.auth.domain.EmailAlreadyInUseException;
import com.project.meet.common.security.JwtService;
import com.project.meet.user.infrastructure.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUseCaseTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtService jwtService;

	@Test
	void rejectsRegistrationWhenEmailAlreadyInUse() {
		RegisterUseCase useCase = new RegisterUseCase(userRepository, passwordEncoder, jwtService);
		when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

		RegisterRequest request = new RegisterRequest("taken@example.com", "password123", "Someone");

		assertThatThrownBy(() -> useCase.execute(request))
				.isInstanceOf(EmailAlreadyInUseException.class);
	}

	@Test
	void registersNewUserAndReturnsToken() {
		RegisterUseCase useCase = new RegisterUseCase(userRepository, passwordEncoder, jwtService);
		when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("hashed");
		when(jwtService.generateToken(any(), any())).thenReturn("token123");
		when(jwtService.expirationFor("token123")).thenReturn(Instant.now());

		RegisterRequest request = new RegisterRequest("new@example.com", "password123", "New User");
		AuthResponse response = useCase.execute(request);

		assertThat(response.accessToken()).isEqualTo("token123");
		assertThat(response.user().email()).isEqualTo("new@example.com");
		assertThat(response.user().displayName()).isEqualTo("New User");
	}
}
