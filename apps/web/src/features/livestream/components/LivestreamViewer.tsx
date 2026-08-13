"use client";

import { useEffect, useRef, useState } from "react";
import Hls from "hls.js";
import * as livestreamApi from "@/features/livestream/api/livestreamApi";

const POLL_INTERVAL_MS = 4000;

/**
 * Public livestream viewer — no camera/mic, no auth, not a meeting
 * participant. Polls livestream status by join code until it goes LIVE,
 * then plays the HLS output FFmpeg is writing (see
 * docs/networking/livestream.md). Native `<video>` handles HLS directly on
 * Safari; everywhere else this uses hls.js, since HLS support isn't
 * built into most browsers' media engines.
 */
export function LivestreamViewer({ code }: { code: string }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [hlsUrl, setHlsUrl] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const state = await livestreamApi.getLivestreamStatusByCode(code);
        if (cancelled) return;
        if (state.status === "LIVE" && state.hlsUrl) {
          setHlsUrl(livestreamApi.resolveHlsUrl(state.hlsUrl));
        } else {
          setHlsUrl(null);
        }
      } catch {
        if (!cancelled) setNotFound(true);
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

  if (notFound) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-2 px-6 text-center">
        <h1 className="text-xl font-semibold">Không tìm thấy cuộc họp này</h1>
      </main>
    );
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4 bg-black px-4 py-8">
      {hlsUrl ? (
        <video ref={videoRef} controls autoPlay playsInline className="aspect-video w-full max-w-4xl rounded-xl bg-black" />
      ) : (
        <div className="flex aspect-video w-full max-w-4xl flex-col items-center justify-center gap-2 rounded-xl bg-neutral-900 text-center text-white">
          <p className="text-lg font-medium">Chưa có livestream</p>
          <p className="text-sm text-white/60">Trang sẽ tự động phát khi chủ phòng bắt đầu livestream.</p>
        </div>
      )}
    </main>
  );
}
