"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/context/AuthContext";
import { useLocalMedia } from "@/hooks/useLocalMedia";
import { VideoTile } from "@/components/VideoTile";
import { MediaToggleButton } from "@/components/MediaToggleButton";
import {
  MicOnIcon,
  MicOffIcon,
  CameraOnIcon,
  CameraOffIcon,
  ChatIcon,
  ScreenShareOnIcon,
  ScreenShareOffIcon,
  PeopleIcon,
  PinIcon,
  PhoneHangupIcon,
} from "@/components/icons";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { useMeetingSocket } from "@/features/meeting/hooks/useMeetingSocket";
import { useWebRtcPeers } from "@/features/meeting/hooks/useWebRtcPeers";
import { useScreenShare } from "@/features/meeting/hooks/useScreenShare";
import { ParticipantsPanel, type WaitingRequest, type RosterEntry } from "@/features/meeting/components/ParticipantsPanel";
import { ChatPanel } from "@/features/chat/components/ChatPanel";
import * as chatApi from "@/features/chat/api/chatApi";
import type { ChatMessage } from "@/features/chat/api/chatApi";
import type { ParticipantPresencePayload, SignalingEnvelope } from "@/lib/websocket/types";

type WaitingState = "none" | "waiting" | "rejected";

export function MeetingRoomView({ code }: { code: string }) {
  const { status: authStatus, user, token } = useAuth();
  const router = useRouter();
  const { stream, error: mediaError, micEnabled, cameraEnabled, toggleMic, toggleCamera, stop: stopLocalMedia } = useLocalMedia();
  const screenShare = useScreenShare();

  const [meeting, setMeeting] = useState<Meeting | null>(null);
  const [participants, setParticipants] = useState<Map<string, ParticipantPresencePayload>>(new Map());
  const [loadError, setLoadError] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);
  const [waitingState, setWaitingState] = useState<WaitingState>("none");
  const [waitingRequests, setWaitingRequests] = useState<WaitingRequest[]>([]);
  const [sharingUserId, setSharingUserId] = useState<string | null>(null);
  const [removedByHost, setRemovedByHost] = useState(false);
  const [activePanel, setActivePanel] = useState<"none" | "chat" | "participants">("none");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [joinNotice, setJoinNotice] = useState<string | null>(null);
  const [pinnedKey, setPinnedKey] = useState<string | null>(null);

  useEffect(() => {
    if (authStatus === "unauthenticated") {
      router.push(`/login?redirect=/meet/${code}/room`);
    }
  }, [authStatus, router, code]);

  useEffect(() => {
    if (authStatus !== "authenticated" || !token) return;
    let cancelled = false;

    meetingApi
      .getMeetingByCode(token, code)
      .then((m) => {
        if (!cancelled) setMeeting(m);
      })
      .catch(() => {
        if (!cancelled) setLoadError("Không tìm thấy cuộc họp này.");
      });

    return () => {
      cancelled = true;
    };
  }, [authStatus, token, code]);

  useEffect(() => {
    if (!meeting || !token) return;
    let cancelled = false;

    meetingApi.listParticipants(token, meeting.id).then((list) => {
      if (cancelled) return;
      setParticipants(
        new Map(list.filter((p) => p.userId).map((p) => [p.userId as string, {
          userId: p.userId as string,
          displayName: p.displayName,
          role: p.role,
        }])),
      );
    });
    chatApi.fetchChatHistory(token, meeting.id).then((history) => {
      if (!cancelled) setChatMessages(history);
    });

    return () => {
      cancelled = true;
    };
  }, [meeting, token]);

  const peerIds = useMemo(
    () => Array.from(participants.keys()).filter((id) => id !== user?.id),
    [participants, user?.id],
  );

  const { send, status: socketStatus } = useMeetingSocket(meeting?.id ?? null, token, (envelope) =>
    handleSignalingMessage(envelope),
  );

  const {
    remoteStreams,
    remoteScreenStreams,
    handleEnvelope: handleWebRtcEnvelope,
    startScreenShare,
    stopScreenShare,
    isScreenSharing,
  } = useWebRtcPeers(user?.id ?? null, stream, peerIds, send, token);

  function handleSignalingMessage(envelope: SignalingEnvelope) {
    switch (envelope.type) {
      case "PARTICIPANT_JOINED": {
        const presence = envelope.payload as ParticipantPresencePayload;
        setParticipants((prev) => new Map(prev).set(presence.userId, presence));
        break;
      }
      case "PARTICIPANT_LEFT": {
        const presence = envelope.payload as ParticipantPresencePayload;
        setParticipants((prev) => {
          const next = new Map(prev);
          next.delete(presence.userId);
          return next;
        });
        setWaitingRequests((prev) => prev.filter((w) => w.userId !== presence.userId));
        break;
      }
      case "WEBRTC_OFFER":
      case "WEBRTC_ANSWER":
      case "ICE_CANDIDATE":
      case "PARTICIPANT_RECONNECTED":
        handleWebRtcEnvelope(envelope);
        break;
      case "PARTICIPANT_WAITING": {
        const payload = envelope.payload as WaitingRequest;
        if (envelope.senderId === user?.id) {
          setWaitingState("waiting");
        } else {
          setWaitingRequests((prev) => [...prev.filter((w) => w.userId !== payload.userId), payload]);
          setJoinNotice(`${payload.displayName} yêu cầu tham gia cuộc họp`);
        }
        break;
      }
      case "PARTICIPANT_APPROVED":
        if (envelope.senderId === user?.id) {
          setWaitingState("none");
        }
        break;
      case "PARTICIPANT_REJECTED":
        if (envelope.senderId === user?.id) {
          setWaitingState("rejected");
        }
        break;
      case "HOST_MUTE_PARTICIPANT":
        if (micEnabled) toggleMic();
        break;
      case "HOST_REMOVE_PARTICIPANT":
        setRemovedByHost(true);
        break;
      case "SCREEN_SHARE_STARTED":
        setSharingUserId(envelope.senderId);
        break;
      case "SCREEN_SHARE_STOPPED":
        setSharingUserId((prev) => (prev === envelope.senderId ? null : prev));
        break;
      case "CHAT_MESSAGE": {
        const payload = envelope.payload as { id: string; senderId: string; senderDisplayName: string; body: string; createdAt: string };
        if (!meeting) break;
        setChatMessages((prev) => (prev.some((m) => m.id === payload.id) ? prev : [...prev, { ...payload, meetingId: meeting.id }]));
        break;
      }
    }
  }

  useEffect(() => {
    if (!joinNotice) return;
    const timer = setTimeout(() => setJoinNotice(null), 5000);
    return () => clearTimeout(timer);
  }, [joinNotice]);

  useEffect(() => {
    if (sharingUserId) {
      setPinnedKey(`screen:${sharingUserId}`);
    } else {
      setPinnedKey((prev) => (prev?.startsWith("screen:") ? null : prev));
    }
  }, [sharingUserId]);

  useEffect(() => {
    if (removedByHost) {
      stopLocalMedia();
      const timer = setTimeout(() => router.push("/"), 2000);
      return () => clearTimeout(timer);
    }
  }, [removedByHost, router, stopLocalMedia]);

  const handleLeave = useCallback(async () => {
    if (!meeting || !token) return;
    setLeaving(true);
    stopLocalMedia();
    try {
      await meetingApi.leaveMeeting(token, meeting.id);
    } finally {
      router.push("/");
    }
  }, [meeting, token, router, stopLocalMedia]);

  const handleEnd = useCallback(async () => {
    if (!meeting || !token) return;
    setLeaving(true);
    stopLocalMedia();
    try {
      await meetingApi.endMeeting(token, meeting.id);
    } finally {
      router.push("/");
    }
  }, [meeting, token, router, stopLocalMedia]);

  const handleToggleScreenShare = useCallback(async () => {
    if (isScreenSharing) {
      stopScreenShare();
      screenShare.stop();
      send({ type: "SCREEN_SHARE_STOPPED" });
      setSharingUserId((prev) => (prev === user?.id ? null : prev));
      return;
    }
    const captured = await screenShare.start(() => {
      stopScreenShare();
      send({ type: "SCREEN_SHARE_STOPPED" });
      setSharingUserId((prev) => (prev === user?.id ? null : prev));
    });
    if (captured) {
      await startScreenShare(captured);
      send({ type: "SCREEN_SHARE_STARTED" });
      setSharingUserId(user?.id ?? null);
    }
  }, [isScreenSharing, screenShare, startScreenShare, stopScreenShare, send, user?.id]);

  const handleSendChat = useCallback((body: string) => {
    send({ type: "CHAT_MESSAGE", payload: { body } });
  }, [send]);

  const handleAdmit = useCallback((userId: string) => {
    send({ type: "PARTICIPANT_APPROVED", targetId: userId });
    setWaitingRequests((prev) => prev.filter((w) => w.userId !== userId));
  }, [send]);

  const handleDeny = useCallback((userId: string) => {
    send({ type: "PARTICIPANT_REJECTED", targetId: userId });
    setWaitingRequests((prev) => prev.filter((w) => w.userId !== userId));
  }, [send]);

  const handleMuteParticipant = useCallback((userId: string) => {
    send({ type: "HOST_MUTE_PARTICIPANT", targetId: userId });
  }, [send]);

  const handleRemoveParticipant = useCallback((userId: string) => {
    send({ type: "HOST_REMOVE_PARTICIPANT", targetId: userId });
  }, [send]);

  if (authStatus !== "authenticated" || (!meeting && !loadError)) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <p className="text-sm text-muted">Đang tải…</p>
      </main>
    );
  }

  if (loadError || !meeting) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">{loadError}</h1>
      </main>
    );
  }

  if (removedByHost) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">Bạn đã bị đưa ra khỏi cuộc họp</h1>
        <p className="text-sm text-muted">Chủ phòng đã đưa bạn ra khỏi &ldquo;{meeting.title}&rdquo;.</p>
      </main>
    );
  }

  if (waitingState === "rejected") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-4 px-6 text-center">
        <h1 className="text-xl font-semibold">Chủ phòng đã từ chối yêu cầu tham gia của bạn</h1>
      </main>
    );
  }

  if (waitingState === "waiting") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-6 px-6 text-center">
        <VideoTile stream={stream} active={cameraEnabled} displayName={user?.displayName ?? "Bạn"} mirrored muted />
        <h1 className="text-xl font-semibold">Đang chờ chủ phòng cho phép vào</h1>
        <p className="text-sm text-muted">&ldquo;{meeting.title}&rdquo; yêu cầu chủ phòng phê duyệt trước khi tham gia.</p>
        <button
          onClick={handleLeave}
          className="rounded-lg border border-input-border px-4 py-2.5 text-sm font-medium transition-colors hover:bg-black/5 dark:hover:bg-white/10"
        >
          Hủy
        </button>
      </main>
    );
  }

  const isHost = user?.id === meeting.hostId;
  const remoteParticipants = Array.from(participants.values()).filter((p) => p.userId !== user?.id);
  const spotlightStream = sharingUserId
    ? sharingUserId === user?.id
      ? screenShare.stream
      : remoteScreenStreams.get(sharingUserId) ?? null
    : null;

  const roster: RosterEntry[] = [
    { userId: user?.id ?? "", displayName: user?.displayName ?? "Bạn", isSelf: true, isHost },
    ...remoteParticipants.map((p) => ({
      userId: p.userId,
      displayName: p.displayName,
      isSelf: false,
      isHost: p.userId === meeting.hostId,
    })),
  ];

  type Tile = { key: string; stream: MediaStream | null; active: boolean; displayName: string; label: string; userId: string | null; muted: boolean; mirrored: boolean };

  const selfTile: Tile = {
    key: "self",
    stream,
    active: cameraEnabled,
    displayName: user?.displayName ?? "Bạn",
    label: `${user?.displayName ?? "Bạn"} (bạn)`,
    userId: user?.id ?? null,
    muted: true,
    mirrored: true,
  };
  const remoteTiles: Tile[] = remoteParticipants.map((p) => ({
    key: `remote:${p.userId}`,
    stream: remoteStreams.get(p.userId) ?? null,
    active: true,
    displayName: p.displayName,
    label: p.displayName,
    userId: p.userId,
    muted: false,
    mirrored: false,
  }));
  const screenTile: Tile | null = spotlightStream
    ? {
        key: `screen:${sharingUserId}`,
        stream: spotlightStream,
        active: true,
        displayName: "Chia sẻ màn hình",
        label: sharingUserId === user?.id ? "Chia sẻ màn hình của bạn" : "Chia sẻ màn hình",
        userId: null,
        muted: false,
        mirrored: false,
      }
    : null;

  const allTiles = [selfTile, ...remoteTiles, ...(screenTile ? [screenTile] : [])];
  const pinnedTile = allTiles.find((t) => t.key === pinnedKey) ?? null;
  const otherTiles = pinnedTile ? allTiles.filter((t) => t.key !== pinnedTile.key) : allTiles;

  function renderTileOverlay(tile: Tile) {
    return (
      <>
        <button
          onClick={() => setPinnedKey((prev) => (prev === tile.key ? null : tile.key))}
          title={pinnedKey === tile.key ? "Bỏ ghim" : "Ghim khung hình này"}
          aria-label={pinnedKey === tile.key ? "Bỏ ghim" : "Ghim khung hình này"}
          className={`absolute left-2 top-2 flex h-7 w-7 items-center justify-center rounded-full text-white transition-colors ${
            pinnedKey === tile.key ? "bg-accent" : "bg-black/60 hover:bg-black/80"
          }`}
        >
          {PinIcon}
        </button>
        {isHost && tile.userId && tile.userId !== user?.id && !tile.key.startsWith("screen:") && (
          <div className="absolute right-2 top-2 flex gap-1">
            <button
              onClick={() => handleMuteParticipant(tile.userId as string)}
              title="Tắt micro người này"
              className="rounded bg-black/60 px-2 py-1 text-xs text-white hover:bg-black/80"
            >
              Tắt tiếng
            </button>
            <button
              onClick={() => handleRemoveParticipant(tile.userId as string)}
              title="Đưa người này ra khỏi cuộc họp"
              className="rounded bg-danger/80 px-2 py-1 text-xs text-white hover:bg-danger"
            >
              Xóa
            </button>
          </div>
        )}
      </>
    );
  }

  return (
    <div className="flex h-screen overflow-hidden">
      <main className="flex min-w-0 flex-1 flex-col gap-4 overflow-hidden px-6 py-6">
        <header className="flex flex-shrink-0 flex-col gap-1">
          <h1 className="text-xl font-semibold tracking-tight">{meeting.title}</h1>
          <div className="flex items-center gap-2 text-sm text-muted">
            <span>Mã: {meeting.code}</span>
            <span aria-hidden="true">·</span>
            <span className="inline-flex items-center gap-1.5">
              <span
                className={`h-1.5 w-1.5 rounded-full ${
                  socketStatus === "open"
                    ? "bg-green-500"
                    : socketStatus === "connecting" || socketStatus === "reconnecting"
                      ? "animate-pulse bg-yellow-400"
                      : "bg-red-500"
                }`}
              />
              {socketStatus === "open"
                ? "Đang hoạt động"
                : socketStatus === "connecting"
                  ? "Đang kết nối…"
                  : socketStatus === "reconnecting"
                    ? "Mất kết nối, đang thử lại…"
                    : "Mất kết nối"}
            </span>
          </div>
        </header>

        {mediaError && <p className="flex-shrink-0 text-sm text-danger">{mediaError}</p>}
        {screenShare.error && <p className="flex-shrink-0 text-sm text-danger">{screenShare.error}</p>}
        {joinNotice && (
          <div className="flex flex-shrink-0 items-center justify-between gap-3 rounded-lg border border-accent/30 bg-accent/10 px-3 py-2 text-sm">
            <span>{joinNotice}</span>
            <button
              onClick={() => setActivePanel("participants")}
              className="flex-shrink-0 rounded-md bg-accent px-2.5 py-1 text-xs font-medium text-accent-foreground hover:bg-accent-hover"
            >
              Xem yêu cầu
            </button>
          </div>
        )}

        {pinnedTile ? (
          <div className="flex min-h-0 flex-1 gap-4">
            <div className="relative h-full" style={{ flex: "2 1 0%" }}>
              <VideoTile
                stream={pinnedTile.stream}
                active={pinnedTile.active}
                displayName={pinnedTile.displayName}
                label={pinnedTile.label}
                mirrored={pinnedTile.mirrored}
                muted={pinnedTile.muted}
                fill
              >
                {renderTileOverlay(pinnedTile)}
              </VideoTile>
            </div>
            <div className="flex h-full flex-col gap-3 overflow-y-auto" style={{ flex: "1 1 0%" }}>
              {otherTiles.map((tile) => (
                <div key={tile.key} className="relative flex-shrink-0">
                  <VideoTile
                    stream={tile.stream}
                    active={tile.active}
                    displayName={tile.displayName}
                    label={tile.label}
                    mirrored={tile.mirrored}
                    muted={tile.muted}
                  >
                    {renderTileOverlay(tile)}
                  </VideoTile>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <section className="grid min-h-0 flex-1 auto-rows-fr grid-cols-1 gap-4 overflow-y-auto sm:grid-cols-2 lg:grid-cols-3">
            {allTiles.map((tile) => (
              <div key={tile.key} className="relative">
                <VideoTile
                  stream={tile.stream}
                  active={tile.active}
                  displayName={tile.displayName}
                  label={tile.label}
                  mirrored={tile.mirrored}
                  muted={tile.muted}
                >
                  {renderTileOverlay(tile)}
                </VideoTile>
              </div>
            ))}
          </section>
        )}

        <div className="flex flex-shrink-0 justify-center gap-3 pt-2">
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
          <MediaToggleButton
            enabled={!isScreenSharing}
            onClick={handleToggleScreenShare}
            label={isScreenSharing ? "Dừng chia sẻ màn hình" : "Chia sẻ màn hình"}
            enabledIcon={ScreenShareOffIcon}
            disabledIcon={ScreenShareOnIcon}
          />
          <div className="relative">
            <MediaToggleButton
              enabled={activePanel !== "participants"}
              onClick={() => setActivePanel((v) => (v === "participants" ? "none" : "participants"))}
              label={activePanel === "participants" ? "Đóng danh sách thành viên" : "Xem thành viên"}
              enabledIcon={PeopleIcon}
              disabledIcon={PeopleIcon}
            />
            {isHost && waitingRequests.length > 0 && (
              <span className="pointer-events-none absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
                {waitingRequests.length}
              </span>
            )}
          </div>
          <MediaToggleButton
            enabled={activePanel !== "chat"}
            onClick={() => setActivePanel((v) => (v === "chat" ? "none" : "chat"))}
            label={activePanel === "chat" ? "Đóng khung chat" : "Mở khung chat"}
            enabledIcon={ChatIcon}
            disabledIcon={ChatIcon}
          />
          <button
            onClick={handleLeave}
            disabled={leaving}
            aria-label="Rời cuộc họp"
            title="Rời cuộc họp"
            className="flex h-11 w-11 items-center justify-center rounded-full bg-danger text-white transition-opacity hover:opacity-90 disabled:opacity-60"
          >
            {PhoneHangupIcon}
          </button>
          {isHost && (
            <button
              onClick={handleEnd}
              disabled={leaving}
              className="rounded-full border border-danger-border bg-danger-bg px-4 text-sm font-medium text-danger transition-opacity hover:opacity-90 disabled:opacity-60"
            >
              Kết thúc cho tất cả
            </button>
          )}
        </div>
      </main>

      {activePanel === "chat" && (
        <ChatPanel
          messages={chatMessages}
          currentUserId={user?.id ?? null}
          onSend={handleSendChat}
          onClose={() => setActivePanel("none")}
        />
      )}
      {activePanel === "participants" && (
        <ParticipantsPanel
          waiting={waitingRequests}
          roster={roster}
          isHost={isHost}
          onAdmit={handleAdmit}
          onDeny={handleDeny}
          onMute={handleMuteParticipant}
          onRemove={handleRemoveParticipant}
          onClose={() => setActivePanel("none")}
        />
      )}
    </div>
  );
}
