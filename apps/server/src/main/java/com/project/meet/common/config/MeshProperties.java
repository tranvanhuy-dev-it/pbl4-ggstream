package com.project.meet.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mesh (Milestone 4/6) doesn't scale past a small room size — every
 * participant opens one RTCPeerConnection per other participant, so upload
 * cost grows as n-1 per person. See docs/architecture/scalability-plan.md
 * (section 1) for the math. {@code maxParticipants} is enforced in
 * SignalingWebSocketHandler#handleJoin — SFU-mode meetings are unaffected.
 */
@ConfigurationProperties(prefix = "app.mesh")
public record MeshProperties(int maxParticipants) {
}
