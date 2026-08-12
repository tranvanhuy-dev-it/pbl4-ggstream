# Connection Flow

**Status: planned, Milestone 4.**

Will diagram the full sequence for a 1:1 call: `createOffer` → signal via
WebSocket → Java relays to the other browser → `createAnswer` → signal back
→ ICE candidate exchange (both directions, throughout) → DTLS handshake →
SRTP media flowing directly between browsers. The Java backend participates
only in the signaling relay steps, never the media path.
