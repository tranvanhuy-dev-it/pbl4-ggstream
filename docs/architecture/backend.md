# Backend Architecture

Spring Boot 4 (Spring Framework 7), Java 21, Maven, PostgreSQL via Spring
Data JPA/Hibernate.

## Package-by-feature

Each feature owns its own vertical slice:

```
com.project.meet
├── common/            # cross-cutting: config, exception, security, web
│   ├── config/        # typed @ConfigurationProperties, CORS, web config
│   ├── exception/      # ApiException hierarchy, GlobalExceptionHandler
│   ├── security/       # JWT filter chain, JwtService, PasswordEncoder
│   └── web/             # RequestIdFilter (request correlation)
├── auth/              # register/login use cases, JWT issuance
├── user/              # User entity, UserRepository, GET /users/me
├── meeting/           # Meeting entity, create/get/end use cases, code generator
├── participant/       # MeetingParticipant entity, join/leave/list use cases
├── signaling/         # WebSocket handler, envelope routing, room concurrency, heartbeat
├── rtc/               # GET /rtc/ice-servers — TURN credential generation
├── chat/              # persisted messages, GET history, WS CHAT_MESSAGE handling
└── MeetApplication.java
```

Features that aren't implemented yet do **not** have empty placeholder
packages — they're created when their milestone starts, per the milestone
order in the root README. This avoids dead scaffolding sitting unused in the
tree.

Within a feature, once it grows beyond a trivial CRUD slice, split further:

```
meeting/
├── api/            # REST controllers, request/response DTOs
├── application/    # use cases (CreateMeetingUseCase, JoinMeetingUseCase, ...)
├── domain/         # entities, value objects, domain rules
└── infrastructure/ # JPA repositories, external integrations
```

No `MeetingService.java` god-class: each use case is its own class with a
single `execute(...)` entry point, so tests and responsibilities stay
scoped.

## Configuration

`.env` → environment variables (loaded via `spring-dotenv`) → `application.yml`
placeholders (`${VAR:default}`) → either Spring Boot's own typed properties
(`spring.datasource.*`, `spring.jpa.*`) or a project-specific
`@ConfigurationProperties` class (e.g. `CorsProperties`).

We deliberately do **not** wrap `spring.datasource.*` in a custom
`DatabaseProperties` class — Spring Boot's `DataSourceProperties` already is
that typed configuration, and duplicating it would just be indirection with
no benefit. Custom `@ConfigurationProperties` records are added only for
settings Spring Boot doesn't already model: `CorsProperties`, `JwtProperties`,
`WebSocketProperties` (heartbeat interval/timeout), `TurnProperties`
(TURN host/port/shared-secret/credential-TTL, plus the STUN URL — grouped
together since `GET /rtc/ice-servers` always returns both).

No `System.getenv(...)` calls anywhere in application code.

