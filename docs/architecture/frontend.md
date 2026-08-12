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
- `lib/webrtc/iceServers.ts` — `fetchIceServers(token)` calls
  `GET /api/v1/rtc/ice-servers` for STUN + (if the backend has TURN
  configured) short-lived TURN credentials, rather than hardcoding either
  client-side; falls back to a bare public STUN entry
  (`NEXT_PUBLIC_STUN_URL`) if that request itself fails, so a transient API
  hiccup degrades a call's NAT-traversal options instead of blocking it.
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

## Screen share, chat, waiting room & host controls (Milestone 7)

- `lib/webrtc/PeerConnectionManager.ts` grew a `PeerEntry` per peer
  (`{pc, negotiationReady, screenSender, primaryRemoteStreamId}`) instead of
  a bare `RTCPeerConnection` map. `negotiationReady` guards
  `onnegotiationneeded`: it's false while the initial offer/answer for a
  newly-`connect()`-ed peer is still in flight, so adding the screen track
  during that window doesn't fire a second, competing offer — it flips true
  only once the initial connection is established, and from then on any
  `addTrack`/`removeTrack` (screen share start/stop) triggers a real
  renegotiation. `startScreenShare(stream)`/`stopScreenShare()` call
  `pc.addTrack`/`removeTrack` on every peer's connection, not a
  connection-replacing `getUserMedia` swap, so the camera track is
  unaffected — screen share is an *additional* track, not a substitute one.
- On the receiving side, `primaryRemoteStreamId` records the first
  `MediaStream.id` seen for a given peer (the camera) so a second stream
  arriving later is recognized as their screen share and routed to
  `onRemoteScreenStream` instead of the regular remote-video callback —
  there's no explicit "this track is a screen" flag in WebRTC itself, so
  the manager infers it from arrival order.
- `features/meeting/hooks/useScreenShare.ts` wraps
  `getDisplayMedia`/manager calls and listens for the captured track's
  native `ended` event, since the browser's own "Stop sharing" bar can end
  capture without the app's UI being involved at all.
- `features/chat/` (new feature folder) — `api/chatApi.ts` fetches history
  once on room load (`GET /meetings/{id}/messages`), then
  `components/ChatPanel.tsx` appends live `CHAT_MESSAGE` envelopes from the
  socket exactly like the roster does with `PARTICIPANT_JOINED`/`LEFT`; no
  optimistic local echo — the server's broadcast (which includes the
  sender) is the only source of a message actually appearing.
