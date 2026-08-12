"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useLocalMedia } from "@/hooks/useLocalMedia";
import { VideoTile } from "@/components/VideoTile";
import { MediaToggleButton } from "@/components/MediaToggleButton";
import { MicOnIcon, MicOffIcon, CameraOnIcon, CameraOffIcon } from "@/components/icons";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { ApiClientError } from "@/lib/api/client";
import { MeetingTopbar } from "@/features/meeting/components/MeetingTopbar";

type LoadState = "loading" | "ready" | "not_found" | "ended";

export function LobbyView({ code }: { code: string }) {
  const { status: authStatus, user, token } = useAuth();
  const router = useRouter();
  const { status: mediaStatus, error: mediaError, stream, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop } = useLocalMedia();

  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [displayName, setDisplayName] = useState("");
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState<string | null>(null);

  useEffect(() => {
    if (authStatus === "unauthenticated") {
      router.push(`/login?redirect=/meet/${code}`);
    }
  }, [authStatus, router, code]);

  useEffect(() => {
    if (user) setDisplayName(user.displayName);
  }, [user]);

  useEffect(() => {
    if (authStatus !== "authenticated" || !token) return;

    meetingApi
      .getMeetingByCode(token, code)
      .then((m) => {
        setMeeting(m);
        setLoadState(m.status === "ENDED" || m.status === "CANCELLED" ? "ended" : "ready");
      })
      .catch(() => setLoadState("not_found"));
  }, [authStatus, token, code]);

  useEffect(() => {
    if (!token || meeting?.status !== "SCHEDULED") return;
    const timer = window.setInterval(() => {
      meetingApi.getMeetingByCode(token, code).then((updated) => {
        setMeeting(updated);
        if (updated.status === "ENDED" || updated.status === "CANCELLED") setLoadState("ended");
      }).catch(() => undefined);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [token, code, meeting?.status]);

  async function handleJoin() {
    if (!meeting || !token) return;
    setJoining(true);
    setJoinError(null);
    try {
      if (meeting.status === "SCHEDULED" && meeting.hostId === user?.id) {
        await meetingApi.startMeeting(token, meeting.id);
      }
      await meetingApi.joinMeeting(token, meeting.id);
      stop();
      router.push(`/meet/${code}/room`);
    } catch (err) {
      setJoinError(err instanceof ApiClientError ? err.message : "Không thể tham gia cuộc họp");
      setJoining(false);
    }
  }

  const waitingForHost = meeting?.status === "SCHEDULED" && meeting.hostId !== user?.id;

  if (authStatus !== "authenticated" || loadState === "loading") {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-muted">Đang tải…</p>
      </main>
    );
  }

  if (loadState === "not_found") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">Không tìm thấy cuộc họp</h1>
        <p className="text-sm text-muted">Không có cuộc họp nào với mã &ldquo;{code}&rdquo;.</p>
        <Link href="/" className="text-sm font-medium text-accent hover:underline">
          Về trang chủ
        </Link>
      </main>
    );
  }

  if (loadState === "ended") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">Cuộc họp này đã kết thúc</h1>
        <Link href="/" className="text-sm font-medium text-accent hover:underline">
          Về trang chủ
        </Link>
      </main>
    );
  }

  if (!meeting) return null;

  return (
    <div className="flex h-dvh flex-col overflow-hidden">
      <MeetingTopbar title={meeting.title} code={meeting.code} />
      <main className="flex min-h-0 flex-1 flex-col items-center justify-center gap-8 overflow-y-auto px-6 py-8">
      <div className="flex w-full max-w-3xl flex-col items-center gap-2 text-center">
        <h1 className="text-xl font-semibold tracking-tight">{meeting?.title}</h1>
        <p className="text-sm text-muted">
          {waitingForHost
            ? "Đang chờ chủ phòng bắt đầu cuộc họp."
            : meeting?.accessType === "APPROVAL_REQUIRED" && meeting.hostId !== user?.id
              ? "Chủ phòng cần phê duyệt trước khi bạn có thể vào."
              : meeting?.status === "SCHEDULED"
                ? "Bạn là chủ phòng. Hãy bắt đầu khi đã sẵn sàng."
                : "Sẵn sàng tham gia?"}
        </p>
      </div>

      <div className="grid w-full max-w-3xl gap-8 md:grid-cols-2 md:items-center">
        <div className="flex flex-col gap-3">
          <VideoTile stream={stream} active={cameraEnabled} displayName={displayName} mirrored muted />
          {mediaStatus === "error" && (
            <p className="text-xs text-danger">{mediaError ?? "Không thể truy cập camera/micro"}</p>
          )}
          <div className="flex justify-center gap-3">
            <MediaToggleButton
              enabled={micEnabled}
              onClick={toggleMic}
              label={micEnabled ? "Tắt micro" : "Bật micro"}
              enabledIcon={MicOnIcon}
              disabledIcon={MicOffIcon}
            />
            <MediaToggleButton
              enabled={cameraEnabled}
              onClick={toggleCamera}
              label={cameraEnabled ? "Tắt camera" : "Bật camera"}
              enabledIcon={CameraOnIcon}
              disabledIcon={CameraOffIcon}
            />
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label htmlFor="displayName" className="text-sm font-medium">
              Tên của bạn
            </label>
            <input
              id="displayName"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
            />
          </div>
          {joinError && <p className="text-sm text-danger">{joinError}</p>}
          <button
            onClick={handleJoin}
            disabled={joining || waitingForHost}
            className="rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover disabled:opacity-60"
          >
            {joining
              ? "Đang tham gia…"
              : waitingForHost
                ? "Đang chờ chủ phòng"
                : meeting?.status === "SCHEDULED"
                  ? "Bắt đầu cuộc họp"
                  : "Tham gia ngay"}
          </button>
        </div>
      </div>
      </main>
    </div>
  );
}
