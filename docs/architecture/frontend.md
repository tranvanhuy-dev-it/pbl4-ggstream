# Frontend Architecture

Next.js 16 (App Router), TypeScript, Tailwind CSS.

## Folder structure (target)

```
src/
├── app/            # routes only — no WebRTC/business logic in page.tsx
├── features/        # auth, meeting, lobby, chat — feature-scoped UI + hooks
│   └── meeting/
│       ├── components/
│       ├── hooks/
│       └── services/
├── components/      # shared, cross-feature UI primitives
├── hooks/           # shared, cross-feature hooks
├── lib/
│   ├── api/          # REST client
│   ├── websocket/     # signaling client
│   └── webrtc/        # PeerConnectionManager, ICE server config
├── stores/          # client state, added when a feature needs shared state
└── types/           # shared TypeScript types
```

As with the backend, folders are created when the feature that needs them
lands (see the milestone order in the root README), not pre-scaffolded
empty. `features/auth` (Milestone 1), `features/meeting` / `features/lobby`
(Milestone 2), `lib/websocket` (Milestone 3), and `lib/webrtc` (Milestone 4)
all exist now.

`hooks/` and `components/` hold anything used by **two or more** features —
`useLocalMedia` and the video-tile/media-button UI started out
lobby-specific (Milestone 2) but moved there once the room page (Milestone
4) needed the same camera/mic capture and the same tile rendering for
remote peers, not just the lobby preview.

## Meeting & lobby (Milestone 2)

- `features/meeting/api/meetingApi.ts` — thin wrappers over `apiFetch` for
  create/get/join/leave/end/participants, each taking the JWT explicitly
  (no ambient auth state inside the API layer — the caller decides).
- `hooks/useLocalMedia.ts` — requests `getUserMedia({video:true,audio:true})`
  on mount, exposes the `MediaStream` plus mic/camera toggle functions that
  just flip `track.enabled`. Used independently by the lobby (preview) and
  the room (the actual call) — each gets its own `getUserMedia` call/stream,
  the browser just reuses the cached permission grant so the room doesn't
  re-prompt.

## Signaling (Milestone 3)

- `lib/websocket/signalingClient.ts` — a thin wrapper around the native
  `WebSocket` API for one meeting's connection: connects to
  `${NEXT_PUBLIC_WS_URL}/ws/meetings/{meetingId}?token=...`, sends an
  application-level `PING` every 10s, and hands every parsed envelope to a
  caller-supplied handler. No auto-reconnect on drop — that's Milestone 8;
  today a dropped connection just reports `"closed"` and stays that way.
- `features/meeting/hooks/useMeetingSocket.ts` — the React-facing wrapper:
  opens the connection for a `(meetingId, token)` pair, exposes connection
  `status`, and forwards every envelope to an `onMessage` callback. Holds no
  participant state itself, so it stays reusable once
  `WEBRTC_OFFER`/`ANSWER`/`ICE_CANDIDATE` routing lands in Milestone 4 — the
  caller decides what each event type means.
- `features/meeting/components/MeetingRoomView.tsx` seeds its roster once
  from `GET .../participants` (REST is the source of truth for "who's here
  at page-load"), then applies `PARTICIPANT_JOINED`/`PARTICIPANT_LEFT`
  events from the socket as deltas from that point forward — replacing the
  4s-polling placeholder from Milestone 2.

## WebRTC (Milestone 4)

- `lib/webrtc/PeerConnectionManager.ts` — owns
  `Map<participantId, RTCPeerConnection>` (see
  [networking/webrtc.md](../networking/webrtc.md#mesh-ready-by-construction)
  for why it's built mesh-ready even though Milestone 4 only ever has one
  entry in that map). Queues ICE candidates that arrive before the remote
  description is set rather than dropping them — `addIceCandidate()` throws
  if called too early, and the offer/answer round trip and ICE gathering
  race each other over the network, so this isn't an edge case, it happens
  routinely.
- `lib/webrtc/iceServers.ts` — STUN-only for now
  (`NEXT_PUBLIC_STUN_URL`, default Google's public STUN server); TURN
  arrives Milestone 5.
- `features/meeting/hooks/useWebRtcPeers.ts` — reconciles the manager
  against the room's actual participant list (connect newcomers, tear down
  anyone who left) and routes `WEBRTC_OFFER`/`WEBRTC_ANSWER`/
  `ICE_CANDIDATE` envelopes from the signaling socket into the manager.
  Decides who initiates each pairwise connection by comparing `userId`s
  (smaller one offers) rather than reacting to "who joined when" — see
  [networking/webrtc.md](../networking/webrtc.md#offer-glare-and-who-initiates)
  for why that avoids offer glare with zero extra coordination messages.
- `components/VideoTile.tsx` — renders one participant's stream; `mirrored`
  is only ever `true` for the local self-view (the convention every video
  call app follows so your own camera feels like a mirror), never for
  remote peers — a mirrored remote video would show the other person
  backwards.

## Why `page.tsx` stays thin

WebRTC and signaling state machines are non-trivial and need to survive
route-level re-renders and be independently testable. They live in
`lib/webrtc` / `lib/websocket` behind small hook APIs
(`useLocalMedia`, `useMeeting`, ...) that feature components consume — never
raw `RTCPeerConnection` calls sprinkled across JSX.

## API client (`lib/api`)

`lib/api/client.ts` exports `apiFetch<T>(path, init)`: a thin wrapper around
`fetch` against `NEXT_PUBLIC_API_URL` that parses the backend's `ApiError`
shape (see [backend.md](./backend.md#error-handling)) into a typed
`ApiClientError` on non-2xx responses. Feature-specific API calls (e.g.
`createMeeting`) will be added under `features/meeting/services` and build on
top of this, not duplicate fetch logic.

## Environment variables

Only `NEXT_PUBLIC_*` values live in `.env.local` — anything the browser can
read is, by definition, not a secret. TURN credentials are fetched from the
backend at runtime as short-lived temporary credentials (Milestone 5), never
baked into a frontend env var.