- `features/meeting/components/ParticipantsPanel.tsx` — the one place a
  join request can actually be decided. It lists everyone in
  `PARTICIPANT_WAITING` state at the top (host-only, with admit/deny
  buttons sending `PARTICIPANT_APPROVED`/`REJECTED`), followed by the full
  roster with per-participant host actions (mute/remove). It is purely a
  view over socket-driven state — no REST call of its own, since the
  waiting room is a signaling-layer concept (see
  [networking/signaling.md](../networking/signaling.md#waiting-room-milestone-7)).
  A new join request also raises a transient, auto-dismissing banner
  (`joinNotice` in `MeetingRoomView`) with a shortcut straight into this
  panel, so a host doesn't have to keep the panel open to notice someone's
  waiting — but approval/rejection is only ever done from inside it, not
  from the banner itself.
- `MeetingRoomView.tsx` now runs a small `WaitingState` state machine
  (`"none" | "waiting" | "rejected"`) for the joining client, and a
  `removedByHost` flag for `HOST_REMOVE_PARTICIPANT` that redirects home
  after a short delay so the removed user sees why before being bounced.
  Host-only mute/remove buttons render as an overlay on each remote
  participant's tile rather than a separate control panel, since they only
  ever apply to that one tile. Chat and participants share one
  `activePanel: "none" | "chat" | "participants"` slot on the right edge
  (opening one closes the other) rather than independent booleans, since
  showing both at once would leave too little room for video.

## Video layout: pin-to-spotlight (Milestone 7)

Early Milestone 7 rendered every tile (self + remote + an always-on-top
screen-share strip) in one `grid-cols-*` block that grew taller than the
viewport as participants joined, forcing the whole page to scroll — unusable
past a handful of people. `MeetingRoomView` now builds a flat list of
`Tile`s (self camera, each remote camera, the active screen share if any)
and keeps one `pinnedKey` for which tile — if any — is spotlighted:

- **No pin**: all tiles render in the original responsive grid, but the
  grid itself is the scroll container (`overflow-y-auto` on a `min-h-0
  flex-1` section) — the page (`h-screen overflow-hidden` on the root) never
  scrolls, only the tile area does when there are more participants than
  fit.
- **Pinned**: the pinned tile fills a `flex: 2` column on the left (roughly
  2/3 width) at full height (`VideoTile`'s new `fill` prop swaps its
  default `aspect-video` sizing for `h-full w-full`); every other tile
  renders small in a `flex: 1` vertical strip on the right, which scrolls
  independently (its own `overflow-y-auto`) rather than showing all
  remaining participants at once — the same "spotlight + filmstrip" pattern
  most Meet-style apps use, not a grid that keeps growing.
- Every tile carries a small pin-toggle button (top-left overlay, via
  `VideoTile`'s new `children` overlay slot); host mute/remove buttons
  reuse the same overlay slot (top-right) rather than a separate absolutely-
  positioned wrapper.
- Starting a screen share auto-pins it (`useEffect` keyed on `sharingUserId`
  sets `pinnedKey` to the screen tile's key), matching the convention that a
  presenter's screen becomes the meeting's focus without anyone having to
  pin it manually; stopping the share clears the pin only if the screen tile
  was what was pinned, so a manual pin on a person survives an unrelated
  screen share elsewhere.

## UI shell & design system

- `components/Navbar.tsx` — sticky top navbar shared by every route via the
  root layout; renders differently based on auth state (sign in/register
  links vs. avatar + name + sign out), so no page has to duplicate that
  logic. Added specifically because early milestones only rendered bare
  centered content with no persistent chrome.
- `app/globals.css` defines a `--gradient` custom property (violet → pink,
  separate light/dark values) and a `.gradient-text` utility, used sparingly
  for emphasis (hero headline, brand mark) rather than as a global theme —
  the goal was a distinct visual identity, deliberately not a Google Meet
  reproduction, while keeping the underlying call/lobby/room *functionality*
  equivalent. Fixed a real bug in the same file where `body` hardcoded
  `font-family: Arial, Helvetica, sans-serif`, silently ignoring the Geist
  fonts already loaded via `next/font` — now `var(--font-sans), Arial,
  Helvetica, sans-serif`.
- All user-facing copy (buttons, labels, empty states, error text) is
  Vietnamese, matching the backend's `ApiError.message` language (see
  [backend.md](./backend.md#error-handling)) — there's no i18n/translation
  layer on either side, Vietnamese is simply the one language the UI is
  written in.

## Reconnect (Milestone 8)

- `lib/websocket/signalingClient.ts` no longer treats every close as final:
  `ConnectionState` grew a `"reconnecting"` value, and any close that wasn't
  triggered by the client's own `close()` call (tracked via a
  `manualClose` flag) schedules a retry with exponential backoff (1s
  doubling up to a 10s cap) instead of just reporting `"closed"` and
  stopping. `close()` itself is unchanged — still an immediate, final
  disconnect, and it now also cancels any pending retry timer.
  `MeetingRoomView` shows this as a yellow pulsing "Mất kết nối, đang thử
  lại…" status instead of the red "Mất kết nối" it used to show for every
  disconnect.
- `lib/webrtc/PeerConnectionManager.ts` gained `restartIce(participantId)` —
  `createOffer({ iceRestart: true })` through the normal offer path — and
  calls it itself when a peer's `connectionState` becomes `"failed"`
  (deliberately not `"disconnected"`, which is usually transient and
  self-recovers). Each `PeerEntry` now tracks `isInitiator` (set once in
  `connect()`) so a restart, like the original connection, is only ever
  offered by one side of the pair — reusing the same glare-avoidance
  invariant instead of inventing a second one.
- `useWebRtcPeers` also triggers `restartIce` proactively on receiving a
  `PARTICIPANT_RECONNECTED` envelope (see
  [networking/webrtc.md](../networking/webrtc.md#ice-restart-milestone-8)) —
  the signaling layer resuming cleanly doesn't guarantee the separate media
  path did, so this is a nudge rather than something to wait on
  `connectionState` to notice by itself.

## Why `page.tsx` stays thin

## Network diagnostics (Milestone 9)

- `PeerConnectionManager.getStats()` phơi bày snapshot
  `RTCStatsReport` theo participant nhưng không giữ state giao diện hay
  tính toán delta.
- `features/meeting/hooks/useWebRtcStats.ts` poll manager mỗi 2 giây khi
  panel đang mở, chuẩn hóa RTT, jitter, packet loss, bitrate nhận, ICE
  candidate, protocol, codec và frames dropped. Hook giữ mẫu
  `bytesReceived/timestamp` trước đó trong ref để tính bitrate.
- `NetworkDiagnosticsPanel.tsx` hiển thị số liệu theo từng participant
  bằng nhãn tiếng Việt và đánh giá nhanh Tốt/Trung bình/Kém. Panel dùng
  chung slot `activePanel` với chat và danh sách thành viên, nên không làm
  hẹp sân khấu video bởi nhiều sidebar cùng lúc.
- Dữ liệu chỉ tồn tại trong trình duyệt và không gửi về backend. Đây là chủ
  ý của Milestone 9: đo đường media WebRTC thật tại endpoint thay vì thêm
  một lớp tổng hợp server chưa cần thiết.

## SFU mode (Milestone 11)

- `lib/sfu/liveKitClient.ts` — đối trọng của `PeerConnectionManager.ts` cho
  chế độ SFU: bọc `Room` của `livekit-client`, dịch sự kiện
  participant/track của LiveKit sang đúng hình dạng
  `Map<participantId, MediaStream>` mà UI đã dùng cho Mesh, để
  `MeetingRoomView` không cần nhánh render riêng theo transport. Khác biệt
  cốt lõi với Mesh: chỉ có **một** kết nối tới LiveKit (không phải một kết
  nối cho mỗi người khác trong phòng), track của từng participant được
  phân biệt qua `participant.identity` — chính là `userId` mà backend nhúng
  vào access token (`IssueSfuTokenUseCase`).
- `features/meeting/hooks/useSfuRoom.ts` — cùng interface bên ngoài với
  `useWebRtcPeers.ts` (`remoteStreams`, `remoteScreenStreams`,
  `startScreenShare`, `stopScreenShare`, `isScreenSharing`), nên
  `MeetingRoomView` chỉ cần chọn output của hook nào để render, không cần
  sửa JSX của lưới video/ghim khung hình/chat/danh sách thành viên.
- `MeetingRoomView.tsx` luôn gọi **cả hai** hook (`useWebRtcPeers` và
  `useSfuRoom`) — tuân thủ quy tắc hooks của React (không gọi hook có điều
  kiện) — nhưng chỉ một bên thực sự hoạt động: khi `meeting.mediaMode ===
  "SFU"`, `peerIds` truyền cho `useWebRtcPeers` luôn là mảng rỗng (Mesh
  không tạo kết nối nào), và ngược lại `useSfuRoom` nhận `sfuToken = null`
  khi ở chế độ Mesh (hiệu ứng bên trong hook tự thoát sớm, không kết nối gì
  cả).
- `features/meeting/api/meetingApi.ts` có `fetchSfuToken(token, meetingId)`
  — gọi 1 lần khi vào phòng ở chế độ SFU, tương tự cách `fetchIceServers`
  được gọi cho Mesh — trả về `{ url, token }` để `Room.connect()` dùng
  trực tiếp.
- Chọn chế độ media (`MESH` mặc định hoặc `SFU`) là một checkbox trong
  `CreateJoinMeeting.tsx` lúc tạo phòng, cùng kiểu với checkbox
  `requireApproval` đã có — cố định cho cả vòng đời cuộc họp, không đổi
  giữa chừng.
- Tắt tiếng do chủ phòng (`HOST_MUTE_PARTICIPANT`) dùng lại nguyên vẹn sự
  kiện signaling đã có — không cần thay đổi gì ở backend hay ở logic xử lý
  sự kiện, vì hành vi vẫn là "người bị tắt tiếng tự tắt track mic của
  chính mình", chỉ khác là track đó giờ được publish qua LiveKit thay vì
  qua `PeerConnectionManager`.

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
