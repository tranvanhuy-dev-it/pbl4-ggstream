package com.project.meet.livestream.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates short public codes for the /watch/{code} link — same shape as
 * {@code meeting.application.MeetingCodeGenerator} (three lowercase groups,
 * e.g. "abc-defg-hij"), duplicated rather than shared since livestream is
 * deliberately independent of the meeting package (see LivestreamSession).
 * Collision handling (retry against {@code LivestreamRegistry#codeInUse})
 * is the caller's responsibility.
 */
@Component
public class LivestreamCodeGenerator {

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
