"use client";

import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import * as livestreamApi from "@/features/livestream/api/livestreamApi";

const POLL_INTERVAL_MS = 4000;

/**
 * Public livestream viewer — no camera/mic, no auth, no account of any
 * kind. Polls livestream status by its public code until it goes LIVE,
 * then plays the HLS output FFmpeg is writing (see
 * docs/networking/livestream.md). Native `<video>` handles HLS directly on
 * Safari; everywhere else this uses hls.js, since HLS support isn't
 * built into most browsers' media engines.
 */
export function LivestreamViewer({ code }: { code: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [hlsUrl, setHlsUrl] = useState<string | null>(null);
  const [title, setTitle] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const state = await livestreamApi.getLivestreamStatus(code);
        if (cancelled) return;
        if (state.status === "LIVE" && state.hlsUrl) {
          setHlsUrl(livestreamApi.resolveHlsUrl(state.hlsUrl));
          setTitle(state.title);
          setOffline(false);
        } else {
          setHlsUrl(null);
          setOffline(true);
        }
      } catch {
        if (!cancelled) setOffline(true);
      }
    }

    poll();
    const timer = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [code]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !hlsUrl) return;

    if (video.canPlayType("application/vnd.apple.mpegurl")) {
      video.src = hlsUrl;
      return;
    }
    if (Hls.isSupported()) {
      const hls = new Hls();
      hls.loadSource(hlsUrl);
      hls.attachMedia(video);
      return () => hls.destroy();
    }
  }, [hlsUrl]);

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-black px-4 py-8">
      {hlsUrl ? (
        <div className="flex w-full max-w-4xl flex-col gap-2">
          <video ref={videoRef} controls autoPlay playsInline className="aspect-video w-full rounded-xl bg-black" />
          {title && <p className="text-sm font-medium text-white/80">{title}</p>}
        </div>
      ) : (
        <div className="flex aspect-video w-full max-w-4xl flex-col items-center justify-center gap-2 rounded-xl bg-neutral-900 text-center text-white">
          <p className="text-lg font-medium">{offline ? "Hiện không có ai đang phát trực tiếp" : "Đang tải…"}</p>
          <p className="text-sm text-white/60">Trang sẽ tự động phát ngay khi buổi phát bắt đầu.</p>
        </div>
      )}
    </main>
  );
}
