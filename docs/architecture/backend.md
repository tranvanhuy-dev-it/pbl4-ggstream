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
│   ├── security/       # SecurityFilterChain
│   └── web/             # RequestIdFilter (request correlation)
├── auth/              # Milestone 1
├── user/              # Milestone 1
├── meeting/           # Milestone 2
├── participant/       # Milestone 2
├── signaling/         # Milestone 3
├── chat/              # Milestone 7
├── media/             # media abstraction (Mesh → SFU), Milestone 4+
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
settings Spring Boot doesn't already model: `CorsProperties` now,
`JwtProperties` (Milestone 1), `TurnProperties` (Milestone 5),
`WebSocketProperties` (heartbeat/reconnect tuning, Milestone 3/8).

No `System.getenv(...)` calls anywhere in application code.

## Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) translates exceptions into
a stable `ApiError` JSON shape:

```json
{
  "code": "MEETING_NOT_FOUND",
  "message": "Meeting not found",
  "timestamp": "2026-08-12T10:00:00Z",
  "requestId": "b3f1...",
  "violations": null
}
```

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

## Security (current state)

`SecurityConfig` at this milestone is intentionally minimal: stateless
session policy, CSRF disabled (pure JSON API, no cookie-based auth yet), all
requests permitted. JWT authentication, `/api/v1/auth/**` public endpoints,
and per-endpoint authorization rules land in Milestone 1.

## Media abstraction

```java
public interface MediaServer {
    MediaRoom createRoom(UUID meetingId);
    void closeRoom(UUID meetingId);
    MediaParticipantConfig createParticipant(UUID meetingId, UUID participantId);
}
```

`meeting` will depend on this interface, not a concrete media server. Milestone
4–7 ship a `MeshMediaServer` (effectively a no-op — Mesh peer connections are
negotiated entirely client-side via signaling); Milestone 11 adds an
`SfuMediaServer`. This interface does not exist yet in code — it's introduced
alongside the `meeting` domain in Milestone 2/4, documented here ahead of time
so the intent is clear before it's built.

## Concurrency model (introduced Milestone 3+)

Runtime meeting state (who's in the room, mute state, active screen share)
lives in memory, not the database — see
[database/schema.md](../database/schema.md#what-is-not-persisted). Each
meeting room processes its signaling commands (JOIN, OFFER, ICE_CANDIDATE,
MUTE, ...) through a per-room ordered queue, so that different rooms run
fully concurrently while commands within one room are serialized to avoid
races (e.g. two participants joining at the same instant both reading a
stale participant list). Full detail lands in
[networking/signaling.md](../networking/signaling.md) once Milestone 3 is
implemented.
