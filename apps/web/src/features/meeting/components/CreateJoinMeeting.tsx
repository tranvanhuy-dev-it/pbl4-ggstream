"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/features/auth/context/AuthContext";
import * as meetingApi from "@/features/meeting/api/meetingApi";
import { ApiClientError } from "@/lib/api/client";

export function CreateJoinMeeting() {
  const { token } = useAuth();
  const router = useRouter();
  const [title, setTitle] = useState("Cuộc họp mới");
  const [requireApproval, setRequireApproval] = useState(false);
  const [joinCode, setJoinCode] = useState("");
  const [creating, setCreating] = useState(false);
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

  function handleJoin(event: React.FormEvent) {
    event.preventDefault();
    const code = joinCode.trim().toLowerCase();
    if (code) router.push(`/meet/${code}`);
  }

  return (
    <div className="flex w-full max-w-sm flex-col gap-6">
      <form onSubmit={handleCreate} className="flex flex-col gap-2">
        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Tên cuộc họp"
          className="rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
        />
        <label className="flex items-center gap-2 text-sm text-muted">
          <input type="checkbox" checked={requireApproval} onChange={(e) => setRequireApproval(e.target.checked)} />
          Yêu cầu chủ phòng phê duyệt trước khi vào
        </label>
        <button
          type="submit"
          disabled={creating}
          className="rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-accent-foreground transition-colors hover:bg-accent-hover disabled:opacity-60"
        >
          {creating ? "Đang tạo…" : "Tạo cuộc họp mới"}
        </button>
      </form>

      <div className="flex items-center gap-3 text-xs text-muted">
        <div className="h-px flex-1 bg-card-border" />
        hoặc
        <div className="h-px flex-1 bg-card-border" />
      </div>

      <form onSubmit={handleJoin} className="flex gap-2">
        <input
          value={joinCode}
          onChange={(e) => setJoinCode(e.target.value)}
          placeholder="Nhập mã cuộc họp"
          className="flex-1 rounded-lg border border-input-border bg-input-bg px-3.5 py-2.5 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
        />
        <button
          type="submit"
          disabled={!joinCode.trim()}
          className="rounded-lg border border-input-border px-4 py-2.5 text-sm font-medium transition-colors hover:bg-black/5 disabled:opacity-50 dark:hover:bg-white/10"
        >
          Tham gia
        </button>
      </form>

      {error && <p className="text-center text-sm text-danger">{error}</p>}
    </div>
  );
}
