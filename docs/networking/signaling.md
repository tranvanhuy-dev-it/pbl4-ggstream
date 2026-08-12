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
- **Everything else** → relayed. If the envelope has a `targetId`, it's
  delivered only to that participant's session (point-to-point — this is
  how `WEBRTC_OFFER`/`WEBRTC_ANSWER`/`ICE_CANDIDATE` will work once
  Milestone 4 starts sending them). Without a `targetId`, it's broadcast to
  every other session in the room.
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

Reconnection (a client that reconnects after a timeout resuming its
session rather than being treated as a brand-new participant) is explicitly
out of scope here — that's Milestone 8.
