"use client";

import { useEffect, useRef, useState } from "react";
import { SignalingClient, type ConnectionState } from "@/lib/websocket/signalingClient";
import type { SignalingEnvelope } from "@/lib/websocket/types";

/**
 * Opens the meeting's signaling connection and hands every incoming
 * envelope to `onMessage`. Deliberately holds no participant state itself —
 * the caller decides what to do with each event, so this hook stays
 * reusable once WEBRTC_OFFER/ANSWER/ICE_CANDIDATE routing lands (Milestone 4).
 */
export function useMeetingSocket(
  meetingId: string | null,
  token: string | null,
  onMessage: (envelope: SignalingEnvelope) => void,
) {
  const [status, setStatus] = useState<ConnectionState>("connecting");
  const clientRef = useRef<SignalingClient | null>(null);
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!meetingId || !token) return;

    const client = new SignalingClient(meetingId, token, {
      onStateChange: setStatus,
      onMessage: (envelope) => onMessageRef.current(envelope),
    });
    clientRef.current = client;
    client.connect();

    return () => {
      client.close();
      clientRef.current = null;
    };
  }, [meetingId, token]);

  return { status, send: (message: Parameters<SignalingClient["send"]>[0]) => clientRef.current?.send(message) };
}
