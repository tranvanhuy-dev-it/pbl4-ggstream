const STUN_URL = process.env.NEXT_PUBLIC_STUN_URL ?? "stun:stun.l.google.com:19302";

/**
 * STUN only for now — TURN (and short-lived backend-issued credentials)
 * lands in Milestone 5. STUN alone is enough for two peers that aren't
 * both behind symmetric NAT, which covers most development/testing setups.
 */
export function getIceServers(): RTCIceServer[] {
  return [{ urls: [STUN_URL] }];
}
