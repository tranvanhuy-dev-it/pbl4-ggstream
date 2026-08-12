# Coturn Setup (Local, No Docker)

**Status: backend/frontend TURN credential plumbing is implemented and
active; the Coturn server itself is not installed in this project's dev
environment yet** (see [ice-stun-turn.md](./ice-stun-turn.md) for why: STUN
alone is enough for same-network testing, TURN only matters for symmetric-NAT/
cross-network calls — install it when you actually need to test or demo
that path). `GET /api/v1/rtc/ice-servers` already degrades gracefully to
STUN-only when `TURN_HOST`/`TURN_SECRET` are unset, so nothing breaks either
way.

## Why Coturn, and why not Docker

Coturn is the de facto standard open-source TURN/STUN server implementation
— what most production WebRTC deployments use. It's a native Linux
service; there's no official Windows build. Two honest options if your dev
machine is Windows:

1. **Run it in WSL2** (Windows Subsystem for Linux) — not a container, a
   real lightweight Linux VM integrated into Windows. This is not "Docker"
   in the sense this project avoids (no image layers, no container
   orchestration, no `docker-compose.yml`) — it's the same kind of thing as
   installing PostgreSQL directly, just on a Linux subsystem instead of
   native Windows.
2. **Deploy it on an actual Linux host/VPS** if you have one — the
   production-realistic option, and what section 2 below assumes.

## 1. Install

Debian/Ubuntu (native Linux, or WSL2):

```bash
sudo apt update
sudo apt install coturn
```

Fedora/RHEL:

```bash
sudo dnf install coturn
```

Confirm it installed:

```bash
turnserver --version
```

## 2. Configure

Edit `/etc/turnserver.conf`. The minimal config matching this project's
backend (`TurnCredentialService`, `app.turn.*` properties):

```ini
# Standard TURN/STUN port
listening-port=3478

# Public IP of this machine (or the host reachable from browsers).
# On a home network behind your own router, this needs port forwarding —
# see step 4.
external-ip=YOUR_PUBLIC_OR_REACHABLE_IP

# Time-limited credential auth (matches TurnCredentialService's
# HMAC-SHA1(secret, "expiryTimestamp:userId") scheme exactly — this is
# Coturn's built-in "REST API" auth mode, not a custom protocol)
use-auth-secret
static-auth-secret=REPLACE_WITH_THE_SAME_VALUE_AS_TURN_SECRET_IN_.ENV

# A realm is required in this auth mode; the exact value doesn't matter
# as long as it's consistent
realm=meet-platform.local

# Relay port range — the ports Coturn actually uses to forward media once
# a session is relayed. Keep this range small and consistent so firewall
# rules (step 4) stay simple.
min-port=49152
max-port=49452

# Log to a normal file instead of syslog, easier to tail while testing
log-file=/var/log/turnserver.log
verbose

# Don't relay to/from this machine's own private/loopback ranges —
# standard hardening, prevents Coturn being abused as an open relay into
# your own LAN
no-loopback-peers
no-multicast-peers
```

Generate a secret and use the **same value** for `static-auth-secret` here
and `TURN_SECRET` in `apps/server/.env`:

```bash
openssl rand -base64 32
```

## 3. Run

```bash
sudo turnserver -c /etc/turnserver.conf
```

Or as a persistent service (systemd, most distros ship a unit file):

```bash
sudo systemctl enable --now coturn
```

## 4. Open the ports

Coturn needs, at minimum:

- **UDP + TCP 3478** — the STUN/TURN control port (candidate gathering,
  TURN allocation requests)
- **UDP 49152–49452** (or whatever `min-port`/`max-port` you set) — the
  actual relayed media

If Coturn runs on a machine behind a router (home network), you need port
forwarding for all of the above, in addition to any OS-level firewall
(`ufw`/`firewalld`) rules:

```bash
# ufw example
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 49152:49452/udp
```

## 5. Point the backend at it

```env
# apps/server/.env
TURN_HOST=your-coturn-host-or-ip
TURN_PORT=3478
TURN_SECRET=the-same-secret-from-static-auth-secret-above
TURN_CREDENTIAL_TTL_SECONDS=3600
```

Restart the backend. `GET /api/v1/rtc/ice-servers` (authenticated) should
now return both a STUN entry and a TURN entry with a freshly-generated
username/credential pair.

## 6. Test the relay candidate actually works

The most reliable test is forcing a call to use **only** the relay
candidate, since ICE always prefers a direct (host/srflx) path when one is
available — so a normal call succeeding doesn't prove TURN works at all.

**Quick check with `turnutils_uclient`** (ships with Coturn):

```bash
turnutils_uclient -u dummy -w dummy -y your-coturn-host
```

(A basic connectivity check — a fuller allocation test needs the actual
HMAC credential, which is more easily verified from the browser.)

**From the browser** (most representative test): open
[Trickle ICE](https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/),
paste in one STUN server and one TURN server using a credential fetched
from `GET /api/v1/rtc/ice-servers` (call it once via `curl` with a real JWT,
copy `username`/`credential` from the response), and confirm a `relay`
candidate appears in the gathered candidates list — not just `host` and
`srflx`.

## Credential lifetime

Credentials from `TurnCredentialService` expire after
`TURN_CREDENTIAL_TTL_SECONDS` (default 1 hour) — Coturn independently
recomputes the HMAC against its own `static-auth-secret` and checks the
embedded expiry timestamp, so there's no server-side credential store to
manage or revoke on either side.
