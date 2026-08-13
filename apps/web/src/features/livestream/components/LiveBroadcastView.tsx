"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useLocalMedia } from "@/hooks/useLocalMedia";
import { VideoTile } from "@/components/VideoTile";
import { MediaToggleButton } from "@/components/MediaToggleButton";
import { MicOnIcon, MicOffIcon, CameraOnIcon, CameraOffIcon, LivestreamIcon } from "@/components/icons";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import * as livestreamApi from "@/features/livestream/api/livestreamApi";
import { useLivestreamBroadcaster } from "@/features/livestream/hooks/useLivestreamBroadcaster";
import { ApiClientError } from "@/lib/api/client";

/**
 * Standalone "go live" screen — no meeting to join, no participants,
 * TikTok/YouTube-Live-style. Backed by the same livestream pipeline as
 * LivestreamControls.tsx (in-meeting), just entered from a different
 * place: a hidden Meeting row is created purely as the channel identity
 * (unique code for the /watch/{code} link, host check reuse) and is never
 * shown anywhere in the meeting UI — see docs/networking/livestream.md.
 */
export function LiveBroadcastView() {
  const { status: authStatus, user, token } = useAuth();
  const { status: mediaStatus, error: mediaError, stream, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop: stopLocalMedia } = useLocalMedia();
  const broadcaster = useLivestreamBroadcaster();

  const [title, setTitle] = useState("");
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [meeting, setMeeting] = useState<meetingApi.Meeting | null>(null);
  const meetingRef = useRef<meetingApi.Meeting | null>(null);

  useEffect(() => {
    if (user) setTitle((current) => current || `${user.displayName} đang phát trực tiếp`);
  }, [user]);

  const handleStop = useCallback(async () => {
    broadcaster.stop();
    const live = meetingRef.current;
    meetingRef.current = null;
    setMeeting(null);
    if (live && token) {
      try {
        await livestreamApi.stopLivestream(token, live.id);
        await meetingApi.endMeeting(token, live.id);
      } catch {
        // best-effort — the browser side has already stopped either way
      }
    }
  }, [broadcaster, token]);

  // Best-effort cleanup if the broadcaster navigates away without
  // pressing "stop" — avoids leaving an FFmpeg process running forever.
  useEffect(() => {
    return () => {
      const live = meetingRef.current;
      if (live && token) {
        livestreamApi.stopLivestream(token, live.id).catch(() => undefined);
      }
    };
  }, [token]);

  const handleStart = useCallback(async () => {
    if (!token || !stream) {
      setError("Chưa có luồng camera/mic để livestream");
      return;
    }
    setStarting(true);
    setError(null);
    try {
      const created = await meetingApi.createMeeting(token, title.trim() || "Livestream");
      await livestreamApi.startLivestream(token, created.id);
      meetingRef.current = created;
      setMeeting(created);
      broadcaster.start(created.id, token, stream);
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Không thể bắt đầu livestream");
    } finally {
      setStarting(false);
    }
  }, [broadcaster, stream, title, token]);

  if (authStatus !== "authenticated") {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-muted">Đang tải…</p>
      </main>
    );
  }

  const watchUrl = meeting && typeof window !== "undefined" ? `${window.location.origin}/watch/${meeting.code}` : null;

  return (
    <div className="flex min-h-screen flex-col">
      <header className="flex h-16 flex-shrink-0 items-center justify-between border-b border-card-border px-4 lg:px-7">
        <Link href="/" className="text-sm font-medium text-muted hover:text-foreground">
          ← Về trang chủ
        </Link>
        <h1 className="flex items-center gap-2 text-sm font-semibold">
          <span className="text-accent">{LivestreamIcon}</span>
          Phát trực tiếp
        </h1>
        <span className="w-24" />
      </header>

      <main className="flex flex-1 flex-col items-center justify-center gap-8 px-6 py-8">
        <div className="grid w-full max-w-3xl gap-8 md:grid-cols-2 md:items-center">
          <div className="flex flex-col gap-3">
            <VideoTile stream={stream} active={cameraEnabled} displayName={user?.displayName ?? "Bạn"} mirrored muted />
            {mediaStatus === "error" && (
              <p className="text-xs text-danger">{mediaError ?? "Không thể truy cập camera/micro"}</p>
            )}
            <div className="flex justify-center gap-3">
              <MediaToggleButton
                enabled={micEnabled}
                onClick={toggleMic}
                label={micEnabled ? "Tắt micro" : "Bật micro"}
                enabledIcon={MicOnIcon}
                disabledIcon={MicOffIcon}
              />
              <MediaToggleButton
                enabled={cameraEnabled}
                onClick={toggleCamera}
                label={cameraEnabled ? "Tắt camera" : "Bật camera"}
                enabledIcon={CameraOnIcon}
                disabledIcon={CameraOffIcon}
              />
            </div>
          </div>

          <div className="flex flex-col gap-4">
            {!broadcaster.isBroadcasting ? (
              <>
                <div className="flex flex-col gap-1.5">
                  <label htmlFor="live-title" className="text-sm font-medium">
                    Tiêu đề buổi phát
                  </label>
                  <input
                    id="live-title"
                    value={title}
                    onChange={(event) => setTitle(event.target.value)}
                    className="rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
                  />
                </div>
                {(error || broadcaster.error) && <p className="text-sm text-danger">{error ?? broadcaster.error}</p>}
                <button
                  onClick={handleStart}
                  disabled={starting || !stream}
                  className="rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover disabled:opacity-60"
                >
                  {starting ? "Đang bắt đầu…" : "Bắt đầu phát trực tiếp"}
                </button>
              </>
            ) : (
              <>
                <div className="flex flex-col gap-1.5">
                  <span className="inline-flex w-fit items-center gap-1.5 rounded-full bg-danger/10 px-2.5 py-1 text-xs font-medium text-danger">
                    <span className="h-1.5 w-1.5 rounded-full bg-danger" /> ĐANG PHÁT TRỰC TIẾP
                  </span>
                  <p className="text-sm text-muted">Chia sẻ liên kết này để mọi người xem:</p>
                  {watchUrl && (
                    <a href={watchUrl} target="_blank" rel="noreferrer" className="break-all text-sm font-medium text-accent hover:underline">
                      {watchUrl}
                    </a>
                  )}
                </div>
                {(error || broadcaster.error) && <p className="text-sm text-danger">{error ?? broadcaster.error}</p>}
                <button
                  onClick={() => {
                    handleStop();
                    stopLocalMedia();
                  }}
                  className="rounded-lg border border-danger-border bg-danger-bg px-4 py-2.5 text-sm font-medium text-danger transition-colors hover:bg-danger hover:text-white"
                >
                  Dừng phát trực tiếp
                </button>
              </>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}
