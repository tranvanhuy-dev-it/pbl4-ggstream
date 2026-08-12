# ICE, STUN, TURN, NAT Traversal

**Status: planned. STUN from Milestone 4, TURN from Milestone 5.**

Will cover: why two peers behind NAT can't connect directly without help,
how STUN discovers a peer's public-facing address (server-reflexive
candidate), why STUN alone fails behind symmetric NAT, how TURN relays media
as a fallback (relay candidate), and how ICE picks the best working
candidate pair (host > server-reflexive > relay, by connectivity + priority).

TURN credentials are never static: the browser fetches short-lived, backend-
issued temporary credentials from `GET /api/v1/rtc/ice-servers` (Milestone 5)
so the TURN shared secret itself never reaches the client. See
[turn-setup.md](./turn-setup.md) for the Coturn install/config guide once
that milestone starts.
