# Meet Platform

A realtime meeting platform (Google Meet-style) built to demonstrate the
full WebRTC/networking stack — SDP, ICE, STUN/TURN, NAT traversal, RTP/RTCP,
DTLS-SRTP, WebSocket signaling, Mesh → SFU scaling — as a Computer Networks
course project. Meeting first; livestream (SFU → transcoder → HLS) is a
later extension, not built until meeting is stable.

No Docker. Every service runs directly on your machine.

See [docs/architecture/overview.md](docs/architecture/overview.md) for the
full architecture and the reasoning behind it.

## Repository layout

```
meet-platform/
├── apps/
│   ├── web/            Next.js frontend
│   └── server/         Spring Boot backend
├── services/
│   ├── turn/            Coturn config (Milestone 5+)
│   └── livestream/      livestream pipeline (post-SFU)
├── packages/
│   └── contracts/       shared types/contracts, if/when needed
├── docs/                architecture, API, database, networking docs
└── scripts/
```

## Prerequisites

Install directly on your machine — no containers:

- **Java 21+** (JDK) — `java -version`
- **Maven** — the repo ships `apps/server/mvnw`, so a system Maven install
  is optional
- **Node.js 20+** and npm — `node -v`
- **PostgreSQL 16+** — see setup below

## 1. PostgreSQL setup

Install PostgreSQL for your OS (e.g. the official installer from
[postgresql.org](https://www.postgresql.org/download/), or your package
manager). On Windows this installs as a service named `postgresql-x64-<version>`.

Make sure the service is running:

```powershell
# Windows, elevated PowerShell
Start-Service postgresql-x64-18   # adjust version suffix to match your install
Get-Service postgresql-x64-18
```

```bash
# Linux
sudo systemctl start postgresql
```

Create the application database and a dedicated user (replace the password):

```bash
psql -U postgres
```

```sql
CREATE DATABASE meet_platform;
CREATE USER meet_user WITH PASSWORD 'changeme';
GRANT ALL PRIVILEGES ON DATABASE meet_platform TO meet_user;
\c meet_platform
GRANT ALL ON SCHEMA public TO meet_user;
```

## 2. Backend setup

```bash
cd apps/server
cp .env.example .env
```

Edit `.env` — at minimum set `DATABASE_PASSWORD` to match what you created
above. `JWT_SECRET` is only required from Milestone 1 onward but you can
generate one now:

```bash
# any random 32+ byte string works; example using openssl
openssl rand -base64 48
```

Run it:

```bash
./mvnw spring-boot:run
```

Windows:

```bash
mvnw.cmd spring-boot:run
```

Verify:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Run tests (uses an in-memory H2 database, not your local PostgreSQL):

```bash
./mvnw test
```

## 3. Frontend setup

```bash
cd apps/web
cp .env.example .env.local
npm install
npm run dev
```

Open http://localhost:3000 — the homepage shows a live backend connectivity
indicator (green = backend reachable).

## Environment variables

Never commit `.env` / `.env.local` — only `.env.example` files are tracked.

- `apps/server/.env` — database credentials, JWT secret, TURN shared secret.
  Never exposed to the frontend.
- `apps/web/.env.local` — only `NEXT_PUBLIC_*` values. Anything the browser
  can read is not a secret by definition; TURN credentials the browser uses
  are short-lived and issued by the backend at runtime (Milestone 5), never
  baked into a frontend env var.

## Milestone plan

Implemented sequentially, each one built, tested, and documented before the
next starts:

0. **Project foundation** ← you are here
1. Authentication (JWT)
2. Meeting domain (create/join/leave, no WebRTC yet)
3. WebSocket signaling foundation
4. WebRTC 1:1 calls (STUN only)
5. TURN (Coturn, temporary credentials)
6. Group Mesh meetings (3–6 participants)
7. Meeting controls (screen share, chat, host/waiting room)
8. Reliability (reconnect, heartbeat timeout, renegotiation)
9. Network diagnostics (WebRTC stats, debug panel)
10. Mesh benchmark (measure the scaling limit before building an SFU)
11. SFU design & migration
12. Livestream (SFU → transcoder → HLS → viewer)

## Documentation

- [Architecture overview](docs/architecture/overview.md)
- [Backend architecture](docs/architecture/backend.md)
- [Frontend architecture](docs/architecture/frontend.md)
- [Database schema](docs/database/schema.md)
- [REST API](docs/api/rest.md)
- [WebSocket signaling protocol](docs/api/websocket-protocol.md)
- [Networking theory used in GGStream](docs/networking/network-theory-in-ggstream.md)
- [Networking: WebRTC](docs/networking/webrtc.md), [signaling](docs/networking/signaling.md), [ICE/STUN/TURN](docs/networking/ice-stun-turn.md), [connection flow](docs/networking/connection-flow.md), [TURN setup](docs/networking/turn-setup.md), [SFU (LiveKit) setup](docs/networking/sfu-setup.md)
