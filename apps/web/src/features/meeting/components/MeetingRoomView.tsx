"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useLocalMedia } from "@/hooks/useLocalMedia";
import { VideoTile } from "@/components/VideoTile";
import { MediaToggleButton } from "@/components/MediaToggleButton";
import { MicOnIcon, MicOffIcon, CameraOnIcon, CameraOffIcon, PhoneHangupIcon } from "@/components/icons";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { useMeetingSocket } from "@/features/meeting/hooks/useMeetingSocket";
import { useWebRtcPeers } from "@/features/meeting/hooks/useWebRtcPeers";
import type { ParticipantPresencePayload, SignalingEnvelope } from "@/lib/websocket/types";

export function MeetingRoomView({ code }: { code: string }) {
  const { status: authStatus, user, token } = useAuth();
  const router = useRouter();
  const { stream, error: mediaError, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop: stopLocalMedia } = useLocalMedia();

  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [participants, setParticipants] = useState<Map<string, ParticipantPresencePayload>>(new Map());
  const [loadError, setLoadError] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    if (authStatus === "unauthenticated") {
      router.push(`/login?redirect=/meet/${code}/room`);
    }
  }, [authStatus, router, code]);

  useEffect(() => {
    if (authStatus !== "authenticated" || !token) return;
    let cancelled = false;

    meetingApi
      .getMeetingByCode(token, code)
      .then((m) => {
        if (!cancelled) setMeeting(m);
      })
      .catch(() => {
        if (!cancelled) setLoadError("This meeting could not be found.");
      });

    return () => {
      cancelled = true;
    };
  }, [authStatus, token, code]);

  // Seed the roster from REST (source of truth for "who's here right now"
  // at page-load time) — the WebSocket only carries deltas from that point
  // forward, it has no way to describe the room's starting state on its own.
  useEffect(() => {
    if (!meeting || !token) return;
    let cancelled = false;

    meetingApi.listParticipants(token, meeting.id).then((list) => {
      if (cancelled) return;
      setParticipants(
        new Map(list.filter((p) => p.userId).map((p) => [p.userId as string, {
          userId: p.userId as string,
          displayName: p.displayName,
          role: p.role,
        }])),
      );
    });

    return () => {
      cancelled = true;
    };
  }, [meeting, token]);

  const peerIds = useMemo(
    () => Array.from(participants.keys()).filter((id) => id !== user?.id),
    [participants, user?.id],
  );

  const { send, status: socketStatus } = useMeetingSocket(meeting?.id ?? null, token, (envelope) =>
    handleSignalingMessage(envelope),
  );

  const { remoteStreams, handleEnvelope: handleWebRtcEnvelope } = useWebRtcPeers(
    user?.id ?? null,
    stream,
    peerIds,
    send,
  );

  function handleSignalingMessage(envelope: SignalingEnvelope) {
    if (envelope.type === "PARTICIPANT_JOINED" && envelope.payload) {
      const presence = envelope.payload as ParticipantPresencePayload;
      setParticipants((prev) => new Map(prev).set(presence.userId, presence));
    } else if (envelope.type === "PARTICIPANT_LEFT" && envelope.payload) {
      const presence = envelope.payload as ParticipantPresencePayload;
      setParticipants((prev) => {
        const next = new Map(prev);
        next.delete(presence.userId);
        return next;
      });
    } else if (envelope.type === "WEBRTC_OFFER" || envelope.type === "WEBRTC_ANSWER" || envelope.type === "ICE_CANDIDATE") {
      handleWebRtcEnvelope(envelope);
    }
  }

  const handleLeave = useCallback(async () => {
    if (!meeting || !token) return;
    setLeaving(true);
    stopLocalMedia();
    try {
      await meetingApi.leaveMeeting(token, meeting.id);
    } finally {
      router.push("/");
    }
  }, [meeting, token, router, stopLocalMedia]);

  const handleEnd = useCallback(async () => {
    if (!meeting || !token) return;
    setLeaving(true);
    stopLocalMedia();
    try {
      await meetingApi.endMeeting(token, meeting.id);
    } finally {
      router.push("/");
    }
  }, [meeting, token, router, stopLocalMedia]);

  if (authStatus !== "authenticated" || (!meeting && !loadError)) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-muted">Loading…</p>
      </main>
    );
  }

  if (loadError || !meeting) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">{loadError}</h1>
      </main>
    );
  }

  const isHost = user?.id === meeting.hostId;
  const remoteParticipants = Array.from(participants.values()).filter((p) => p.userId !== user?.id);

  return (
    <main className="flex min-h-screen flex-col gap-6 px-6 py-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold tracking-tight">{meeting.title}</h1>
        <div className="flex items-center gap-2 text-sm text-muted">
          <span>Code: {meeting.code}</span>
          <span aria-hidden="true">·</span>
          <span className="inline-flex items-center gap-1.5">
            <span
              className={`h-1.5 w-1.5 rounded-full ${
                socketStatus === "open" ? "bg-green-500" : socketStatus === "connecting" ? "bg-yellow-400" : "bg-red-500"
              }`}
            />
            {socketStatus === "open" ? "Live" : socketStatus === "connecting" ? "Connecting…" : "Disconnected"}
          </span>
        </div>
      </header>

      {mediaError && <p className="text-sm text-danger">{mediaError}</p>}

      <section className="grid flex-1 auto-rows-fr grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <VideoTile
          stream={stream}
          active={cameraEnabled}
          displayName={user?.displayName ?? "You"}
          mirrored
          muted
          label={`${user?.displayName ?? "You"} (you)`}
        />
        {remoteParticipants.map((p) => (
          <VideoTile
            key={p.userId}
            stream={remoteStreams.get(p.userId) ?? null}
            displayName={p.displayName}
            label={p.displayName}
          />
        ))}
      </section>

      <div className="flex justify-center gap-3 pt-2">
        <MediaToggleButton
          enabled={micEnabled}
          onClick={toggleMic}
          label={micEnabled ? "Mute microphone" : "Unmute microphone"}
          enabledIcon={MicOnIcon}
          disabledIcon={MicOffIcon}
        />
        <MediaToggleButton
          enabled={cameraEnabled}
          onClick={toggleCamera}
          label={cameraEnabled ? "Turn off camera" : "Turn on camera"}
          enabledIcon={CameraOnIcon}
          disabledIcon={CameraOffIcon}
        />
        <button
          onClick={handleLeave}
          disabled={leaving}
          aria-label="Leave meeting"
          title="Leave meeting"
          className="flex h-11 w-11 items-center justify-center rounded-full bg-danger text-white transition-opacity hover:opacity-90 disabled:opacity-60"
        >
          {PhoneHangupIcon}
        </button>
        {isHost && (
          <button
            onClick={handleEnd}
            disabled={leaving}
            className="rounded-full border border-danger-border bg-danger-bg px-4 text-sm font-medium text-danger transition-opacity hover:opacity-90 disabled:opacity-60"
          >
            End for everyone
          </button>
        )}
      </div>
    </main>
  );
}
