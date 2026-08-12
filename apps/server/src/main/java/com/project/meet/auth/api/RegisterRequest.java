package com.project.meet.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, max = 72, message = "phải có độ dài từ 8 đến 72 ký tự") String password,
		@NotBlank @Size(max = 100) String displayName
) {
}
