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

## Tables

`users`, `meetings`, and `meeting_participants` are implemented. Only
`chat_messages` (Milestone 7) is still planned.

### `users`

| column         | type      | notes                       |
|----------------|-----------|------------------------------|
| id             | UUID (PK) |                              |
| email          | text      | unique                      |
| password_hash  | text      | BCrypt                      |
| display_name   | text      |                              |
| avatar_url     | text      | nullable                    |
| created_at     | timestamp |                              |
| updated_at     | timestamp |                              |

### `meetings`

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

### `meeting_participants`

| column       | type      | notes                                  |
|--------------|-----------|------------------------------------------|
| id           | UUID (PK) |                                          |
| meeting_id   | UUID (FK → meetings) |                               |
| user_id      | UUID (FK → users) | nullable — reserved for anonymous/guest participants, always populated today |
| display_name | text      |                                          |
| role         | enum      | HOST, CO_HOST, PARTICIPANT              |
| joined_at    | timestamp |                                          |
| left_at      | timestamp | nullable                                |

One row per join/leave session, not one row per (meeting, user) — rejoining
after leaving inserts a new row rather than reusing the old one, so a
meeting's full attendance history is reconstructable from this table alone.
`meetingId`/`userId` combined with `leftAt IS NULL` identifies the *current*
active session for a user in a meeting.

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
