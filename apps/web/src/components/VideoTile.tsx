"use client";

import { useEffect, useRef } from "react";

export function VideoTile({
  stream,
  active = true,
  displayName,
  mirrored = false,
  muted = false,
  label,
  fill = false,
  fit = "cover",
  children,
}: {
  stream: MediaStream | null;
  /** Whether the video track should actually be shown (vs. an avatar placeholder — camera off, or no stream yet). */
  active?: boolean;
  displayName: string;
  /** Self-view convention — never applied to remote peers, see VideoPreview usage in the lobby. */
  mirrored?: boolean;
  /** Local self-view must stay muted to avoid echoing your own mic back to you; remote tiles should not be. */
  muted?: boolean;
  label?: string;
  /** Fill the parent's box instead of forcing a 16:9 aspect ratio — used by the pinned/spotlight tile. */
  fill?: boolean;
  /** Keep the whole frame visible with letterboxing, or crop it to fill the tile. */
  fit?: "cover" | "contain";
  /** Overlay controls (pin, host actions) positioned absolutely by the caller. */
  children?: React.ReactNode;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.srcObject = stream;
    }
  }, [stream]);

  const initial = displayName.trim().charAt(0).toUpperCase() || "?";
  const showVideo = active && stream !== null;

  return (
    <div className={`group relative overflow-hidden rounded-xl bg-neutral-900 ${fill ? "h-full w-full" : "aspect-video w-full"}`}>
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={muted}
        className={`h-full w-full ${fit === "contain" ? "object-contain" : "object-cover"} ${mirrored ? "-scale-x-100" : ""} ${showVideo ? "" : "hidden"}`}
      />
      {!showVideo && (
        <div className="flex h-full w-full items-center justify-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-neutral-700 text-2xl font-semibold text-white">
            {initial}
          </div>
        </div>
      )}
      {label && (
        <span className="absolute bottom-2 left-2 rounded bg-black/60 px-2 py-0.5 text-xs text-white">{label}</span>
      )}
      {children}
    </div>
  );
}
