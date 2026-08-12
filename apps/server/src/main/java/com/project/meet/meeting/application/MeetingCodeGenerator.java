package com.project.meet.meeting.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates Google Meet-style join codes: three lowercase groups separated
 * by hyphens (e.g. "abc-defg-hij"). Collision handling (retry against
 * {@code existsByCode}) is the caller's responsibility.
 */
@Component
public class MeetingCodeGenerator {

	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz";
	private static final int[] GROUP_LENGTHS = {3, 4, 3};

	private final SecureRandom random = new SecureRandom();

	public String generate() {
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < GROUP_LENGTHS.length; i++) {
			if (i > 0) {
				code.append('-');
			}
			for (int j = 0; j < GROUP_LENGTHS[i]; j++) {
				code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
			}
		}
		return code.toString();
	}
}
