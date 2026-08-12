# Signaling

WebSocket endpoint: `ws://localhost:8080/ws/meetings/{meetingId}?token=<JWT>`
(`SignalingWebSocketHandler` / `WebSocketConfig`, package `signaling`).

## Authentication on connect

Browsers cannot attach an `Authorization` header to a native WebSocket
upgrade request, so the JWT travels as a `?token=` query parameter instead —
the one deliberate exception to "JWT always via header" elsewhere in this
API. `SignalingHandshakeInterceptor` runs *before* the handshake completes:

1. Extracts `{meetingId}` from the request's path variables (Spring's
   `SimpleUrlHandlerMapping` populates
   `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE` on the underlying
   `HttpServletRequest` for pattern-matched WebSocket handler registrations,
   same mechanism as an `@PathVariable` in an MVC controller).
2. Parses and verifies the `token` query param via the same `JwtService`
   used for REST auth.
3. Looks up the meeting and rejects (404) if it doesn't exist or has
   already ended.
4. On success, stashes the resolved `AuthenticatedUser` and `meetingId` in
   the WebSocket session's attributes for the handler to read later.

A rejected handshake never reaches `SecurityConfig`'s JWT filter chain at
all — `/ws/**` is explicitly `permitAll()` there, since this interceptor
*is* the auth check for that path.

## Envelope routing

Every parsed `SignalingEnvelope` (see
[api/websocket-protocol.md](../api/websocket-protocol.md)) is handled by
type:

- **`PING`** → updates the sender's heartbeat timestamp, replies `PONG`
  directly to that connection only.
- **`MEETING_JOIN` / `MEETING_LEAVE`** → accepted but inert. Presence is
  driven by the WebSocket connection lifecycle itself
  (`afterConnectionEstablished` / `afterConnectionClosed`), not by a
  client-sent message — a dropped connection is still a "leave" whether or
  not the client managed to send one first.
- **`CHAT_MESSAGE`**, **`PARTICIPANT_APPROVED`/`PARTICIPANT_REJECTED`**,
  **`HOST_MUTE_PARTICIPANT`**, **`HOST_REMOVE_PARTICIPANT`** → custom
  handling, see the dedicated sections below.
- **Everything else** (`WEBRTC_OFFER`/`WEBRTC_ANSWER`/`ICE_CANDIDATE`,
  `SCREEN_SHARE_STARTED`/`STOPPED`) → generic relay. If the envelope has a
  `targetId`, it's delivered only to that participant's session
  (point-to-point). Without a `targetId`, it's broadcast to every other
  session in the room.
- `senderId`/`meetingId` on every envelope are always stamped server-side
  from the authenticated session, never trusted from the client — a
  connection can't claim to speak as a different user.

On connect, a newly-joined participant is sent one `PARTICIPANT_JOINED`
envelope per participant already in the room (a snapshot, sent only to
them) before their own `PARTICIPANT_JOINED` is broadcast to everyone else —
that's how a client builds its initial roster without a separate
"room state" message type.

## Concurrency model

```
WebSocket connections
        │
        ▼
SignalingWebSocketHandler   (parses envelopes, never touches room state directly)
        │
        ▼
RoomCommandDispatcher
        │
    ┌───┼────┐
    ▼   ▼    ▼
Room A Room B Room C   ← fully concurrent across rooms
```

Within one room, commands (join, leave, PING, relay) run in the exact order
they were dispatched — otherwise two participants acting at the same
instant (e.g. one joining while another triggers the room's last-participant
eviction) could observe or produce an inconsistent `MeetingRuntime`.

This is **not** implemented as one dedicated thread per room (that doesn't
scale — thread count would grow with concurrent meeting count, most of them
idle). Instead, `RoomCommandDispatcher` keeps one chained
`CompletableFuture<Void>` per room (`Map<UUID, CompletableFuture<Void>>`):
appending a command is `tail = tail.thenRunAsync(command, executor)`, which
guarantees that room's commands run strictly in sequence without pinning a
thread to the room between commands. All rooms share a single
`Executors.newVirtualThreadPerTaskExecutor()` (Java 21) — virtual threads
are cheap enough that "one per in-flight command, across every room" is a
non-issue at any scale this project will realistically reach, unlike
platform threads. A failing command is logged and does not break that
room's chain; the next command still runs.

`MeetingRuntime` (per-room participant map) and `MeetingRuntimeRegistry`
(the `meetingId → MeetingRuntime` map) are the in-memory, non-persisted
state this dispatcher protects — see
[database/schema.md](../database/schema.md#what-is-not-persisted) for why
none of this lives in PostgreSQL.

## Heartbeat

Client → server `PING` every `WS_HEARTBEAT_INTERVAL_SECONDS` (default 10s,
frontend `SignalingClient`). Server replies `PONG` and records the
timestamp on that participant's `RuntimeParticipant`. A separate
`HeartbeatReaper` (`@Scheduled`, checks every
`WS_HEARTBEAT_TIMEOUT_SECONDS`-independent fixed interval, default 5s) closes
any session whose last heartbeat is older than
`WS_HEARTBEAT_TIMEOUT_SECONDS` (default 30s — three missed pings). Closing
the session triggers the handler's normal `afterConnectionClosed` cleanup
path, so the reaper doesn't duplicate any room-state or broadcast logic —
it only decides *when* a session counts as dead.

## Reconnect grace period (Milestone 8)

`afterConnectionClosed` doesn't remove a participant immediately for every
close — only for a clean, client-initiated one (WebSocket close code 1000,
which covers the "leave" button, host-remove, and waiting-room rejection,
since all three explicitly close with that code). Anything else — a network
drop, a suspended tab, `HeartbeatReaper` closing a stale session — is
treated as a *possibly temporary* disconnect:

1. The `RuntimeParticipant` stays in the room's roster (so nobody else sees
   them vanish) but is marked `pendingRemoval` via
   `RuntimeParticipant.markDisconnected()`.
