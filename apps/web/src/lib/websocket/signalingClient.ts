import { WS_URL } from "@/lib/api/config";
import type { SignalingEnvelope, SignalingMessageType } from "./types";

// Kept in sync with the backend's WS_HEARTBEAT_INTERVAL_SECONDS default
// (apps/server/.env.example) — not fetched dynamically, since the two only
// need to be in the same ballpark, not identical to the second.
const HEARTBEAT_INTERVAL_MS = 10_000;

export type ConnectionState = "connecting" | "open" | "closed";

export interface SignalingClientHandlers {
  onMessage: (envelope: SignalingEnvelope) => void;
  onStateChange: (state: ConnectionState) => void;
}

/**
 * Thin wrapper around the native WebSocket API for one meeting's signaling
 * connection. Deliberately does not attempt to reconnect on drop — that's
 * Milestone 8 (reconnect + session resume). For now a dropped connection
 * just reports "closed" and stays closed.
 */
export class SignalingClient {
  private ws: WebSocket | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;

  constructor(
    private readonly meetingId: string,
    private readonly token: string,
    private readonly handlers: SignalingClientHandlers,
  ) {}

  connect(): void {
    this.handlers.onStateChange("connecting");

    const url = `${WS_URL}/ws/meetings/${this.meetingId}?token=${encodeURIComponent(this.token)}`;
    const ws = new WebSocket(url);
    this.ws = ws;

    ws.onopen = () => {
      this.handlers.onStateChange("open");
      this.startHeartbeat();
    };

    ws.onmessage = (event: MessageEvent<string>) => {
      try {
        this.handlers.onMessage(JSON.parse(event.data) as SignalingEnvelope);
      } catch {
        // malformed frame — ignore rather than crash the connection
      }
    };

    ws.onclose = () => {
      this.stopHeartbeat();
      this.handlers.onStateChange("closed");
    };
  }

  send(message: { type: SignalingMessageType; requestId?: string; targetId?: string; payload?: unknown }): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;

    const envelope: SignalingEnvelope = {
      type: message.type,
      requestId: message.requestId ?? null,
      // meetingId/senderId are always stamped server-side from the
      // authenticated session — sending them from the client would be
      // pointless, the server ignores/overwrites them anyway.
      meetingId: null,
      senderId: null,
      targetId: message.targetId ?? null,
      timestamp: Date.now(),
      payload: message.payload ?? null,
    };
    this.ws.send(JSON.stringify(envelope));
  }

  close(): void {
    this.stopHeartbeat();
    this.ws?.close(1000, "Client closed");
    this.ws = null;
  }

  private startHeartbeat(): void {
    this.heartbeatTimer = setInterval(() => this.send({ type: "PING" }), HEARTBEAT_INTERVAL_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }
}
