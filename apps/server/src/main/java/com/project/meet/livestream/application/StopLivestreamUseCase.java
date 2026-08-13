package com.project.meet.livestream.application;

import com.project.meet.livestream.domain.LivestreamNotLiveException;
import com.project.meet.livestream.domain.NotLivestreamOwnerException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StopLivestreamUseCase {

	private final LivestreamRegistry registry;

	public StopLivestreamUseCase(LivestreamRegistry registry) {
		this.registry = registry;
	}

	public void execute(UUID id, UUID requesterUserId) {
		LivestreamSession session = registry.get(id);
		if (session == null) {
			throw new LivestreamNotLiveException();
		}
		if (!session.getHostUserId().equals(requesterUserId)) {
			throw new NotLivestreamOwnerException();
		}

		registry.remove(id);
		session.stop();
	}
}
