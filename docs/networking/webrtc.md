# WebRTC

**Status: 1:1 calling implemented (Milestone 4). Mesh (3+ participants) is
Milestone 6, SFU is Milestone 11 — see below for why the same code already
supports both.**

## SDP offer/answer, ICE, and why media skips the backend

`RTCPeerConnection` negotiates a call in three interleaved steps, all
happening client-side in `lib/webrtc/PeerConnectionManager`:

1. **SDP offer/answer** — each side describes its media capabilities
   (codecs, tracks) as an SDP blob. One side calls `createOffer()`, sends it
   to the other over signaling; the other calls `setRemoteDescription()`
   then `createAnswer()`, sends that back. Once both sides have called
   `setLocalDescription`/`setRemoteDescription` with matching
   offer/answer, the connection has agreed on *what* to send.
2. **ICE candidate gathering** — in parallel, each browser asks the OS for
   every network path it might be reachable on (local LAN address, and via
   STUN, its public-facing address behind NAT — see
   [ice-stun-turn.md](./ice-stun-turn.md)) and sends each one to the other
   side as it's discovered (`onicecandidate`). This decides *how* the two
   browsers actually reach each other.
3. Once ICE finds a working candidate pair, a **DTLS handshake** negotiates
   encryption keys for the media itself (**SRTP** — encrypted RTP), then
   audio/video **RTP** packets flow directly between the two browsers.

The Spring Boot backend participates in exactly step 1 and the candidate
exchange in step 2 — as a dumb relay (`WEBRTC_OFFER`/`WEBRTC_ANSWER`/
`ICE_CANDIDATE`, routed by `targetId`, see
[signaling.md](./signaling.md)) — and never sees the DTLS handshake or any
RTP packet. That's not an optimization, it's the whole point of using
WebRTC instead of routing media through the app server: the backend would
otherwise have to handle N× the bandwidth of every call in the system.

## Offer glare and who initiates

If both sides of a pair called `createOffer()` at the same moment (e.g.
both react to seeing each other join at once), you'd get two competing
offers — "glare". `useWebRtcPeers` avoids this without any extra
coordination message: whichever participant has the lexicographically
smaller `userId` always initiates, deterministically, for every pair. The
other side just waits to receive an offer and answers it.

## Mesh-ready by construction

`PeerConnectionManager` was built as `Map<participantId, RTCPeerConnection>`
from the start — Milestone 4 only ever populates that map with one entry
(the other participant in a 1:1 call), but the class itself doesn't know
or care how many peers it's managing. Milestone 6 doesn't change this
class at all; it only changes how many participants are in the room the
existing reconciliation logic (`useWebRtcPeers`, connect-new/disconnect-
gone based on the room's participant list) is already watching.

## Mesh vs SFU

Mesh: every participant opens one `RTCPeerConnection` per other participant.
For `n` participants that's `n(n-1)/2` peer connections total, and each
participant uploads their own stream `n-1` times — upload bandwidth and CPU
(encoding) scale linearly with room size per participant. This is fine for
small rooms (Milestone 6 targets 3–6 participants) and breaks down quickly
beyond that.

SFU: each participant sends **one** upload to a central relay, which forwards
(without re-encoding) to every other participant. Upload cost per participant
becomes constant; the SFU absorbs the fan-out. Milestone 10 measures Mesh's
actual bandwidth/CPU/RTT/packet-loss behavior at 2–6 participants before
Milestone 11 justifies and designs the SFU migration — see the root README's
milestone plan.

## WebRTC statistics

Once media is flowing (Milestone 9), `RTCPeerConnection.getStats()` is
polled for RTT, jitter, packet loss, bitrate, frames dropped, candidate
type/protocol, and codec — surfaced in a developer/debug panel. This is the
data the project's networking report draws on.
