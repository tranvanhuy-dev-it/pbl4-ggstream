# Architecture Overview

## Goal

A realtime meeting platform (Google Meet-like) built as a **modular monolith**,
with an explicit split between:

- **Control plane** (Spring Boot): auth, meeting/participant state, signaling,
  chat, persistence. Talks to browsers over HTTPS (REST) and WSS (signaling).
- **Media plane** (WebRTC, browser-to-browser): actual audio/video/screen-share
  RTP media. Never touches the Spring Boot process.

```
                    ┌─────────────────────┐
                    │      Next.js        │
                    │      Frontend       │
                    └──────────┬──────────┘
                               │
                        HTTPS / WSS
                               │
                               ▼
                    ┌─────────────────────┐
                    │    Spring Boot      │
                    │    Control Plane    │
                    │                     │
                    │ Auth                │
                    │ Meeting             │
                    │ Participant         │
                    │ Signaling           │
                    │ Chat                │
                    │ Session             │
                    └──────────┬──────────┘
                               │
                         PostgreSQL

Browser A ═════════ WebRTC Media ═════════ Browser B
                    │
                 STUN/TURN
```

Livestream (SFU → transcoder → HLS → CDN) is a future extension and is not
implemented until the meeting product (1:1 → Mesh → SFU) is stable. See
[Milestone Plan](../../README.md#milestones).

## Why no Docker

Every service (PostgreSQL, Coturn, the JVM, Node) runs directly on the host.
This keeps the project's networking layer (ports, NAT, firewall rules)
visible and inspectable — which matters for a Computer Networks course
project — instead of hidden behind container networking.

## Why this repo is a modular monolith, not microservices

At this stage there is a single team, a single deployable backend, and no
independent scaling requirement between features. Splitting into services
now would add network hops, serialization, and deployment complexity with
no corresponding benefit. The backend is still organized **by feature**
(package-by-feature, see [backend.md](./backend.md)) so that a future service
extraction — if ever needed — has clean seams to cut along.

## Why the media server is abstracted (Mesh today, SFU later)

`MediaServer` (see [backend.md](./backend.md#media-abstraction)) is an
interface the `meeting` domain depends on. Milestone 4–7 use a Mesh
implementation (participants connect directly to each other, no server-side
media relay). Milestone 11 introduces an SFU implementation once Mesh's
`O(n²)` connection growth has been measured and documented (see
[Mesh Benchmark](../networking/webrtc.md#mesh-vs-sfu)). The `meeting` domain
itself never depends on a specific media vendor.

## Database portability

The backend never talks to PostgreSQL through vendor-specific SQL in
application code — all data access goes through Spring Data JPA repository
interfaces using JPQL/derived queries, and connection details are 100%
externalized (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` in
`.env`, see [database/schema.md](../database/schema.md)). Swapping the
database engine later is a matter of changing the JDBC driver dependency and
the three connection env vars — no application code changes required, as
long as the replacement is a standard relational database Hibernate has a
dialect for.

## Current status

- **Milestone 0 (this milestone):** monorepo, backend/frontend skeletons,
  config, error handling, health check.
- Everything else in this document describes the target architecture and is
  built up incrementally — see the root [README](../../README.md) for the
  milestone order.
