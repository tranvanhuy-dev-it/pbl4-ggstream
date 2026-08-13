"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useLocalMedia } from "@/hooks/useLocalMedia";
import { VideoTile } from "@/components/VideoTile";
import { MicOnIcon, MicOffIcon, CameraOnIcon, CameraOffIcon, LivestreamIcon, PhoneHangupIcon } from "@/components/icons";
import * as livestreamApi from "@/features/livestream/api/livestreamApi";
import type { LivestreamState } from "@/features/livestream/api/livestreamApi";
import { useLivestreamBroadcaster } from "@/features/livestream/hooks/useLivestreamBroadcaster";
import { ApiClientError } from "@/lib/api/client";

/**
 * Standalone "go live" studio — a broadcast stage, not a meeting screen.
 * Livestream is its own feature: going live never creates, joins, or
 * touches a meeting in any way — see docs/networking/livestream.md. Full-
 * bleed camera preview with floating controls, closer to TikTok/YouTube
 * Live's own broadcaster view than to the meeting lobby/room.
 */
export function LiveBroadcastView() {
  const { status: authStatus, user, token } = useAuth();
  const { status: mediaStatus, error: mediaError, stream, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop: stopLocalMedia } = useLocalMedia();
  const broadcaster = useLivestreamBroadcaster();

  const [title, setTitle] = useState("");
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [live, setLive] = useState<LivestreamState | null>(null);
  const [copied, setCopied] = useState(false);
  const liveRef = useRef<LivestreamState | null>(null);

  useEffect(() => {
    if (user) setTitle((current) => current || `${user.displayName} đang phát trực tiếp`);
  }, [user]);

  const handleStop = useCallback(async () => {
    broadcaster.stop();
    const current = liveRef.current;
    liveRef.current = null;
    setLive(null);
    if (current?.id && token) {
      try {
        await livestreamApi.stopLivestream(token, current.id);
      } catch {
        // best-effort — the browser side has already stopped either way
      }
    }
  }, [broadcaster, token]);

  // Best-effort cleanup if the broadcaster navigates away without
  // pressing "stop" — avoids leaving an FFmpeg process running forever.
  useEffect(() => {
    return () => {
      const current = liveRef.current;
      if (current?.id && token) {
        livestreamApi.stopLivestream(token, current.id).catch(() => undefined);
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
      const started = await livestreamApi.startLivestream(token, title.trim() || undefined);
      liveRef.current = started;
      setLive(started);
      if (started.id) broadcaster.start(started.id, token, stream);
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Không thể bắt đầu livestream");
    } finally {
      setStarting(false);
    }
  }, [broadcaster, stream, title, token]);

  const watchUrl = live && typeof window !== "undefined" ? `${window.location.origin}/watch/${live.code}` : null;

  const handleCopyLink = useCallback(() => {
    if (!watchUrl) return;
    navigator.clipboard?.writeText(watchUrl).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }, [watchUrl]);

  if (authStatus !== "authenticated") {
    return (
      <main className="flex min-h-screen items-center justify-center bg-black">
        <p className="text-sm text-white/60">Đang tải…</p>
      </main>
    );
  }

  const isLive = broadcaster.isBroadcasting;

  return (
    <div className="relative h-dvh w-full overflow-hidden bg-black">
      {/* Full-bleed camera preview — the stage itself, not a card in a form. */}
      <VideoTile stream={stream} active={cameraEnabled} displayName={user?.displayName ?? "Bạn"} mirrored muted fill />

      {/* Top bar: back link + LIVE badge, floating over the video. */}
      <div className="absolute inset-x-0 top-0 flex items-center justify-between bg-gradient-to-b from-black/70 to-transparent px-4 py-4 sm:px-6">
        <Link href="/" className="flex h-9 w-9 items-center justify-center rounded-full bg-black/40 text-white hover:bg-black/60">
          ←
        </Link>
        <span className="flex items-center gap-1.5 text-sm font-semibold text-white/90">
          <span className="text-accent">{LivestreamIcon}</span>
          Studio phát trực tiếp
        </span>
        {isLive ? (
          <span className="flex items-center gap-1.5 rounded-full bg-danger px-3 py-1 text-xs font-bold tracking-wide text-white">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-white" /> LIVE
          </span>
        ) : (
          <span className="w-9" />
        )}
      </div>

      {mediaStatus === "error" && (
        <div className="absolute left-1/2 top-20 -translate-x-1/2 rounded-lg bg-danger/90 px-4 py-2 text-sm text-white">
          {mediaError ?? "Không thể truy cập camera/micro"}
        </div>
      )}

      {/* Bottom overlay: pre-live setup card, or the live control bar. */}
      <div className="absolute inset-x-0 bottom-0 flex flex-col gap-4 bg-gradient-to-t from-black/85 via-black/50 to-transparent px-4 pb-6 pt-16 sm:px-8 sm:pb-8">
        {!isLive ? (
          <div className="mx-auto flex w-full max-w-md flex-col gap-3">
            <label htmlFor="live-title" className="text-xs font-medium text-white/70">
              Tiêu đề buổi phát
            </label>
            <input
              id="live-title"
              value={title}
              onChange={(event) => setTitle(event.target.value)}
              className="rounded-full border border-white/20 bg-white/10 px-4 py-2.5 text-sm text-white outline-none placeholder:text-white/40 focus:border-accent"
              placeholder="Đặt tên cho buổi phát của bạn"
            />
            {error && <p className="text-center text-sm text-danger">{error}</p>}
            <button
              onClick={handleStart}
              disabled={starting || !stream}
              className="flex items-center justify-center gap-2 rounded-full bg-gradient-to-r from-danger to-accent px-6 py-3 text-sm font-bold text-white shadow-lg transition-opacity hover:opacity-90 disabled:opacity-50"
            >
              <span className="h-2 w-2 rounded-full bg-white" />
              {starting ? "Đang bắt đầu…" : "Bắt đầu phát trực tiếp"}
            </button>
          </div>
        ) : (
          <div className="mx-auto flex w-full max-w-lg flex-col items-center gap-3">
            {watchUrl && (
              <button
                onClick={handleCopyLink}
                className="max-w-full truncate rounded-full bg-white/10 px-4 py-1.5 text-xs text-white/80 hover:bg-white/20"
                title={watchUrl}
              >
                {copied ? "Đã sao chép liên kết ✓" : `${watchUrl} · sao chép`}
              </button>
            )}
            {(error || broadcaster.error) && (
              <p className="text-center text-sm text-danger">{error ?? broadcaster.error}</p>
            )}
            <div className="flex items-center gap-4">
              <button
                onClick={toggleMic}
                aria-label={micEnabled ? "Tắt micro" : "Bật micro"}
                className={`flex h-12 w-12 items-center justify-center rounded-full transition-colors ${
                  micEnabled ? "bg-white/15 text-white hover:bg-white/25" : "bg-danger text-white"
                }`}
              >
                {micEnabled ? MicOnIcon : MicOffIcon}
              </button>
              <button
                onClick={toggleCamera}
                aria-label={cameraEnabled ? "Tắt camera" : "Bật camera"}
                className={`flex h-12 w-12 items-center justify-center rounded-full transition-colors ${
                  cameraEnabled ? "bg-white/15 text-white hover:bg-white/25" : "bg-danger text-white"
                }`}
              >
                {cameraEnabled ? CameraOnIcon : CameraOffIcon}
              </button>
              <button
                onClick={() => {
                  handleStop();
                  stopLocalMedia();
                }}
                aria-label="Dừng phát trực tiếp"
                title="Dừng phát trực tiếp"
                className="flex h-14 w-14 items-center justify-center rounded-full bg-danger text-white shadow-lg hover:opacity-90"
              >
                {PhoneHangupIcon}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
