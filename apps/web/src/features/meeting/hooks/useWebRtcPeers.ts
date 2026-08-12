"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { PeerConnectionManager } from "@/lib/webrtc/PeerConnectionManager";
import { fetchIceServers } from "@/lib/webrtc/iceServers";
import type { SignalingEnvelope, SignalingMessageType } from "@/lib/websocket/types";

type SendFn = (message: { type: SignalingMessageType; targetId?: string; payload?: unknown }) => void;

/**
 * Owns the PeerConnectionManager for the room and reconciles it against who
 * is actually present: whenever `peerIds` changes, connects to anyone new
 * and tears down anyone who left. Offer/answer/ICE routing is
 * event-driven (via handleEnvelope); who's-in-the-room is state-driven (via
 * the peerIds prop) — mixing those two models for the same data would be
 * more error-prone than keeping them separate.
 *
 * Glare avoidance: if both sides of a pair tried to initiate a connection
 * simultaneously, you'd get two competing offers. Instead, whichever
 * participant has the lexicographically smaller userId is always the one
 * who initiates — deterministic, requires no extra coordination message,
 * and every peer computes the same answer independently.
 */
export function useWebRtcPeers(
  myUserId: string | null,
  localStream: MediaStream | null,
  peerIds: string[],
  send: SendFn,
  token: string | null,
) {
  const managerRef = useRef<PeerConnectionManager | null>(null);
  const [remoteStreams, setRemoteStreams] = useState<Map<string, MediaStream>>(new Map());
  const [remoteScreenStreams, setRemoteScreenStreams] = useState<Map<string, MediaStream>>(new Map());
  const [isScreenSharing, setIsScreenSharing] = useState(false);
  // ICE server fetch is async, so manager creation can't just be a synchronous
  // ref assignment the reconcile effect below picks up for free — setting a
  // ref doesn't trigger a re-render, so without this the reconcile effect
  // would only run reconciliation for peerIds changes that happen to occur
  // *after* the fetch resolves, missing anyone already in peerIds at that point.
  const [managerReady, setManagerReady] = useState(false);

  useEffect(() => {
    if (!localStream || !token) return;
    let cancelled = false;
    setManagerReady(false);

    fetchIceServers(token).then((iceServers) => {
      if (cancelled) return;

      const manager = new PeerConnectionManager({
        iceServers,
        localStream,
        onRemoteStream: (participantId, stream) => {
          setRemoteStreams((prev) => new Map(prev).set(participantId, stream));
        },
        onRemoteScreenStream: (participantId, stream) => {
          setRemoteScreenStreams((prev) => {
            const next = new Map(prev);
            if (stream) next.set(participantId, stream);
            else next.delete(participantId);
            return next;
          });
        },
        onIceCandidate: (participantId, candidate) => {
          send({ type: "ICE_CANDIDATE", targetId: participantId, payload: candidate });
        },
        onOffer: (participantId, offer) => {
          send({ type: "WEBRTC_OFFER", targetId: participantId, payload: offer });
        },
      });
      managerRef.current = manager;
      setManagerReady(true);
    });

    return () => {
      cancelled = true;
      managerRef.current?.closeAll();
      managerRef.current = null;
      setManagerReady(false);
      setRemoteStreams(new Map());
      setRemoteScreenStreams(new Map());
      setIsScreenSharing(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [localStream, token]);

  useEffect(() => {
    const manager = managerRef.current;
    if (!manager || !myUserId) return;

    for (const peerId of peerIds) {
      if (peerId === myUserId || manager.hasPeer(peerId)) continue;
      if (myUserId < peerId) {
        manager.connect(peerId);
      }
      // else: wait for their offer — see glare-avoidance note above.
    }

    for (const connectedId of manager.connectedPeerIds()) {
      if (!peerIds.includes(connectedId)) {
        manager.removePeer(connectedId);
        setRemoteStreams((prev) => {
          const next = new Map(prev);
          next.delete(connectedId);
          return next;
        });
        setRemoteScreenStreams((prev) => {
          const next = new Map(prev);
          next.delete(connectedId);
          return next;
        });
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [peerIds, myUserId, localStream, managerReady]);

  const handleEnvelope = useCallback(
    (envelope: SignalingEnvelope) => {
      const manager = managerRef.current;
      const senderId = envelope.senderId;
      if (!manager || !senderId) return;

      switch (envelope.type) {
        case "WEBRTC_OFFER":
          manager.handleOffer(senderId, envelope.payload as RTCSessionDescriptionInit).then((answer) => {
            send({ type: "WEBRTC_ANSWER", targetId: senderId, payload: answer });
          });
          break;
        case "WEBRTC_ANSWER":
          manager.handleAnswer(senderId, envelope.payload as RTCSessionDescriptionInit);
          break;
        case "ICE_CANDIDATE":
          manager.addIceCandidate(senderId, envelope.payload as RTCIceCandidateInit);
          break;
        case "PARTICIPANT_RECONNECTED":
          // The signaling layer resumed seamlessly (no LEFT/JOINED), but
          // the underlying network path for this peer's media may still
          // have changed — nudge an ICE restart just in case the
          // connection didn't already recover on its own.
          if (manager.hasPeer(senderId)) {
            void manager.restartIce(senderId);
          }
          break;
      }
    },
    [send],
  );

  const startScreenShare = useCallback(async (stream: MediaStream) => {
    await managerRef.current?.startScreenShare(stream);
    setIsScreenSharing(true);
  }, []);

  const stopScreenShare = useCallback(() => {
    managerRef.current?.stopScreenShare();
    setIsScreenSharing(false);
  }, []);

  return { remoteStreams, remoteScreenStreams, handleEnvelope, startScreenShare, stopScreenShare, isScreenSharing };
}
