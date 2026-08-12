# Database Schema

PostgreSQL, accessed exclusively through Spring Data JPA repositories (no raw
SQL tied to Postgres-specific syntax in application code — see
[architecture/overview.md](../architecture/overview.md#database-portability)
for why).

## Schema management strategy

`spring.jpa.hibernate.ddl-auto=${JPA_DDL_AUTO:update}` — Hibernate
auto-synchronizes the schema from JPA entities during development. This is a
deliberate, explicit project decision (not the default for production Spring
apps): it trades the safety of an explicit migration tool for faster
iteration while the schema is still taking shape. `create` / `create-drop`
are never used against a real database, since they can drop data.

Once the schema stabilizes, this can move to Flyway/Liquibase-managed
migrations without changing anything else in the architecture.

## Tables (planned — introduced as their milestone lands)

None yet — Milestone 0 has no entities. Milestone 1 adds `users`, Milestone 2
adds `meetings`, `meeting_participants`, Milestone 7 adds `chat_messages`.

### `users` (Milestone 1)

| column         | type      | notes                       |
|----------------|-----------|------------------------------|
| id             | UUID (PK) |                              |
| email          | text      | unique                      |
| password_hash  | text      | BCrypt                      |
| display_name   | text      |                              |
| avatar_url     | text      | nullable                    |
| created_at     | timestamp |                              |
| updated_at     | timestamp |                              |

### `meetings` (Milestone 2)

| column      | type      | notes                                       |
|-------------|-----------|-----------------------------------------------|
| id          | UUID (PK) |                                               |
| code        | text      | unique, used in join links                   |
| title       | text      |                                               |
| host_id     | UUID (FK → users) |                                       |
| status      | enum      | CREATED, WAITING, ACTIVE, ENDED              |
| access_type | enum      | PUBLIC, INVITED, APPROVAL_REQUIRED           |
| created_at  | timestamp |                                               |
| started_at  | timestamp | nullable                                     |
| ended_at    | timestamp | nullable                                     |

### `meeting_participants` (Milestone 2)

| column       | type      | notes                                  |
|--------------|-----------|------------------------------------------|
| id           | UUID (PK) |                                          |
| meeting_id   | UUID (FK → meetings) |                               |
| user_id      | UUID (FK → users) | nullable — anonymous participants |
| display_name | text      |                                          |
| role         | enum      | HOST, CO_HOST, PARTICIPANT              |
| joined_at    | timestamp |                                          |
| left_at      | timestamp | nullable                                |

### `chat_messages` (Milestone 7)

Persisted chat history: `id`, `meeting_id`, `sender_participant_id`,
`body`, `created_at`.

## What is *not* persisted

Runtime-only meeting state — current mic/camera toggle, active WebSocket
session id, ICE candidates, peer connection state — lives in an in-memory
`MeetingRuntime` on the backend, not in PostgreSQL. These values change many
times per second per participant and have no value once a meeting ends; only
the participant's *joined_at* / *left_at* / final role is worth persisting.
See [architecture/backend.md](../architecture/backend.md#concurrency-model-introduced-milestone-3).