2. `RoomCommandDispatcher.scheduleDispatch` queues a removal command for
   `WS_RECONNECT_GRACE_SECONDS` later (default 15s), still running through
   the same per-room serialized queue as every other command — just
   delayed.
3. If the same user (matched by `userId`, not session id) opens a new
   WebSocket connection to the same meeting before that timer fires,
   `MeetingRuntime.reconnect()` re-points the existing `RuntimeParticipant`
   at the new session (new map key, same object) and cancels the pending
   removal implicitly — `expireDisconnect` looks the participant up by the
   *old* session id, finds nothing there anymore, and no-ops.
4. A successful reconnect sends the reconnecting client a fresh roster
   snapshot (their own client-side state may be gone entirely, e.g. after a
   full page reload) and broadcasts `PARTICIPANT_RECONNECTED` — not
   `PARTICIPANT_LEFT` + `PARTICIPANT_JOINED` — to everyone else, since they
   never saw a LEFT for this user in the first place. If the grace period
   expires with no reconnect, `PARTICIPANT_LEFT` fires exactly as before.

The waiting-room lane does **not** get this grace period — a disconnect
while waiting is removed immediately; reconnecting just re-enters the
waiting lane and asks again. Not worth the added complexity for a
pre-admission state.

This only resumes the *signaling* session/roster slot. The WebRTC media
path is a separate concern — see
[webrtc.md](./webrtc.md#ice-restart-milestone-8) for how peer connections
recover independently once signaling is back.

## Waiting room (Milestone 7)

`MeetingRuntime` holds two separate maps: active `participants` and
`waitingParticipants` (sessions in the waiting lane — not in anyone's
roster, not receiving broadcasts, not counted for "is the room empty").
When a non-host joins a meeting whose `accessType` is `APPROVAL_REQUIRED`:

1. Their session goes into the waiting map instead of the active one.
   `PARTICIPANT_WAITING` is sent to them (an ack their request is in) and
   to every currently-connected host.
2. If no host is connected yet when they arrive, nothing is lost — a host
   who joins later receives a `PARTICIPANT_WAITING` for everyone still
   waiting, as part of their own join handling.
3. A host decides by sending `PARTICIPANT_APPROVED` or
   `PARTICIPANT_REJECTED` with `targetId` set to the waiting user's id.
   The server verifies the sender is actually a `HOST` in that room's
   runtime before acting — a non-host sending this is silently ignored, the
   same guard as `HOST_MUTE_PARTICIPANT`/`HOST_REMOVE_PARTICIPANT` below.
4. On approval: the waiting session gets the normal snapshot + becomes a
   full `RuntimeParticipant`, exactly as if they'd connected directly, then
   `PARTICIPANT_JOINED` broadcasts to the room. On rejection: the session is
   closed with a `PARTICIPANT_REJECTED` sent first so the client can show
   why.

The REST `POST /meetings/{id}/join` call already persisted their
`meeting_participants` row *before* any of this — the waiting room is a
purely runtime/signaling concept (see
[database/schema.md](../database/schema.md#what-is-not-persisted)), it
doesn't gate whether they're recorded as having joined, only whether
they're actually present in the live room yet.

## Chat (Milestone 7)

`CHAT_MESSAGE` is the one event type that touches PostgreSQL:
`SendChatMessageUseCase` persists it (validating non-empty, ≤4000 chars)
*before* the server constructs the outbound envelope from the saved
row's id/timestamp, then sends that to **every** participant including the
original sender — nobody renders an unconfirmed local copy first. This
keeps a single source of truth for message ordering and ids instead of
reconciling an optimistic client render against what the server eventually
persists.

## Host controls (Milestone 7)

`HOST_MUTE_PARTICIPANT` and `HOST_REMOVE_PARTICIPANT` both require the
sender to hold `HOST` role in that room's runtime — checked the same way as
waiting-room approval decisions — before anything happens:

- **Mute**: a plain relay to the target once authorized. The target's own
  client is what actually mutes its microphone on receipt; the server never
  touches media state, it only delivers the instruction.
- **Remove**: not a relay — the server closes the target's WebSocket
  session directly. That triggers the normal `afterConnectionClosed` →
  `handleLeave` path, which broadcasts `PARTICIPANT_LEFT` to everyone else
  exactly as a voluntary leave would. The target is sent a
  `HOST_REMOVE_PARTICIPANT` envelope just before the close so their client
  can show *why* the connection ended, rather than just going silent.
