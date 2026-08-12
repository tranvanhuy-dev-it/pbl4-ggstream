"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/context/AuthContext";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { useMeetingSocket } from "@/features/meeting/hooks/useMeetingSocket";
import type { ParticipantPresencePayload, SignalingEnvelope } from "@/lib/websocket/types";

export function MeetingRoomView({ code }: { code: string }) {
  const { status: authStatus, user, token } = useAuth();
  const router = useRouter();

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
  // at page-load time) — the WebSocket below only carries deltas from that
  // point forward, it has no way to describe the room's starting state on
  // its own.
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

  const handleSignalingMessage = useCallback((envelope: SignalingEnvelope) => {
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
    }
  }, []);

  const { status: socketStatus } = useMeetingSocket(meeting?.id ?? null, token, handleSignalingMessage);

  const handleLeave = useCallback(async () => {
    if (!meeting || !token) return;
    setLeaving(true);
    try {
      await meetingApi.leaveMeeting(token, meeting.id);
    } finally {
      router.push("/");
    }
  }, [meeting, token, router]);

  const handleEnd = useCallback(async () => {
    if (!meeting || !token) return;
    setLeaving(true);
    try {
      await meetingApi.endMeeting(token, meeting.id);
    } finally {
      router.push("/");
    }
  }, [meeting, token, router]);

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
  const participantList = Array.from(participants.values());

  return (
    <main className="flex min-h-screen flex-col gap-8 px-6 py-10">
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

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-muted">Participants ({participantList.length})</h2>
        <ul className="flex flex-col gap-2">
          {participantList.map((p) => (
            <li
              key={p.userId}
              className="flex items-center justify-between rounded-lg border border-card-border bg-card px-4 py-3"
            >
              <span className="text-sm font-medium">
                {p.displayName}
                {p.userId === user?.id && <span className="text-muted"> (you)</span>}
              </span>
              <span className="text-xs uppercase tracking-wide text-muted">{p.role.replace("_", "-")}</span>
            </li>
          ))}
        </ul>
      </section>

      <div className="mt-auto flex justify-center gap-3 pt-6">
        <button
          onClick={handleLeave}
          disabled={leaving}
          className="rounded-lg border border-input-border px-4 py-2.5 text-sm font-medium transition-colors hover:bg-black/5 disabled:opacity-60 dark:hover:bg-white/10"
        >
          Leave meeting
        </button>
        {isHost && (
          <button
            onClick={handleEnd}
            disabled={leaving}
            className="rounded-lg bg-danger px-4 py-2.5 text-sm font-medium text-white transition-colors disabled:opacity-60"
          >
            End meeting for everyone
          </button>
        )}
      </div>
    </main>
  );
}
