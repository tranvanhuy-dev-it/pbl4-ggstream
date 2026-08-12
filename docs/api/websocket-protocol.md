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
`ICE_CANDIDATE`) — the server routes any envelope with a `targetId` only to
that participant's session, and broadcasts everything else to the rest of
the room.

## Event types

```
MEETING_JOIN / MEETING_LEAVE        — accepted, but a no-op: presence is
                                       driven by the socket connecting/
                                       closing, not by these messages
PARTICIPANT_JOINED / PARTICIPANT_LEFT
WEBRTC_OFFER / WEBRTC_ANSWER / ICE_CANDIDATE   — relayed, not yet sent by
                                                   any client (Milestone 4)
MIC_STATE_CHANGED / CAMERA_STATE_CHANGED       — relayed, not yet sent (Milestone 4+)
SCREEN_SHARE_STARTED / SCREEN_SHARE_STOPPED    — relayed, not yet sent (Milestone 7)
CHAT_MESSAGE                                    — relayed, not yet sent (Milestone 7)
PARTICIPANT_WAITING / PARTICIPANT_APPROVED / PARTICIPANT_REJECTED  — Milestone 7
HOST_MUTE_PARTICIPANT / HOST_REMOVE_PARTICIPANT                    — Milestone 7
PING / PONG                          — implemented; heartbeat
ERROR                                — implemented; malformed-message reports
```

Everything not yet sent by a client still routes correctly today — the
server's relay logic (broadcast vs. point-to-point-by-`targetId`) doesn't
special-case message types beyond `PING`/`MEETING_JOIN`/`MEETING_LEAVE`, so
Milestone 4+ features land by having a client start sending these types,
not by changing server routing.

Media (audio/video/screen RTP) never travels over this WebSocket — only
signaling and application events. See
[networking/signaling.md](../networking/signaling.md) for the routing and
concurrency model.
