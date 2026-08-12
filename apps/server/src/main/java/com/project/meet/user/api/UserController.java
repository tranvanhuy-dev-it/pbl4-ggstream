package com.project.meet.user.api;

import com.project.meet.common.exception.ResourceNotFoundException;
import com.project.meet.common.security.AuthenticatedUser;
import com.project.meet.user.domain.User;
import com.project.meet.user.infrastructure.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/me")
	public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
		User user = userRepository.findById(principal.userId())
				.orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User no longer exists"));
		return UserProfileResponse.from(user);
	}
}
