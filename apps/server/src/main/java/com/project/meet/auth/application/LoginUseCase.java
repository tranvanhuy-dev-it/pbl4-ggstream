package com.project.meet.auth.application;

import com.project.meet.auth.api.AuthResponse;
import com.project.meet.auth.api.LoginRequest;
import com.project.meet.auth.api.UserSummary;
import com.project.meet.auth.domain.InvalidCredentialsException;
import com.project.meet.common.security.JwtService;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginUseCase {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public LoginUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional(readOnly = true)
	public AuthResponse execute(LoginRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase();

		User user = userRepository.findByEmail(normalizedEmail)
				.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		String token = jwtService.generateToken(user.getId(), user.getEmail());
		return AuthResponse.of(token, jwtService.expirationFor(token), UserSummary.from(user));
	}
}
