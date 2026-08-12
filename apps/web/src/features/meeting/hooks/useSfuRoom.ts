"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { LiveKitClient } from "@/lib/sfu/liveKitClient";
import type { SfuToken } from "@/features/meeting/api/meetingApi";

/**
 * SFU-mode counterpart to useWebRtcPeers.ts — same external shape
 * (remoteStreams, remoteScreenStreams, startScreenShare, stopScreenShare,
 * isScreenSharing) so MeetingRoomView can pick whichever hook's output to
 * render without any transport-specific JSX. Connects once to LiveKit via
 * `sfuToken` rather than negotiating a peer connection per participant.
 */
export function useSfuRoom(sfuToken: SfuToken | null, localStream: MediaStream | null) {
  const clientRef = useRef<LiveKitClient | null>(null);
  const [remoteStreams, setRemoteStreams] = useState<Map<string, MediaStream>>(new Map());
  const [remoteScreenStreams, setRemoteScreenStreams] = useState<Map<string, MediaStream>>(new Map());
  const [isScreenSharing, setIsScreenSharing] = useState(false);

  useEffect(() => {
    if (!sfuToken || !localStream) return;
    let cancelled = false;

    const client = new LiveKitClient({
      onRemoteStream: (participantId, stream) => {
        if (cancelled) return;
        setRemoteStreams((prev) => new Map(prev).set(participantId, stream));
      },
      onRemoteStreamRemoved: (participantId) => {
        if (cancelled) return;
        setRemoteStreams((prev) => {
          const next = new Map(prev);
          next.delete(participantId);
          return next;
        });
      },
      onRemoteScreenStream: (participantId, stream) => {
        if (cancelled) return;
        setRemoteScreenStreams((prev) => {
          const next = new Map(prev);
          if (stream) next.set(participantId, stream);
          else next.delete(participantId);
          return next;
        });
      },
    });
    clientRef.current = client;

    client.connect(sfuToken.url, sfuToken.token).then(() => {
      if (!cancelled) client.publishLocalStream(localStream);
    });

    return () => {
      cancelled = true;
      client.disconnect();
      clientRef.current = null;
      setRemoteStreams(new Map());
      setRemoteScreenStreams(new Map());
      setIsScreenSharing(false);
    };
  }, [sfuToken, localStream]);

  const startScreenShare = useCallback(async (stream: MediaStream) => {
    await clientRef.current?.startScreenShare(stream);
    setIsScreenSharing(true);
  }, []);

  const stopScreenShare = useCallback(() => {
    clientRef.current?.stopScreenShare();
    setIsScreenSharing(false);
  }, []);

  return { remoteStreams, remoteScreenStreams, startScreenShare, stopScreenShare, isScreenSharing };
}
