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
  NetworkStatsIcon,
  EndMeetingIcon,
  RemoveParticipantIcon,
} from "@/components/icons";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import type { Meeting } from "@/features/meeting/api/meetingApi";
import { useMeetingSocket } from "@/features/meeting/hooks/useMeetingSocket";
import { useWebRtcPeers } from "@/features/meeting/hooks/useWebRtcPeers";
import { useScreenShare } from "@/features/meeting/hooks/useScreenShare";
import { ParticipantsPanel, type WaitingRequest, type RosterEntry } from "@/features/meeting/components/ParticipantsPanel";
import { ChatPanel } from "@/features/chat/components/ChatPanel";
import { MeetingTopbar } from "@/features/meeting/components/MeetingTopbar";
import { ScreenShareBanner } from "@/features/meeting/components/ScreenShareBanner";
import { NetworkDiagnosticsPanel } from "@/features/meeting/components/NetworkDiagnosticsPanel";
import { useWebRtcStats } from "@/features/meeting/hooks/useWebRtcStats";
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
  const [meetingEndedByHost, setMeetingEndedByHost] = useState(false);
  const [activePanel, setActivePanel] = useState<"none" | "chat" | "participants" | "diagnostics">("none");
  const [chatMessages, setChatMessages] = useState<ChatMessage[]>([]);
  const [joinNotice, setJoinNotice] = useState<string | null>(null);
  const [pinnedKey, setPinnedKey] = useState<string | null>(null);
  const [participantMicStates, setParticipantMicStates] = useState<Map<string, boolean>>(new Map());

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
    manager,
  } = useWebRtcPeers(user?.id ?? null, stream, peerIds, send, token);
  const networkStats = useWebRtcStats(manager, activePanel === "diagnostics");

  function handleSignalingMessage(envelope: SignalingEnvelope) {
    switch (envelope.type) {
      case "PARTICIPANT_JOINED": {
        const presence = envelope.payload as ParticipantPresencePayload;
        setParticipants((prev) => new Map(prev).set(presence.userId, presence));
        send({ type: "MIC_STATE_CHANGED", payload: { enabled: micEnabled } });
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
        if (micEnabled) {
          toggleMic();
          send({ type: "MIC_STATE_CHANGED", payload: { enabled: false } });
        }
        break;
      case "MIC_STATE_CHANGED": {
        const payload = envelope.payload as { enabled?: boolean };
        if (envelope.senderId && typeof payload?.enabled === "boolean") {
          setParticipantMicStates((prev) => new Map(prev).set(envelope.senderId as string, payload.enabled as boolean));
        }
        break;
      }
      case "HOST_REMOVE_PARTICIPANT":
        setRemovedByHost(true);
        break;
      case "MEETING_ENDED":
        stopLocalMedia();
        setMeetingEndedByHost(true);
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

  useEffect(() => {
    if (!meetingEndedByHost) return;
    const timer = setTimeout(() => router.push("/"), 3000);
    return () => clearTimeout(timer);
  }, [meetingEndedByHost, router]);

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
      send({ type: "MEETING_ENDED" });
    } finally {
      router.push("/");
    }
  }, [meeting, token, router, stopLocalMedia, send]);

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

  const handleToggleMic = useCallback(() => {
    const nextEnabled = !micEnabled;
    toggleMic();
    send({ type: "MIC_STATE_CHANGED", payload: { enabled: nextEnabled } });
  }, [micEnabled, toggleMic, send]);

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

  if (meetingEndedByHost) {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center gap-3 bg-[var(--stage)] px-6 text-center">
        <div className="flex h-14 w-14 items-center justify-center rounded-full bg-danger/15 text-danger">
          {EndMeetingIcon}
        </div>
        <h1 className="text-xl font-semibold">Cuộc họp đã kết thúc</h1>
        <p className="text-sm text-muted">Chủ phòng đã kết thúc &ldquo;{meeting.title}&rdquo;.</p>
        <p className="text-xs text-muted">Bạn sẽ được chuyển về trang chủ sau ít giây.</p>
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
    { userId: user?.id ?? "", displayName: user?.displayName ?? "Bạn", isSelf: true, isHost, micEnabled },
    ...remoteParticipants.map((p) => ({
      userId: p.userId,
      displayName: p.displayName,
      isSelf: false,
      isHost: p.userId === meeting.hostId,
      micEnabled: participantMicStates.get(p.userId) ?? true,
    })),
  ];
  const participantNames = new Map(roster.map((participant) => [participant.userId, participant.displayName]));

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
  const gridColumns =
    allTiles.length === 1
      ? "grid-cols-1"
      : allTiles.length === 2
        ? "grid-cols-1 md:grid-cols-2"
        : allTiles.length <= 4
          ? "grid-cols-1 sm:grid-cols-2"
          : allTiles.length <= 6
            ? "grid-cols-1 sm:grid-cols-2 lg:grid-cols-3"
            : "grid-cols-1 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4";

  function renderTileOverlay(tile: Tile) {
    return (
      <>
        <button
          onClick={() => setPinnedKey((prev) => (prev === tile.key ? null : tile.key))}
          title={pinnedKey === tile.key ? "Bỏ ghim" : "Ghim khung hình này"}
          aria-label={pinnedKey === tile.key ? "Bỏ ghim" : "Ghim khung hình này"}
          className={`absolute left-2 top-2 flex h-8 w-8 items-center justify-center rounded-full text-white opacity-0 transition-all group-hover:opacity-100 group-focus-within:opacity-100 ${
            pinnedKey === tile.key ? "bg-accent" : "bg-black/60 hover:bg-black/80"
          }`}
        >
          {PinIcon}
        </button>
        {isHost && tile.userId && tile.userId !== user?.id && !tile.key.startsWith("screen:") && (
          <div className="absolute right-2 top-2 flex gap-1 opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100">
            <button
              onClick={() => handleMuteParticipant(tile.userId as string)}
              disabled={participantMicStates.get(tile.userId) === false}
              aria-label={participantMicStates.get(tile.userId) === false ? `${tile.displayName} đã tắt micro` : `Tắt micro của ${tile.displayName}`}
              title={participantMicStates.get(tile.userId) === false ? "Người này đã tắt micro" : "Tắt micro người này"}
              className="flex h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80 disabled:cursor-default disabled:text-white/65 [&>svg]:h-4 [&>svg]:w-4"
            >
              {participantMicStates.get(tile.userId) === false ? MicOffIcon : MicOnIcon}
            </button>
            <button
              onClick={() => handleRemoveParticipant(tile.userId as string)}
              aria-label={`Mời ${tile.displayName} ra khỏi cuộc họp`}
              title="Mời người này ra khỏi cuộc họp"
              className="flex h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white hover:bg-danger/90 [&>svg]:h-4 [&>svg]:w-4"
            >
              {RemoveParticipantIcon}
            </button>
          </div>
        )}
      </>
    );
  }

  return (
    <div className="flex h-dvh flex-col overflow-hidden">
      <MeetingTopbar title={meeting.title} code={meeting.code} socketStatus={socketStatus} />
      <div className="relative flex min-h-0 flex-1 overflow-hidden pb-[4.75rem]">
        <main className="flex min-w-0 flex-1 flex-col gap-3 overflow-hidden bg-[var(--stage)] px-3 py-3 md:gap-4 md:px-5 md:py-4">

        {mediaError && <p className="flex-shrink-0 text-sm text-danger">{mediaError}</p>}
        {screenShare.error && <p className="flex-shrink-0 text-sm text-danger">{screenShare.error}</p>}
        {isScreenSharing && <ScreenShareBanner onStop={handleToggleScreenShare} />}
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
                fit={pinnedTile.key.startsWith("screen:") ? "contain" : "cover"}
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
          <section className={`grid min-h-0 flex-1 auto-rows-fr gap-3 overflow-hidden md:gap-4 ${gridColumns}`}>
            {allTiles.map((tile) => (
              <div
                key={tile.key}
                className="flex min-h-0 items-center justify-center overflow-hidden"
                style={{ containerType: "size" }}
              >
                <div
                  className="relative aspect-video"
                  style={{ width: "min(100cqw, 177.78cqh)" }}
                >
                  <VideoTile
                    stream={tile.stream}
                    active={tile.active}
                    displayName={tile.displayName}
                    label={tile.label}
                    mirrored={tile.mirrored}
                    muted={tile.muted}
                    fill
                    fit={tile.key.startsWith("screen:") ? "contain" : "cover"}
                  >
                    {renderTileOverlay(tile)}
                  </VideoTile>
                </div>
              </div>
            ))}
          </section>
        )}

        <div className="absolute bottom-3 left-3 right-3 z-30 flex items-center justify-start gap-2 overflow-x-auto rounded-xl border border-card-border bg-card/95 p-2 shadow-sm backdrop-blur sm:justify-center md:left-5 md:right-5 md:gap-3">
          <MediaToggleButton
            enabled={micEnabled}
            onClick={handleToggleMic}
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
          <MediaToggleButton
            enabled={activePanel !== "diagnostics"}
            onClick={() => setActivePanel((value) => value === "diagnostics" ? "none" : "diagnostics")}
            label={activePanel === "diagnostics" ? "Đóng chẩn đoán mạng" : "Mở chẩn đoán mạng"}
            enabledIcon={NetworkStatsIcon}
            disabledIcon={NetworkStatsIcon}
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
              aria-label="Kết thúc cuộc họp cho tất cả"
              title="Kết thúc cuộc họp cho tất cả"
              className="flex h-11 w-11 items-center justify-center rounded-full border border-danger-border bg-danger-bg text-danger transition-colors hover:bg-danger hover:text-white disabled:opacity-60"
            >
              {EndMeetingIcon}
            </button>
          )}
        </div>
        </main>

      {activePanel === "chat" && (
        <div className="absolute inset-0 z-20 flex justify-end sm:static [&>aside]:w-full sm:[&>aside]:w-80">
        <ChatPanel
          messages={chatMessages}
          currentUserId={user?.id ?? null}
          onSend={handleSendChat}
          onClose={() => setActivePanel("none")}
        />
        </div>
      )}
      {activePanel === "participants" && (
        <div className="absolute inset-0 z-20 flex justify-end sm:static [&>aside]:w-full sm:[&>aside]:w-80">
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
        </div>
      )}
      {activePanel === "diagnostics" && (
        <div className="absolute inset-0 z-20 flex justify-end sm:static [&>aside]:w-full sm:[&>aside]:w-80">
          <NetworkDiagnosticsPanel
            stats={networkStats}
            participantNames={participantNames}
            onClose={() => setActivePanel("none")}
          />
        </div>
      )}
      </div>
    </div>
  );
}
