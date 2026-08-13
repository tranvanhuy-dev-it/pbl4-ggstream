"use client";

import { useCallback, useRef, useState } from "react";
import { livestreamIngestUrl } from "@/features/livestream/api/livestreamApi";

// A short timeslice keeps chunks small enough to forward over the socket
// with low latency — MediaRecorder still produces one contiguous webm
// stream across chunks (each ondataavailable Blob is a fragment of it, not
// an independently-decodable file), which is exactly what FFmpeg reading
// from a single stdin pipe expects.
const CHUNK_INTERVAL_MS = 1000;

/**
 * Captures a MediaStream via MediaRecorder and forwards each encoded chunk
 * to the backend's livestream ingest WebSocket — the browser-side half of
 * the "broadcast → FFmpeg → HLS" pipeline (see
 * docs/networking/livestream.md). The backend transcodes whatever codec
 * MediaRecorder used (VP8/Opus in WebM, broadly supported) to H.264/AAC.
 */
export function useLivestreamBroadcaster() {
  const recorderRef = useRef<MediaRecorder | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const [isBroadcasting, setIsBroadcasting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const start = useCallback((meetingId: string, token: string, stream: MediaStream) => {
    setError(null);
    const socket = new WebSocket(livestreamIngestUrl(meetingId, token));
    socket.binaryType = "arraybuffer";
    socketRef.current = socket;

    socket.onopen = () => {
      const mimeType = ["video/webm;codecs=vp8,opus", "video/webm"].find((type) => MediaRecorder.isTypeSupported(type));
      const recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
      recorderRef.current = recorder;

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0 && socket.readyState === WebSocket.OPEN) {
          event.data.arrayBuffer().then((buffer) => socket.send(buffer));
        }
      };
      recorder.onerror = () => setError("Không thể ghi luồng livestream");
      recorder.start(CHUNK_INTERVAL_MS);
      setIsBroadcasting(true);
    };

    socket.onerror = () => setError("Mất kết nối tới máy chủ livestream");
    socket.onclose = () => {
      recorderRef.current?.stop();
      recorderRef.current = null;
      setIsBroadcasting(false);
    };
  }, []);

  const stop = useCallback(() => {
    recorderRef.current?.stop();
    recorderRef.current = null;
    socketRef.current?.close();
    socketRef.current = null;
    setIsBroadcasting(false);
  }, []);

  return { isBroadcasting, error, start, stop };
}
