# Signaling

**Status: planned, Milestone 3.**

Will cover: WebSocket authentication on connect, the envelope/event router
(see [api/websocket-protocol.md](../api/websocket-protocol.md)), heartbeat
(PING/PONG, timeout thresholds), and the concurrency model —

```
WebSocket connections
        │
        ▼
Signaling Gateway
        │
        ▼
Room Command Dispatcher
        │
    ┌───┼────┐
    ▼   ▼    ▼
Room A Room B Room C   (concurrent across rooms)
```

Within one room, commands (JOIN, LEAVE, OFFER, ANSWER, ICE_CANDIDATE, MUTE,
...) are processed through an ordered per-room queue so two participants
acting at the same instant can't race on the same room's state — the
tradeoff and its implementation (`ConcurrentHashMap` + per-room command
queue, no `new Thread()` per connection) will be documented here once built.
