"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/context/AuthContext";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import { ApiClientError } from "@/lib/api/client";

function defaultLocalDate(hoursAhead: number) {
  const date = new Date(Date.now() + hoursAhead * 60 * 60 * 1000);
  date.setMinutes(Math.ceil(date.getMinutes() / 15) * 15, 0, 0);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export function CreateJoinMeeting({ onScheduled }: { onScheduled?: () => void }) {
  const { token } = useAuth();
  const router = useRouter();
  const [title, setTitle] = useState("Cuộc họp mới");
  const [requireApproval, setRequireApproval] = useState(false);
  const [joinCode, setJoinCode] = useState("");
  const [creating, setCreating] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [showSchedule, setShowSchedule] = useState(false);
  const [scheduledStart, setScheduledStart] = useState(() => defaultLocalDate(1));
  const [scheduledEnd, setScheduledEnd] = useState(() => defaultLocalDate(2));
  const [error, setError] = useState<string | null>(null);

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    setCreating(true);
    setError(null);
    try {
      const meeting = await meetingApi.createMeeting(
        token,
        title.trim() || "Cuộc họp mới",
        requireApproval ? "APPROVAL_REQUIRED" : "PUBLIC",
      );
      router.push(`/meet/${meeting.code}`);
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Không thể tạo cuộc họp");
      setCreating(false);
    }
  }

  async function handleSchedule(event: React.FormEvent) {
    event.preventDefault();
    if (!token) return;
    setCreating(true);
    setError(null);
    try {
      await meetingApi.createMeeting(token, title.trim() || "Cuộc họp mới",
        requireApproval ? "APPROVAL_REQUIRED" : "PUBLIC", {
          scheduledStartAt: new Date(scheduledStart).toISOString(),
          scheduledEndAt: new Date(scheduledEnd).toISOString(),
          timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        });
      setShowSchedule(false);
      onScheduled?.();
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Không thể lên lịch cuộc họp");
    } finally {
      setCreating(false);
    }
  }

  function handleJoin(event: React.FormEvent) {
    event.preventDefault();
    const code = joinCode.trim().toLowerCase();
    if (code) router.push(`/meet/${code}`);
  }

  return (
    <div className="relative flex items-center gap-2">
      <form onSubmit={handleJoin} className="flex h-11 min-w-0 rounded-full bg-neutral-100 p-1 dark:bg-neutral-800">
        <label className="flex min-w-0 items-center gap-2 px-3">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <rect x="3" y="5" width="18" height="14" rx="2" /><path d="M7 9h2M11 9h2M15 9h2M7 13h2M11 13h6" />
          </svg>
          <input
            value={joinCode}
            onChange={(event) => setJoinCode(event.target.value)}
            placeholder="Nhập mã hoặc đường liên kết"
            aria-label="Mã hoặc đường liên kết cuộc họp"
            className="w-40 bg-transparent text-sm outline-none placeholder:text-muted sm:w-56 lg:w-72"
          />
        </label>
        <button
          type="submit"
          disabled={!joinCode.trim()}
          className="rounded-full bg-accent px-4 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover disabled:bg-neutral-300 disabled:text-neutral-500 dark:disabled:bg-neutral-700"
        >
          Tham gia
        </button>
      </form>

      <button
        type="button"
        onClick={() => { setShowCreate((value) => !value); setShowSchedule(false); }}
        className="flex h-11 items-center gap-2 rounded-full bg-accent px-4 text-sm font-semibold text-accent-foreground hover:bg-accent-hover"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <rect x="3" y="6" width="13" height="12" rx="2" /><path d="M16 10l5-2v8l-5-2M9.5 9v6M6.5 12h6" />
        </svg>
        <span className="hidden sm:inline">Mới</span>
      </button>

      <button
        type="button"
        onClick={() => { setShowSchedule((value) => !value); setShowCreate(false); }}
        className="flex h-11 items-center gap-2 rounded-full border border-input-border px-3 text-sm font-semibold hover:bg-black/5 dark:hover:bg-white/10 sm:px-4"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M7 3v4M17 3v4M3 10h18" /></svg>
        <span className="hidden sm:inline">Lên lịch</span>
      </button>

      {showCreate && (
        <form onSubmit={handleCreate} className="absolute right-0 top-14 z-40 flex w-80 flex-col gap-4 rounded-2xl border border-card-border bg-card p-4 shadow-xl">
          <div>
            <h2 className="font-semibold">Tạo cuộc họp mới</h2>
            <p className="mt-1 text-xs text-muted">Đặt tên và chọn quyền truy cập cho phòng.</p>
          </div>
          <input
            autoFocus
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="Tên cuộc họp"
            className="rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent"
          />
          <label className="flex items-start gap-2 text-sm text-muted">
            <input className="mt-0.5" type="checkbox" checked={requireApproval} onChange={(event) => setRequireApproval(event.target.checked)} />
            Yêu cầu chủ phòng phê duyệt trước khi vào
          </label>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setShowCreate(false)} className="rounded-lg px-3 py-2 text-sm hover:bg-black/5 dark:hover:bg-white/10">Hủy</button>
            <button type="submit" disabled={creating} className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-foreground hover:bg-accent-hover disabled:opacity-60">
              {creating ? "Đang tạo…" : "Tạo phòng"}
            </button>
          </div>
        </form>
      )}

      {showSchedule && (
        <form onSubmit={handleSchedule} className="absolute right-0 top-14 z-40 flex w-80 flex-col gap-4 rounded-2xl border border-card-border bg-card p-4 shadow-xl">
          <div>
            <h2 className="font-semibold">Lên lịch cuộc họp</h2>
            <p className="mt-1 text-xs text-muted">Thời gian được lưu theo múi giờ của thiết bị.</p>
          </div>
          <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Tên cuộc họp"
            className="rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent" />
          <label className="text-xs font-medium">Bắt đầu
            <input type="datetime-local" required value={scheduledStart} onChange={(event) => setScheduledStart(event.target.value)}
              className="mt-1 w-full rounded-lg border border-input-border bg-input-bg px-3 py-2 text-sm" />
          </label>
          <label className="text-xs font-medium">Kết thúc
            <input type="datetime-local" required value={scheduledEnd} min={scheduledStart} onChange={(event) => setScheduledEnd(event.target.value)}
              className="mt-1 w-full rounded-lg border border-input-border bg-input-bg px-3 py-2 text-sm" />
          </label>
          <label className="flex items-start gap-2 text-sm text-muted">
            <input className="mt-0.5" type="checkbox" checked={requireApproval} onChange={(event) => setRequireApproval(event.target.checked)} />
            Yêu cầu chủ phòng phê duyệt
          </label>
          {error && <p className="text-sm text-danger">{error}</p>}
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => setShowSchedule(false)} className="rounded-lg px-3 py-2 text-sm">Hủy</button>
            <button type="submit" disabled={creating} className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-accent-foreground disabled:opacity-60">
              {creating ? "Đang lưu…" : "Lưu lịch"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
