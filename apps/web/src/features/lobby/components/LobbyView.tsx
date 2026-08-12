"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useLocalMedia } from "@/features/lobby/hooks/useLocalMedia";
import { VideoPreview } from "@/features/lobby/components/VideoPreview";
import { MediaToggleButton } from "@/features/lobby/components/MediaToggleButton";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { ApiClientError } from "@/lib/api/client";

const MicOnIcon = (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="2" width="6" height="12" rx="3" />
    <path d="M5 10a7 7 0 0014 0M12 19v3" />
  </svg>
);
const MicOffIcon = (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="2" y1="2" x2="22" y2="22" />
    <path d="M9 9v3a3 3 0 004.24 2.74M15 9.34V5a3 3 0 00-5.94-.6" />
    <path d="M5 10a7 7 0 0010.5 6.06M19 10a7 7 0 01-.34 2.16M12 19v3" />
  </svg>
);
const CameraOnIcon = (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M15 10l4.55-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-1.45.894L15 14" />
    <rect x="3" y="6" width="12" height="12" rx="2" />
  </svg>
);
const CameraOffIcon = (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <line x1="2" y1="2" x2="22" y2="22" />
    <path d="M15 10l4.55-2.276A1 1 0 0121 8.618v6.764a1 1 0 01-.324.738M3 6h9a2 2 0 012 2v6.34M11.5 18H5a2 2 0 01-2-2V8c0-.32.076-.622.211-.89" />
  </svg>
);

type LoadState = "loading" | "ready" | "not_found" | "ended";

export function LobbyView({ code }: { code: string }) {
  const { status: authStatus, user, token } = useAuth();
  const router = useRouter();
  const { status: mediaStatus, error: mediaError, stream, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop } = useLocalMedia();

  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [displayName, setDisplayName] = useState("");
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  useEffect(() => {
    if (authStatus === "unauthenticated") {
      router.push(`/login?redirect=/meet/${code}`);
    }
  }, [authStatus, router, code]);

  useEffect(() => {
    if (user) setDisplayName(user.displayName);
  }, [user]);

  useEffect(() => {
    if (authStatus !== "authenticated" || !token) return;

    meetingApi
      .getMeetingByCode(token, code)
      .then((m) => {
        setMeeting(m);
        setLoadState(m.status === "ENDED" ? "ended" : "ready");
      })
      .catch(() => setLoadState("not_found"));
  }, [authStatus, token, code]);

  async function handleJoin() {
    if (!meeting || !token) return;
    setJoining(true);
    setJoinError(null);
    try {
      await meetingApi.joinMeeting(token, meeting.id);
      stop();
      router.push(`/meet/${code}/room`);
    } catch (err) {
      setJoinError(err instanceof ApiClientError ? err.message : "Could not join meeting");
      setJoining(false);
    }
  }

  if (authStatus !== "authenticated" || loadState === "loading") {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-muted">Loading…</p>
      </main>
    );
  }

  if (loadState === "not_found") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">Meeting not found</h1>
        <p className="text-sm text-muted">No meeting exists for the code &ldquo;{code}&rdquo;.</p>
        <Link href="/" className="text-sm font-medium text-accent hover:underline">
          Back to home
        </Link>
      </main>
    );
  }

  if (loadState === "ended") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">This meeting has ended</h1>
        <Link href="/" className="text-sm font-medium text-accent hover:underline">
          Back to home
        </Link>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 px-6 py-12">
      <div className="flex w-full max-w-3xl flex-col items-center gap-2 text-center">
        <h1 className="text-xl font-semibold tracking-tight">{meeting?.title}</h1>
        <p className="text-sm text-muted">Ready to join?</p>
      </div>

      <div className="grid w-full max-w-3xl gap-8 md:grid-cols-2 md:items-center">
        <div className="flex flex-col gap-3">
          <VideoPreview stream={stream} cameraEnabled={cameraEnabled} displayName={displayName} />
          {mediaStatus === "error" && (
            <p className="text-xs text-danger">{mediaError ?? "Camera/microphone unavailable"}</p>
          )}
          <div className="flex justify-center gap-3">
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
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="displayName" className="text-sm font-medium">
              Your name
            </label>
            <input
              id="displayName"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
            />
          </div>
          {joinError && <p className="text-sm text-danger">{joinError}</p>}
          <button
            onClick={handleJoin}
            disabled={joining}
            className="rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover disabled:opacity-60"
          >
            {joining ? "Joining…" : "Join now"}
          </button>
        </div>
      </div>
    </main>
  );
}
