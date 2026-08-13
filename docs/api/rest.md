# REST API

Base path: `/api/v1` (health check is the exception — it's under Spring Boot
Actuator's own `/actuator` path, not versioned application API).

## Implemented

### `GET /actuator/health`

Public. Returns `{"status": "UP"}` when the application and its database
connection are healthy. Used by the frontend's `BackendStatus` component and
for manual verification (`curl http://localhost:8080/actuator/health`).

### `POST /api/v1/auth/register`

Public. Body: `{ "email", "password", "displayName" }`. Creates a `users`
row (BCrypt-hashed password) and returns `201` with an `AuthResponse`:

```json
{
  "accessToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresAt": "2026-08-13T11:31:06Z",
  "user": { "id": "uuid", "email": "...", "displayName": "..." }
}
```

`409 EMAIL_ALREADY_IN_USE` if the (case-insensitive) email is already
registered.

### `POST /api/v1/auth/login`

Public. Body: `{ "email", "password" }`. Returns `200` with the same
`AuthResponse` shape as register. `401 INVALID_CREDENTIALS` for a wrong
email or password — deliberately the same error either way, so the response
doesn't reveal whether an email is registered.

### `GET /api/v1/users/me`

Requires `Authorization: Bearer <accessToken>`. Returns the authenticated
user's profile. `401 UNAUTHENTICATED` if the header is missing or the token
is invalid/expired — exists primarily to prove the JWT filter chain works
end to end; the frontend doesn't call it beyond session rehydration on load.

### `POST /api/v1/meetings`

Body: `{ "title", "accessType"?, "mediaMode"? }` (`accessType` defaults to `PUBLIC`; `APPROVAL_REQUIRED` is enforced at the signaling layer — see [networking/signaling.md](../networking/signaling.md#waiting-room-milestone-7) — `INVITED` is stored but not yet enforced. `mediaMode` defaults to `MESH`; `SFU` requires calling `POST /meetings/{meetingId}/sfu-token` to actually join the call, see below). Returns `201` with a `MeetingResponse`, including a generated join code (`abc-defg-hij` style, collision-checked against the DB). The caller becomes `hostId` but is **not** automatically a participant — they still call `/join` like anyone else, which is what actually creates their `meeting_participants` row and (for the host specifically) flips the meeting `CREATED → ACTIVE`.

### `GET /api/v1/meetings/{meetingId}` / `GET /api/v1/meetings/code/{code}`

Fetch meeting metadata (title, host, status, access type, timestamps). The lobby uses the by-code variant to resolve a join link before the user has the meeting's UUID. `404 MEETING_NOT_FOUND` if it doesn't exist.

### `POST /api/v1/meetings/{meetingId}/join`

Creates (or, if already an active participant, idempotently returns) a `meeting_participants` row for the caller — `HOST` role if they're the meeting's host, `PARTICIPANT` otherwise. First join transitions the meeting `CREATED → ACTIVE`. `409 MEETING_ENDED` if the meeting has already ended.

### `POST /api/v1/meetings/{meetingId}/leave`

Marks the caller's active participant row `leftAt = now`. `404 NOT_IN_MEETING` if they don't currently have an active row (e.g. double leave-click, or never joined).

### `POST /api/v1/meetings/{meetingId}/end`

Host-only — `403 NOT_MEETING_HOST` otherwise. Marks the meeting `ENDED` and closes every still-active participant row. Idempotent: ending an already-ended meeting just returns its current state.

### `GET /api/v1/meetings/{meetingId}/participants`

Lists currently-active participants (`leftAt IS NULL`) — the room page
calls this once to seed its roster on load, then applies
`PARTICIPANT_JOINED`/`PARTICIPANT_LEFT` WebSocket events as deltas from
that point forward (see [networking/signaling.md](../networking/signaling.md)).

### `GET /api/v1/rtc/ice-servers`

Requires `Authorization: Bearer <accessToken>`. Returns STUN and (if
`TURN_HOST`/`TURN_SECRET` are configured) TURN ICE server entries, the
latter with a freshly-generated short-lived credential
(`rtc.application.TurnCredentialService` — HMAC-SHA1 of the shared secret,
Coturn's `use-auth-secret` REST API auth convention). Degrades to STUN-only,
not an error, when TURN isn't configured. See
[networking/ice-stun-turn.md](../networking/ice-stun-turn.md#turn-credentials-milestone-5).

```json
{
  "iceServers": [
    { "urls": ["stun:stun.l.google.com:19302"], "username": null, "credential": null },
    { "urls": ["turn:host:3478?transport=udp", "turn:host:3478?transport=tcp"], "username": "1786...:uuid", "credential": "base64..." }
  ]
}
```

### `GET /api/v1/meetings/{meetingId}/messages`

Returns full chat history for a meeting, oldest first — the room page
fetches this once on load, then applies live `CHAT_MESSAGE` WebSocket
events as deltas (see [api/websocket-protocol.md](./websocket-protocol.md)).
`404 MEETING_NOT_FOUND` if the meeting doesn't exist. Sending a message
happens over the WebSocket, not REST — see `ChatMessageResponse` for the
shape each entry has.

### `POST /api/v1/meetings/{meetingId}/sfu-token`

Requires `Authorization: Bearer <accessToken>`. Only valid for meetings
created with `mediaMode: "SFU"` — `409 NOT_AN_SFU_MEETING` otherwise, so a
Mesh-mode meeting can't accidentally end up connecting to the SFU. Returns
a short-lived, per-user [LiveKit](https://livekit.io) access token the
frontend uses to connect directly to the self-hosted LiveKit server (see
[networking/sfu-setup.md](../networking/sfu-setup.md) and
[networking/webrtc.md](../networking/webrtc.md#triển-khai-sfu-milestone-11)):

```json
{
  "url": "ws://localhost:7880",
  "token": "eyJhbGciOi..."
}
```

`503 SFU_NOT_CONFIGURED` if `LIVEKIT_URL`/`LIVEKIT_API_KEY`/`LIVEKIT_API_SECRET`
aren't set. Unlike TURN credentials (which degrade gracefully to STUN-only),
there's no fallback for a missing SFU — an `SFU`-mode meeting has no other
transport to fall back to, so this fails loudly instead of silently.

### `POST /api/v1/live/start`

Requires `Authorization: Bearer <accessToken>`. **No meeting involved at
all** — livestream is its own top-level feature, see
[networking/livestream.md](../networking/livestream.md). Body:
`{ "title"? }`, defaults to `"Livestream"` if omitted/blank. Spawns an
FFmpeg process and returns:

```json
{
  "id": "b3f1...",
  "code": "abc-defg-hij",
  "title": "Buổi phát của tôi",
  "status": "LIVE",
  "hlsUrl": "/livestreams/b3f1.../stream.m3u8"
}
```

`id` is the caller's own credential for `stop`/the ingest WebSocket — see
below, it's never exposed to anyone else. Actually pushing media happens
over the ingest WebSocket (`/ws/live/{id}/ingest`, binary frames, JWT via
`?token=` like signaling), not REST.

### `POST /api/v1/live/{id}/stop`

Requires `Authorization: Bearer <accessToken>` **and** being the same user
who called `/start` (`403 NOT_LIVESTREAM_OWNER` otherwise, `404
LIVESTREAM_NOT_LIVE` if `id` isn't a currently-live session). Returns `204`.

### `GET /api/v1/live/{code}/status`

**Public — no auth.** Backs the `/watch/{code}` viewer page. Returns the
same shape as `/start`'s response but with `id: null` — the public,
by-code lookup never exposes the control id needed to stop someone else's
stream. An unknown or already-ended `code` returns `200` with
`status: "ENDED"`, not a `404` — see
[networking/livestream.md](../networking/livestream.md#vì-sao-trạng-thái-livestream-không-lưu-db)
for why the two cases are indistinguishable by design.

## Authentication

Every route except `/actuator/health`, `/api/v1/auth/**`, and the public
livestream-viewing routes above requires a Bearer JWT
(`Authorization: Bearer <token>`), verified by `JwtAuthenticationFilter` —
see
[architecture/backend.md](../architecture/backend.md#authentication-milestone-1).
Missing/invalid tokens on a protected route get `401 UNAUTHENTICATED`;
authenticated-but-forbidden gets `403 ACCESS_DENIED` — both in the same
`ApiError` shape as everything else, even though they're generated by the
Spring Security filter chain rather than `GlobalExceptionHandler`.

## Error shape

Every non-2xx response from `/api/v1/**` uses the shape documented in
[architecture/backend.md](../architecture/backend.md#error-handling):

```json
{
  "code": "STRING_CODE",
  "message": "human readable",
  "timestamp": "ISO-8601",
  "requestId": "uuid",
  "violations": [{ "field": "email", "message": "must not be blank" }]
}
```

`violations` is only present for validation failures (400). `message` is
Vietnamese (the UI's display language) — `code` is the stable,
language-independent identifier for anything that needs to branch on the
error type programmatically.

DTOs are defined per-feature under `<feature>/api/`; JPA entities are never
returned directly from a controller.
