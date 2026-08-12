package com.project.meet.auth.api;

import com.project.meet.auth.application.LoginUseCase;
import com.project.meet.auth.application.RegisterUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final RegisterUseCase registerUseCase;
	private final LoginUseCase loginUseCase;

	public AuthController(RegisterUseCase registerUseCase, LoginUseCase loginUseCase) {
		this.registerUseCase = registerUseCase;
		this.loginUseCase = loginUseCase;
	}

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(registerUseCase.execute(request));
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ResponseEntity.ok(loginUseCase.execute(request));
	}
}
