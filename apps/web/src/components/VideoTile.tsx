"use client";

import { useEffect, useRef } from "react";

export function VideoTile({
  stream,
  active = true,
  displayName,
  mirrored = false,
  muted = false,
  label,
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
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-black/90">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted={muted}
        className={`h-full w-full object-cover ${mirrored ? "-scale-x-100" : ""} ${showVideo ? "" : "hidden"}`}
      />
      {!showVideo && (
        <div className="flex h-full w-full items-center justify-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-2xl font-semibold text-accent-foreground">
            {initial}
          </div>
        </div>
      )}
      {label && (
        <span className="absolute bottom-2 left-2 rounded bg-black/60 px-2 py-0.5 text-xs text-white">{label}</span>
      )}
    </div>
  );
}
