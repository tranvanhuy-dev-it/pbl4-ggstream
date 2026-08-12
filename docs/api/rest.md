# REST API

Base path: `/api/v1` (health check is the exception — it's under Spring Boot
Actuator's own `/actuator` path, not versioned application API).

## Implemented (Milestone 0)

### `GET /actuator/health`

Public. Returns `{"status": "UP"}` when the application and its database
connection are healthy. Used by the frontend's `BackendStatus` component and
for manual verification (`curl http://localhost:8080/actuator/health`).

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

`violations` is only present for validation failures (400).

## Planned endpoints

```
POST /api/v1/auth/register              Milestone 1
POST /api/v1/auth/login                 Milestone 1

POST /api/v1/meetings                   Milestone 2
GET  /api/v1/meetings/{meetingId}       Milestone 2
GET  /api/v1/meetings/code/{code}       Milestone 2
POST /api/v1/meetings/{meetingId}/join  Milestone 2
POST /api/v1/meetings/{meetingId}/leave Milestone 2
POST /api/v1/meetings/{meetingId}/end   Milestone 2
GET  /api/v1/meetings/{meetingId}/participants  Milestone 2

GET  /api/v1/rtc/ice-servers            Milestone 5 (temporary TURN credentials)
```

DTOs are defined per-feature under `<feature>/api/`; JPA entities are never
returned directly from a controller.