`spring-dotenv` doesn't carry Spring Boot auto-registration metadata in the
version this project uses, so it's wired explicitly in `MeetApplication.main`
via `SpringApplicationBuilder(...).initializers(new
DotenvApplicationInitializer())` rather than relying on classpath
auto-detection. Tests don't go through `main()`, so they never read `.env` —
`src/test/resources/application.yml` sets every property a test needs
directly (including `app.jwt.secret`, since `JwtService`'s constructor fails
fast if it's blank).

## Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) translates exceptions into
a stable `ApiError` JSON shape:

```json
{
  "code": "MEETING_NOT_FOUND",
  "message": "Không tìm thấy cuộc họp",
  "timestamp": "2026-08-12T10:00:00Z",
  "requestId": "b3f1...",
  "violations": null
}
```

`message` is Vietnamese (the frontend's display language) since it's what
the UI renders as-is (`ErrorAlert`) without any client-side translation
layer; `code` is the stable, language-independent identifier for anything
that needs to branch on the error type. Bean validation's default messages
(`@NotBlank`, `@Size`, ...) are localized the same way, via
`src/main/resources/ValidationMessages.properties` — Hibernate Validator
picks that up automatically as the default resource bundle, so individual
`@NotBlank` annotations don't need an explicit `message = "..."` unless
they want to override the bundle default (as `RegisterRequest.password`
does, for its specific length message).

- `ApiException` (with `code` + `HttpStatus`) is the base type for
  domain/application errors; feature modules subclass it
  (`ResourceNotFoundException` is provided as the common 404 case).
- `MethodArgumentNotValidException` / `ConstraintViolationException` produce
  `VALIDATION_FAILED` with a `violations` list (field + message).
- Anything unexpected is logged with the request id and returned as a bare
  `INTERNAL_ERROR` — no stack trace ever reaches the client.

`RequestIdFilter` stamps every request with a correlation id (from
`X-Request-Id` if the client sent one, otherwise a generated UUID), stores it
in MDC for log correlation, and echoes it back as a response header. The
console log pattern includes `[%X{requestId}]` so every log line for a
request can be grepped together.

## Health check

`spring-boot-starter-actuator` exposes `GET /actuator/health`, public
(no auth) per `SecurityConfig`. This is the endpoint the frontend's
`BackendStatus` component polls, and what `curl` should hit to confirm the
backend is up.

## Authentication (Milestone 1)

Stateless JWT — no server-side session, no `HttpSession`. Flow:

1. `POST /api/v1/auth/register` or `/login` (both public) return an
   `AuthResponse` with a signed JWT (`JwtService`, HS512, key from
   `JWT_SECRET`, HMAC key material is just the secret's raw UTF-8 bytes —
   fine for HS512 as long as the secret is reasonably long, no separate
   key-derivation step). Subject claim is the user's UUID; `email` is a
   custom claim. Expiry is `JWT_EXPIRATION_MINUTES` (default 24h).
2. Every subsequent request carries `Authorization: Bearer <token>`.
   `JwtAuthenticationFilter` (a plain `OncePerRequestFilter`, registered
   before `UsernamePasswordAuthenticationFilter`) parses and verifies it,
   then populates `SecurityContextHolder` with an `AuthenticatedUser(userId,
   email)` principal — no `UserDetailsService`/`AuthenticationManager`
   round-trip, since there's no username/password check happening at
   request time, just signature + expiry verification.
3. `SecurityConfig` permits `/actuator/health` and `/api/v1/auth/**`;
   everything else requires an authenticated principal.
4. Rejections from the security filter chain (missing/invalid token → 401,
   authenticated-but-forbidden → 403) never reach `GlobalExceptionHandler`
   — that only covers exceptions inside the DispatcherServlet. They're
   handled by `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`,
   writing the same `ApiError` JSON shape directly, so the API's error
   contract stays uniform regardless of which layer rejected the request.

Passwords are hashed with `BCryptPasswordEncoder` (`SecurityConfig` exposes
it as a `PasswordEncoder` bean); `RegisterUseCase`/`LoginUseCase` are the
only callers. Login failures (unknown email vs. wrong password) both raise
the same `InvalidCredentialsException` — deliberately indistinguishable, so
the API doesn't leak which emails are registered.

Controllers read the current user via
`@AuthenticationPrincipal AuthenticatedUser principal` (see
`UserController.me`) — never by re-parsing the token or querying
`SecurityContextHolder` directly in application code.

## Meeting domain (Milestone 2)

State machine: `CREATED → ACTIVE → ENDED` (`WAITING` is reserved for the
Milestone 7 approval/waiting-room flow and unused today). A meeting is
created in `CREATED` and only flips to `ACTIVE` on the *first successful
join* — including the host's own join, since creating a meeting and being
present in it are deliberately separate actions (`CreateMeetingUseCase` vs.
`JoinMeetingUseCase`). This mirrors real usage: a host can generate a join
link before anyone, including themselves, has actually entered.

Host vs. participant role isn't a separate field the client sets — `Meeting
.isHostedBy(userId)` (comparing against `host`) decides it at join time, so
it can't drift out of sync with who actually created the meeting.

Join and leave are both idempotent, for the same reason: a page refresh or
a flaky network re-sends the same request, and that should be a no-op, not
an error.
- **Join**: if an active (`leftAt IS NULL`) `meeting_participants` row
  already exists for (meeting, user), it's returned as-is rather than
  creating a duplicate.
- **Leave**: finds that same active row and stamps `leftAt`; leaving without
  ever having joined is a genuine `404 NOT_IN_MEETING`, not silently ignored,
  since that case usually means the client's state is wrong.

Ending a meeting is host-only (`403 NOT_MEETING_HOST` otherwise) and, beyond
marking the meeting `ENDED`, also force-closes every still-active
participant row — so "who was in the meeting when it ended" is always an
accurate query, not something the client has to reconstruct.

## Media abstraction & SFU (Milestone 11)

```java
public interface MediaServer {
    MediaRoom createRoom(UUID meetingId);
    void closeRoom(UUID meetingId);
    MediaParticipantConfig createParticipant(UUID meetingId, UUID userId, String displayName);
}
```

(package `sfu.domain`). Mesh (Milestones 4–6) needed **zero** backend media
orchestration — peer connections are negotiated entirely client-side, with
the generic signaling relay (`WEBRTC_OFFER`/`ANSWER`/`ICE_CANDIDATE`,
`SCREEN_SHARE_STARTED`/`STOPPED`) as the only server involvement — so this
interface stayed undefined until a real implementation actually needed the
seam, rather than adding an unused interface+no-op pair up front.

`sfu.infrastructure.LiveKitMediaServer` is that implementation, driving a
self-hosted [LiveKit](https://livekit.io) server (see
[networking/sfu-setup.md](../networking/sfu-setup.md) for why LiveKit was
chosen over mediasoup — mainly: it ships an official Windows binary, no
C++ toolchain needed). It's the only class that imports LiveKit's SDK
types directly:

- `createParticipant` is pure local JWT signing
  (`io.livekit.server.AccessToken`) — the SFU equivalent of
  `TurnCredentialService`: a short-lived, per-user credential derived from
  a server-only shared secret (`LIVEKIT_API_SECRET`), which never reaches
  the client. No network call, so `IssueSfuTokenUseCase` doesn't depend on
  the LiveKit server actually being reachable to issue a token.
- `createRoom`/`closeRoom` do go over the network
  (`io.livekit.server.RoomServiceClient`) and are treated as **best-effort**
  — wrapped in try/catch + logged, never thrown — since LiveKit would
  auto-create a room on first join anyway, and ending a meeting shouldn't
  fail just because the SFU happens to be unreachable at that moment.
  `EndMeetingUseCase` calls `closeRoom` for `SFU`-mode meetings alongside
  its existing force-close-all-participants logic.

`meeting.domain.MediaMode` (`MESH` | `SFU`) is a field on `Meeting`, fixed
at creation time exactly like `MeetingAccessType` — a host picks the mode
when creating the meeting; there's no mid-call migration between the two.
`POST /api/v1/meetings/{meetingId}/sfu-token` (`sfu.api.SfuController`) is
the only new REST surface: it 409s with `NOT_AN_SFU_MEETING` for a
`MESH`-mode meeting, so a client can't accidentally end up calling LiveKit
for a meeting that was never configured to use it.

Everything else — chat, waiting room, host mute/remove, reconnect
(Milestones 7–8) — is unchanged and mode-agnostic: those all run over the
existing Spring Boot WebSocket regardless of which `MediaMode` a meeting
uses. Only the WebRTC transport itself is swapped out.

## Signaling & concurrency model (Milestone 3)

Runtime meeting state (`MeetingRuntime`/`MeetingRuntimeRegistry`, package
`signaling.application`) — who's connected right now, keyed by WebSocket
session — lives in memory, not the database, since it changes many times
per second and has zero value once a meeting ends. See
[database/schema.md](../database/schema.md#what-is-not-persisted).

Each meeting room processes its signaling commands (join, leave, PING,
relay) through `RoomCommandDispatcher`: different rooms run fully
concurrently (one chained `CompletableFuture` per room, all sharing a
`newVirtualThreadPerTaskExecutor()`), while commands within one room are
strictly ordered — so two participants joining at the same instant can't
both observe a stale room snapshot. Full detail, including why this isn't a
thread-per-room design, is in
[networking/signaling.md](../networking/signaling.md#concurrency-model).

WebSocket auth happens at handshake time (`SignalingHandshakeInterceptor`),
not through `JwtAuthenticationFilter` — browsers can't attach an
`Authorization` header to a WebSocket upgrade request, so the JWT travels
as a query parameter instead and `/ws/**` is excluded from the normal
header-based auth path in `SecurityConfig`. See
[networking/signaling.md](../networking/signaling.md#authentication-on-connect)
and [api/websocket-protocol.md](../api/websocket-protocol.md).

## TURN credentials (Milestone 5)

`GET /api/v1/rtc/ice-servers` (`rtc` package) is the only place the TURN
shared secret (`TURN_SECRET`) is ever read — `TurnCredentialService` derives
a short-lived, per-user credential from it
(`Base64(HMAC-SHA1(secret, "<expiryEpochSeconds>:<userId>"))`) and that's
the only thing that ever reaches the response body. This matches Coturn's
built-in `use-auth-secret` REST API auth mode exactly, so there's no
custom protocol to keep in sync between the two services, and no shared
credential database — Coturn independently recomputes the same HMAC
against its own copy of the secret to verify a credential and read back the
embedded expiry.

`GetIceServersUseCase` degrades to STUN-only (no error) when
`TurnProperties.isTurnConfigured()` is false — i.e. `TURN_HOST`/`TURN_SECRET`
are unset — so the endpoint, and every caller of it, works identically
whether or not a Coturn server has actually been deployed yet. See
[networking/ice-stun-turn.md](../networking/ice-stun-turn.md#turn-credentials-milestone-5)
and [networking/turn-setup.md](../networking/turn-setup.md) for the
Coturn side of this.

## Reconnect grace period (Milestone 8)

`RoomCommandDispatcher` grew a second entry point,
`scheduleDispatch(meetingId, command, delay, unit)`, alongside its existing
`dispatch(...)`: it runs `command` on a tiny dedicated
`ScheduledExecutorService` timer thread after `delay`, but the command
itself still only ever executes via a normal `dispatch(...)` call — so it
lands in that room's regular serialized queue like everything else, just
later. This is what backs the reconnect grace period: on an unexpected
WebSocket close (see
[networking/signaling.md](../networking/signaling.md#reconnect-grace-period-milestone-8)),
`SignalingWebSocketHandler` marks the `RuntimeParticipant` pending-removal
and schedules its actual removal for `WS_RECONNECT_GRACE_SECONDS` later
instead of removing it inline.

`MeetingRuntime.reconnect(userId, newSession)` is the other half: it looks
up an existing participant by `userId` (not session id — the whole point is
that the old session is gone), re-points it at the new `WebSocketSession`,
and re-keys the internal `participantsBySessionId` map accordingly. Because
the delayed removal command looks the participant up by the *old* session
id when it finally runs, a successful reconnect makes it a silent no-op —
no separate "cancel this scheduled task" bookkeeping needed, the lookup
failing is the cancellation.

Both executors added by this (`RoomCommandDispatcher`'s existing
virtual-thread-per-task pool, plus the new scheduling thread) are shut down
together in the same `@PreDestroy` method, keeping lifecycle ownership in
one place.
