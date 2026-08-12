# WebRTC

**Status: planned, Milestone 4 (1:1) → Milestone 6 (Mesh) → Milestone 11 (SFU).**

This document will explain, with references to this project's actual code:

- SDP offer/answer negotiation
- RTP/RTCP media transport, DTLS-SRTP encryption
- ICE candidate gathering and connectivity checks
- why media never routes through the Spring Boot process

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
