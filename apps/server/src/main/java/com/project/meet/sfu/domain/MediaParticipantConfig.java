package com.project.meet.sfu.domain;

/** The SFU URL the client connects to directly, plus a token scoped to one meeting + one user. */
public record MediaParticipantConfig(String url, String token) {
}
