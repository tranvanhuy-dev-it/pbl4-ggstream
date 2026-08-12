# Connection Flow

Full sequence for a 1:1 call, as implemented (`useWebRtcPeers` +
`PeerConnectionManager` on the frontend, generic relay in
`SignalingWebSocketHandler` on the backend — see
[signaling.md](./signaling.md)):

```
Browser A (smaller userId)          Java (relay only)          Browser B
       │                                   │                          │
       │  both already connected to /ws/meetings/{id}, know each     │
       │  other's userId via PARTICIPANT_JOINED (snapshot/broadcast) │
       │                                   │                          │
  createOffer()                            │                          │
  setLocalDescription(offer)               │                          │
       │──── WEBRTC_OFFER, targetId=B ────▶│──── relay by targetId ──▶│
       │                                   │                          │  setRemoteDescription(offer)
       │                                   │                          │  createAnswer()
       │                                   │                          │  setLocalDescription(answer)
       │◀──── relay by targetId ───────────│◀── WEBRTC_ANSWER ────────│
  setRemoteDescription(answer)             │                          │
       │                                   │                          │
       │  (throughout, both directions, as each browser's ICE agent  │
       │   discovers candidates)                                     │
       │──── ICE_CANDIDATE, targetId=B ───▶│──── relay ──────────────▶│
       │◀──── relay ────────────────────────│◀─── ICE_CANDIDATE ──────│
       │                                   │                          │
       │        ICE connectivity checks find a working candidate pair
       │        DTLS handshake negotiates SRTP keys
       │                                   │                          │
       ║═══════════════ SRTP media (audio/video RTP) ═════════════════║
       ║              directly between browsers, no backend           ║
```

Java is on the wire for exactly four message types
(`WEBRTC_OFFER`/`WEBRTC_ANSWER`/`ICE_CANDIDATE`, relayed by `targetId`) and
never touches anything after that — the DTLS handshake and all RTP/RTCP
traffic is browser-to-browser only.

## Which side is "A"

There's no dedicated caller/callee role — `useWebRtcPeers` decides per pair,
deterministically, by comparing `userId`s (lexicographically smaller
initiates). See [webrtc.md](./webrtc.md#offer-glare-and-who-initiates) for
why.
