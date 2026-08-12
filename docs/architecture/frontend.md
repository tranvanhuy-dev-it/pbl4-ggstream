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
│   ├── api/          # REST client (this milestone)
│   ├── websocket/     # signaling client (Milestone 3)
│   └── webrtc/        # PeerConnectionManager (Milestone 4+)
├── stores/          # client state, added when a feature needs shared state
└── types/           # shared TypeScript types
```

As with the backend, folders are created when the feature that needs them
lands (see the milestone order in the root README), not pre-scaffolded
empty. `features/auth` (Milestone 1) and `features/meeting` /
`features/lobby` (Milestone 2) exist now; `lib/websocket` and `lib/webrtc`
land with Milestone 3/4.

## Meeting & lobby (Milestone 2)

- `features/meeting/api/meetingApi.ts` — thin wrappers over `apiFetch` for
  create/get/join/leave/end/participants, each taking the JWT explicitly
  (no ambient auth state inside the API layer — the caller decides).
- `features/lobby/hooks/useLocalMedia.ts` — requests
  `getUserMedia({video:true,audio:true})` on mount, exposes the
  `MediaStream` plus mic/camera toggle functions that just flip
  `track.enabled` (no renegotiation involved, since there's no peer
  connection yet — that's Milestone 4). This is the only piece of "real"
  WebRTC-adjacent browser API in the app so far; `RTCPeerConnection` itself
  doesn't appear until Milestone 4, layered on top of the same stream this
  hook produces.
- `features/meeting/components/MeetingRoomView.tsx` polls
  `GET .../participants` every 4s as a stand-in for realtime updates —
  explicitly temporary, replaced by `PARTICIPANT_JOINED`/`PARTICIPANT_LEFT`
  WebSocket events once signaling exists (Milestone 3). Documented in the
  component itself so it isn't mistaken for the intended final design.

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
