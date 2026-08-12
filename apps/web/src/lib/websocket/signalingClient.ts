import { WS_URL } from "@/lib/api/config";
import type { SignalingEnvelope, SignalingMessageType } from "./types";

// Kept in sync with the backend's WS_HEARTBEAT_INTERVAL_SECONDS default
// (apps/server/.env.example) — not fetched dynamically, since the two only
// need to be in the same ballpark, not identical to the second.
const HEARTBEAT_INTERVAL_MS = 10_000;

// Exponential backoff for reconnect attempts, capped at 10s — the backend's
// grace period (WS_RECONNECT_GRACE_SECONDS, default 15s) is what actually
// determines whether a reconnect resumes the same roster slot or looks like
// a fresh join, so retrying faster than that comfortably covers a typical
// wifi blip or tab suspend/resume.
const RECONNECT_BASE_DELAY_MS = 1_000;
const RECONNECT_MAX_DELAY_MS = 10_000;

export type ConnectionState = "connecting" | "open" | "reconnecting" | "closed";

export interface SignalingClientHandlers {
  onMessage: (envelope: SignalingEnvelope) => void;
  onStateChange: (state: ConnectionState) => void;
}

/**
 * Thin wrapper around the native WebSocket API for one meeting's signaling
 * connection. Automatically retries with exponential backoff after any
 * unexpected drop (Milestone 8) — "closed" only ever means the caller
 * explicitly called close(), never a transient network blip.
 */
export class SignalingClient {
  private ws: WebSocket | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempts = 0;
  private manualClose = false;

  constructor(
    private readonly meetingId: string,
    private readonly token: string,
    private readonly handlers: SignalingClientHandlers,
  ) {}

  connect(): void {
    this.manualClose = false;
    this.handlers.onStateChange(this.reconnectAttempts > 0 ? "reconnecting" : "connecting");

    const url = `${WS_URL}/ws/meetings/${this.meetingId}?token=${encodeURIComponent(this.token)}`;
    const ws = new WebSocket(url);
    this.ws = ws;

    ws.onopen = () => {
      this.reconnectAttempts = 0;
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
      if (this.manualClose) {
        this.handlers.onStateChange("closed");
        return;
      }
      this.scheduleReconnect();
    };
  }

  private scheduleReconnect(): void {
    this.handlers.onStateChange("reconnecting");
    const delay = Math.min(RECONNECT_BASE_DELAY_MS * 2 ** this.reconnectAttempts, RECONNECT_MAX_DELAY_MS);
    this.reconnectAttempts += 1;
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
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
    this.manualClose = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
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
