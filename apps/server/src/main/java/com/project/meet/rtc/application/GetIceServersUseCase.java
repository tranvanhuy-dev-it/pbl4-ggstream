package com.project.meet.rtc.application;

import com.project.meet.common.config.TurnProperties;
import com.project.meet.rtc.api.IceServer;
import com.project.meet.rtc.api.IceServersResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class GetIceServersUseCase {

	private static final Logger log = LoggerFactory.getLogger(GetIceServersUseCase.class);

	private final TurnProperties properties;
	private final TurnCredentialService credentialService;

	public GetIceServersUseCase(TurnProperties properties, TurnCredentialService credentialService) {
		this.properties = properties;
		this.credentialService = credentialService;
	}

	public IceServersResponse execute(UUID requesterId) {
		List<IceServer> servers = new ArrayList<>();

		if (properties.stunUrl() != null && !properties.stunUrl().isBlank()) {
			servers.add(IceServer.stunOnly(properties.stunUrl()));
		}

		if (properties.isTurnConfigured()) {
			TurnCredentialService.TurnCredential credential = credentialService.generate(requesterId);
			servers.add(IceServer.withCredentials(
					List.of(
							"turn:%s:%d?transport=udp".formatted(properties.host(), properties.port()),
							"turn:%s:%d?transport=tcp".formatted(properties.host(), properties.port())
					),
					credential.username(),
					credential.credential()
			));
		} else {
			log.debug("TURN is not configured (app.turn.host/secret unset) — returning STUN-only ICE servers");
		}

		return new IceServersResponse(servers);
	}
}
