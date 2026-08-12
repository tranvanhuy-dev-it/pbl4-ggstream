# ICE, STUN, TURN, NAT Traversal

**Status: STUN implemented (Milestone 4). TURN credential issuance is
implemented (Milestone 5) but no Coturn server is deployed in this
project's dev environment yet — see
[turn-setup.md](./turn-setup.md#status-backendfrontend-turn-credential-plumbing-is-implemented-and-active)
for why that's a deliberate, not-yet-needed step.**

## The problem: NAT

Most devices, including on the same home/office network, sit behind NAT
(Network Address Translation) — they don't have a public IP address the
rest of the internet can reach directly. Two browsers on different
networks can't just connect to each other's local address (`192.168.x.x`,
`10.x.x.x`); each only knows its own *private* address, not the
NAT-translated *public* one the other side would need to dial.

## STUN: discovering your own public address

A STUN (Session Traversal Utilities for NAT) server's entire job is: a
client sends it a UDP packet, and the server replies with "here's the
public IP:port I saw this packet come from." That's the client's
NAT-translated address — a **server-reflexive candidate**. The browser
gathers this alongside its local (**host**) candidate and sends both to the
other peer over signaling; ICE then tries every combination of candidate
pairs (host↔host, host↔srflx, srflx↔srflx) and picks whichever pair
actually completes a connectivity check.

This project uses Google's public STUN server by default
(`stun:stun.l.google.com:19302`, configurable via
`NEXT_PUBLIC_STUN_URL`) — `lib/webrtc/iceServers.ts`. STUN is free, requires
no credentials, and is enough for most NAT types (full-cone, restricted-cone,
port-restricted-cone) as long as *at least one* side isn't behind the
hardest case:

## Where STUN alone isn't enough: symmetric NAT

A symmetric NAT assigns a *different* public port for every distinct
destination a client talks to. STUN still correctly reports "here's your
address as seen by the STUN server" — but that address is only valid for
traffic to *that* STUN server, not to the other peer, since the NAT will
allocate a different port for that new destination. Two peers both behind
symmetric NAT (or one symmetric + the other restrictive) frequently cannot
establish a direct path no matter how many STUN candidates they exchange.

This is exactly the gap TURN closes: instead of trying to connect
peer-to-peer, both sides connect to a TURN *relay* server (each still just
a normal outbound connection, which every NAT allows) and it forwards
traffic between them. It costs bandwidth on a server in the path — the
thing WebRTC otherwise avoids — but it's the only thing that works for
that NAT combination. ICE always tries host and server-reflexive candidates
first; a relay candidate is the fallback of last resort, not a replacement
for STUN.

## TURN credentials (Milestone 5)

TURN credentials are never static — the browser fetches short-lived,
backend-issued temporary credentials from `GET /api/v1/rtc/ice-servers`
(`rtc.application.TurnCredentialService`) so the TURN shared secret itself
never reaches the client. The credential is
`Base64(HMAC-SHA1(TURN_SECRET, "<expiryUnixTimestamp>:<userId>"))` — Coturn's
standard `use-auth-secret` REST API auth mode, which lets Coturn verify a
credential by recomputing the same HMAC against its own copy of the secret,
with no shared credential database between the two services. If
`TURN_HOST`/`TURN_SECRET` aren't configured, the endpoint returns
STUN-only rather than erroring — see
[turn-setup.md](./turn-setup.md) for the Coturn install/config guide.
