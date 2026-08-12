"use client";

import { useEffect, useRef, useState } from "react";
import type { PeerConnectionManager } from "@/lib/webrtc/PeerConnectionManager";

export interface WebRtcPeerStats {
  rttMs: number | null;
  jitterMs: number | null;
  packetLossPercent: number | null;
  bitrateKbps: number | null;
  candidateType: string | null;
  protocol: string | null;
  codec: string | null;
  framesDropped: number | null;
  connectionState: string | null;
  sampledAt: number;
}

type PreviousSample = { bytes: number; timestamp: number };

function rounded(value: number, digits = 1) {
  const factor = 10 ** digits;
  return Math.round(value * factor) / factor;
}

function parseReport(report: RTCStatsReport, previous?: PreviousSample): { stats: WebRtcPeerStats; sample: PreviousSample } {
  let inboundBytes = 0;
  let packetsLost = 0;
  let packetsReceived = 0;
  let jitterSeconds: number | null = null;
  let framesDropped = 0;
  let codecId: string | null = null;
  let selectedPair: RTCStats | null = null;
  let newestTimestamp = Date.now();

  report.forEach((item) => {
    newestTimestamp = Math.max(newestTimestamp, item.timestamp ?? 0);
    if (item.type === "inbound-rtp" && !item.isRemote) {
      inboundBytes += item.bytesReceived ?? 0;
      packetsLost += item.packetsLost ?? 0;
      packetsReceived += item.packetsReceived ?? 0;
      if (typeof item.jitter === "number") jitterSeconds = Math.max(jitterSeconds ?? 0, item.jitter);
      framesDropped += item.framesDropped ?? 0;
      codecId ??= item.codecId ?? null;
    }
    if (item.type === "candidate-pair" && (item.nominated || item.selected) && item.state === "succeeded") {
      selectedPair = item;
    }
  });

  const pair = selectedPair as (RTCStats & { currentRoundTripTime?: number; localCandidateId?: string; state?: string }) | null;
  const localCandidate = pair?.localCandidateId ? report.get(pair.localCandidateId) : null;
  const codec = codecId ? report.get(codecId) : null;
  const totalPackets = packetsReceived + Math.max(0, packetsLost);
  const elapsedMs = previous ? newestTimestamp - previous.timestamp : 0;
  const bitrate = previous && elapsedMs > 0
    ? Math.max(0, (inboundBytes - previous.bytes) * 8 / elapsedMs)
    : null;

  return {
    sample: { bytes: inboundBytes, timestamp: newestTimestamp },
    stats: {
      rttMs: typeof pair?.currentRoundTripTime === "number" ? rounded(pair.currentRoundTripTime * 1000) : null,
      jitterMs: jitterSeconds === null ? null : rounded(jitterSeconds * 1000),
      packetLossPercent: totalPackets > 0 ? rounded(Math.max(0, packetsLost) / totalPackets * 100, 2) : null,
      bitrateKbps: bitrate === null ? null : rounded(bitrate),
      candidateType: localCandidate?.candidateType ?? null,
      protocol: localCandidate?.protocol ?? null,
      codec: codec?.mimeType?.replace(/^audio\//, "").replace(/^video\//, "") ?? null,
      framesDropped: framesDropped || null,
      connectionState: pair?.state ?? null,
      sampledAt: Date.now(),
    },
  };
}

export function useWebRtcStats(manager: PeerConnectionManager | null, enabled: boolean, intervalMs = 2000) {
  const [stats, setStats] = useState<Map<string, WebRtcPeerStats>>(new Map());
  const previousRef = useRef<Map<string, PreviousSample>>(new Map());

  useEffect(() => {
    if (!manager || !enabled) {
      previousRef.current.clear();
      return;
    }
    let cancelled = false;

    async function poll() {
      if (!manager) return;
      try {
        const reports = await manager.getStats();
        if (cancelled) return;
        const next = new Map<string, WebRtcPeerStats>();
        const nextPrevious = new Map<string, PreviousSample>();
        reports.forEach((report, participantId) => {
          const parsed = parseReport(report, previousRef.current.get(participantId));
          next.set(participantId, parsed.stats);
          nextPrevious.set(participantId, parsed.sample);
        });
        previousRef.current = nextPrevious;
        setStats(next);
      } catch {
        // A peer can close while getStats() is in flight; the next poll retries.
      }
    }

    void poll();
    const timer = window.setInterval(poll, intervalMs);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [manager, enabled, intervalMs]);

  return stats;
}
