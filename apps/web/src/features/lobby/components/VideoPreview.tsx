"use client";

import { useEffect, useRef } from "react";

export function VideoPreview({
  stream,
  cameraEnabled,
  displayName,
}: {
  stream: MediaStream | null;
  cameraEnabled: boolean;
  displayName: string;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    if (videoRef.current) {
      videoRef.current.srcObject = stream;
    }
  }, [stream]);

  const initial = displayName.trim().charAt(0).toUpperCase() || "?";

  return (
    <div className="relative aspect-video w-full overflow-hidden rounded-xl bg-black/90">
      <video
        ref={videoRef}
        autoPlay
        playsInline
        muted
        // Mirrored so the self-view feels like looking in a mirror (standard
        // for local preview in every video call app). The stream sent to
        // remote peers later is never mirrored — this is a CSS-only,
        // local-rendering concern.
        className={`h-full w-full origin-center -scale-x-100 object-cover ${cameraEnabled ? "" : "hidden"}`}
      />
      {!cameraEnabled && (
        <div className="flex h-full w-full items-center justify-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent text-2xl font-semibold text-accent-foreground">
            {initial}
          </div>
        </div>
      )}
    </div>
  );
}
