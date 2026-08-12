# WebSocket Signaling Protocol

**Status: planned, Milestone 3.** Documented ahead of time so the envelope
shape is settled before implementation starts.

Endpoint: `/ws/meetings/{meetingId}` (subject to change if a single gateway
endpoint turns out cleaner once auth-on-connect is implemented).

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

`targetId` is null for broadcast events (e.g. `PARTICIPANT_JOINED`) and set
for point-to-point signaling relay (`WEBRTC_OFFER`, `WEBRTC_ANSWER`,
`ICE_CANDIDATE`).

## Event types

```
MEETING_JOIN / MEETING_LEAVE
PARTICIPANT_JOINED / PARTICIPANT_LEFT
WEBRTC_OFFER / WEBRTC_ANSWER / ICE_CANDIDATE
MIC_STATE_CHANGED / CAMERA_STATE_CHANGED
SCREEN_SHARE_STARTED / SCREEN_SHARE_STOPPED
CHAT_MESSAGE
PARTICIPANT_WAITING / PARTICIPANT_APPROVED / PARTICIPANT_REJECTED
HOST_MUTE_PARTICIPANT / HOST_REMOVE_PARTICIPANT
PING / PONG
ERROR
```

Media (audio/video/screen RTP) never travels over this WebSocket — only
signaling and application events. See
[networking/signaling.md](../networking/signaling.md) for the routing and
concurrency model once implemented.
