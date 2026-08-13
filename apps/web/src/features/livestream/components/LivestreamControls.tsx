"use client";

import { useCallback, useState } from "react";
import { MediaToggleButton } from "@/components/MediaToggleButton";
import { LivestreamIcon } from "@/components/icons";
import * as livestreamApi from "@/features/livestream/api/livestreamApi";
import { useLivestreamBroadcaster } from "@/features/livestream/hooks/useLivestreamBroadcaster";
import { ApiClientError } from "@/lib/api/client";

/**
 * Host-only start/stop control, mounted next to the other meeting control
 * buttons. Broadcasts whatever `localStream` currently is (the presenter's
 * own camera/mic) — see docs/networking/livestream.md for why this is a
 * single local view rather than a composite of every participant.
 */
export function LivestreamControls({
  meetingId,
  code,
  token,
  localStream,
}: {
  meetingId: string;
  code: string;
  token: string;
  localStream: MediaStream | null;
}) {
  const broadcaster = useLivestreamBroadcaster();
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleToggle = useCallback(async () => {
    if (broadcaster.isBroadcasting) {
      broadcaster.stop();
      try {
        await livestreamApi.stopLivestream(token, meetingId);
      } catch {
        // best-effort — the browser side has already stopped either way
      }
      return;
    }

    if (!localStream) {
      setError("Chưa có luồng camera/mic để livestream");
      return;
    }

    setStarting(true);
    setError(null);
    try {
      await livestreamApi.startLivestream(token, meetingId);
      broadcaster.start(meetingId, token, localStream);
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Không thể bắt đầu livestream");
    } finally {
      setStarting(false);
    }
  }, [broadcaster, localStream, meetingId, token]);

  const watchUrl = typeof window !== "undefined" ? `${window.location.origin}/watch/${code}` : `/watch/${code}`;

  return (
    <div className="flex flex-col items-center gap-1">
      <MediaToggleButton
        enabled={!broadcaster.isBroadcasting}
        onClick={handleToggle}
        label={broadcaster.isBroadcasting ? "Dừng livestream" : "Bắt đầu livestream"}
        enabledIcon={LivestreamIcon}
        disabledIcon={LivestreamIcon}
      />
      {starting && <span className="text-[10px] text-muted">Đang bắt đầu…</span>}
      {broadcaster.isBroadcasting && (
        <span className="max-w-[9rem] truncate text-[10px] text-muted" title={watchUrl}>
          {watchUrl}
        </span>
      )}
      {(error || broadcaster.error) && (
        <span className="max-w-[9rem] text-center text-[10px] text-danger">{error ?? broadcaster.error}</span>
      )}
    </div>
  );
}
