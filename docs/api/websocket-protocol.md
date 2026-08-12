# WebSocket Signaling Protocol

Endpoint: `ws://localhost:8080/ws/meetings/{meetingId}?token=<JWT>` — the
token travels as a query param, not an `Authorization` header, because
browsers can't set custom headers on a WebSocket upgrade request. See
[networking/signaling.md](../networking/signaling.md#authentication-on-connect)
for the handshake-time auth flow.

## Envelope

Every message, both directions, uses the same shape:

```json
{
  "type": "WEBRTC_OFFER",
  "requestId": "uuid",
  "meetingId": "uuid",
  "senderId": "uuid",
  "targetId": "uuid",
  "timestamp": 0,
  "payload": {}
}
```

`meetingId`/`senderId` are always overwritten server-side from the
authenticated session on every message the server sends or relays — a
client can't claim to speak as someone else, and doesn't need to fill them
in on outgoing messages (the backend ignores whatever it receives there).
`timestamp` is likewise server-assigned and safe to omit entirely when
sending.

`targetId` is null for broadcast events (e.g. `PARTICIPANT_JOINED`) and set
for point-to-point signaling relay (`WEBRTC_OFFER`, `WEBRTC_ANSWER`,
`ICE_CANDIDATE`, `HOST_MUTE_PARTICIPANT`, `HOST_REMOVE_PARTICIPANT`,
`PARTICIPANT_APPROVED`, `PARTICIPANT_REJECTED`) — the server routes any
envelope with a `targetId` only to that participant's session, and
broadcasts everything else to the rest of the room.

## Event types

```
MEETING_JOIN / MEETING_LEAVE        — accepted, but a no-op: presence is
                                       driven by the socket connecting/
                                       closing, not by these messages
PARTICIPANT_JOINED / PARTICIPANT_LEFT
PARTICIPANT_RECONNECTED             — server → everyone except the
                                        reconnecting user, when a dropped
                                        participant reconnects within the
                                        grace period (see
                                        networking/signaling.md#reconnect-grace-period-milestone-8).
                                        No JOINED/LEFT pair — the roster
                                        entry never actually left — but a
                                        cue to attempt an ICE restart for
                                        that peer's WebRTC connection
WEBRTC_OFFER / WEBRTC_ANSWER / ICE_CANDIDATE   — relayed by targetId (Milestone 4+)
SCREEN_SHARE_STARTED / SCREEN_SHARE_STOPPED    — broadcast; UI-only signal,
                                                   the actual screen track is
                                                   a WebRTC renegotiation,
                                                   not sent over this socket
CHAT_MESSAGE                         — persisted (see chat_messages) then
                                        broadcast to everyone including the
                                        sender — the server's copy (with its
                                        assigned id/timestamp) is the single
                                        source of truth, not a client-side
                                        optimistic render
PARTICIPANT_WAITING                  — server → the waiting client (an ack)
                                        and server → every host in the room
                                        (a request to act on); see
                                        signaling.md#waiting-room-milestone-7
PARTICIPANT_APPROVED / PARTICIPANT_REJECTED  — host → server (with
                                        targetId = the waiting user) to
                                        decide; server → that user to notify
                                        the outcome. Same type both
                                        directions, reused rather than
                                        introducing a separate "decision"
                                        message type
HOST_MUTE_PARTICIPANT                — host → server → targeted participant;
                                        server checks the sender actually
                                        has HOST role before relaying
HOST_REMOVE_PARTICIPANT              — host → server, which closes the
                                        target's WebSocket session directly
                                        (not a plain relay) — the target's
                                        own afterConnectionClosed cleanup
                                        then broadcasts PARTICIPANT_LEFT
                                        normally
PING / PONG                          — heartbeat
ERROR                                — malformed-message / invalid-chat-message reports
```

Everything not yet sent by a client still routes correctly — the server's
default relay logic (broadcast vs. point-to-point-by-`targetId`) doesn't
special-case message types beyond the ones listed above with custom
handling, so future features land by having a client start sending a new
type, not by changing server routing.

Media (audio/video/screen RTP) never travels over this WebSocket — only
signaling and application events. See
[networking/signaling.md](../networking/signaling.md) for the routing and
concurrency model.
