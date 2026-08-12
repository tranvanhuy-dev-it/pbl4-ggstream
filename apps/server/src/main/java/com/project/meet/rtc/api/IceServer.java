package com.project.meet.rtc.api;

import java.util.List;

public record IceServer(
		List<String> urls,
		String username,
		String credential
) {

	public static IceServer stunOnly(String url) {
		return new IceServer(List.of(url), null, null);
	}

	public static IceServer withCredentials(List<String> urls, String username, String credential) {
		return new IceServer(urls, username, credential);
	}
}
