"use client";

import { useCallback, useRef, useState } from "react";

/**
 * getDisplayMedia() capture, kept separate from the camera/mic stream
 * (useLocalMedia) — screen share is its own MediaStream added as an
 * additional WebRTC track (see PeerConnectionManager.startScreenShare),
 * never a replacement for the camera track.
 */
export function useScreenShare() {
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [error, setError] = useState<string | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  const start = useCallback(async (onEnded: () => void): Promise<MediaStream | null> => {
    setError(null);
    try {
      const captured = await navigator.mediaDevices.getDisplayMedia({ video: true, audio: false });
      streamRef.current = captured;
      setStream(captured);
      // The browser's own "Stop sharing" control (or closing the shared
      // window/tab) ends the track directly — that's the authoritative
      // signal to stop, not a button click inside our own UI.
      captured.getVideoTracks()[0]?.addEventListener("ended", onEnded);
      return captured;
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start screen sharing");
      return null;
    }
  }, []);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    setStream(null);
  }, []);

  return { stream, error, start, stop };
}
