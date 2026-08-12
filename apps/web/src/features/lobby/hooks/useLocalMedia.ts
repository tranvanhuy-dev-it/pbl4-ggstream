"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type MediaStatus = "idle" | "requesting_permission" | "ready" | "error";

/**
 * Local camera/microphone preview for the lobby. Deliberately has nothing
 * to do with WebRTC peer connections — those come in Milestone 4+, layered
 * on top of the same MediaStream this hook produces.
 */
export function useLocalMedia() {
  const [status, setStatus] = useState<MediaStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [micEnabled, setMicEnabled] = useState(true);
  const [cameraEnabled, setCameraEnabled] = useState(true);
  const streamRef = useRef<MediaStream | null>(null);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setStream(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setStatus("requesting_permission");

    navigator.mediaDevices
      .getUserMedia({ video: true, audio: true })
      .then((mediaStream) => {
        if (cancelled) {
          mediaStream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = mediaStream;
        setStream(mediaStream);
        setStatus("ready");
      })
      .catch((err) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Could not access camera/microphone");
        setStatus("error");
      });

    return () => {
      cancelled = true;
      stop();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggleMic = useCallback(() => {
    setMicEnabled((enabled) => {
      const next = !enabled;
      streamRef.current?.getAudioTracks().forEach((track) => (track.enabled = next));
      return next;
    });
  }, []);

  const toggleCamera = useCallback(() => {
    setCameraEnabled((enabled) => {
      const next = !enabled;
      streamRef.current?.getVideoTracks().forEach((track) => (track.enabled = next));
      return next;
    });
  }, []);

  return { status, error, stream, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop };
}
