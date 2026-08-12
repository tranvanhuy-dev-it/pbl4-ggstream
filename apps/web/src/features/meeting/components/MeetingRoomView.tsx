"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/context/AuthContext";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting, Participant } from "@/features/meeting/api/meetingApi";

const PARTICIPANTS_POLL_INTERVAL_MS = 4000;

export function MeetingRoomView({ code }: { code: string }) {
  const { status: authStatus, user, token } = useAuth();
  const router = useRouter();

  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [participants, setParticipants] = useState<Participant[]>([]);
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

  useEffect(() => {
    if (!meeting || !token) return;
    let cancelled = false;

    // Polling placeholder — replaced by realtime PARTICIPANT_JOINED /
    // PARTICIPANT_LEFT WebSocket events once signaling lands (Milestone 3).
    async function refresh() {
      try {
        const list = await meetingApi.listParticipants(token!, meeting!.id);
        if (!cancelled) setParticipants(list);
      } catch {
        // transient — next poll will retry
      }
    }

    refresh();
    const interval = setInterval(refresh, PARTICIPANTS_POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [meeting, token]);

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

  return (
    <main className="flex min-h-screen flex-col gap-8 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="text-xl font-semibold tracking-tight">{meeting.title}</h1>
        <p className="text-sm text-muted">Code: {meeting.code}</p>
      </header>

      <section className="flex flex-col gap-3">
        <h2 className="text-sm font-medium text-muted">Participants ({participants.length})</h2>
        <ul className="flex flex-col gap-2">
          {participants.map((p) => (
            <li
              key={p.id}
              className="flex items-center justify-between rounded-lg border border-card-border bg-card px-4 py-3"
            >
              <span className="text-sm font-medium">{p.displayName}</span>
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
