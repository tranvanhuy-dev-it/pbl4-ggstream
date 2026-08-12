"use client";

import { useEffect, useRef, useState } from "react";
import type { ChatMessage } from "@/features/chat/api/chatApi";

export function ChatPanel({
  messages,
  currentUserId,
  onSend,
  onClose,
}: {
  messages: ChatMessage[];
  currentUserId: string | null;
  onSend: (body: string) => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState("");
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages]);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = draft.trim();
    if (!trimmed) return;
    onSend(trimmed);
    setDraft("");
  }

  return (
    <aside className="flex h-full w-80 flex-col border-l border-card-border bg-card">
      <div className="flex items-center justify-between border-b border-card-border px-4 py-3">
        <h2 className="text-sm font-semibold">Tin nhắn trong cuộc họp</h2>
        <button onClick={onClose} aria-label="Đóng khung chat" className="text-muted hover:text-foreground">
          ✕
        </button>
      </div>

      <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-3">
        {messages.length === 0 && <p className="text-sm text-muted">Chưa có tin nhắn nào.</p>}
        <ul className="flex flex-col gap-3">
          {messages.map((m) => {
            const isMine = m.senderId === currentUserId;
            return (
              <li key={m.id} className={`flex flex-col ${isMine ? "items-end" : "items-start"}`}>
                {!isMine && <span className="text-xs font-medium text-muted">{m.senderDisplayName}</span>}
                <span
                  className={`mt-0.5 max-w-[85%] rounded-lg px-3 py-1.5 text-sm break-words ${
                    isMine ? "bg-accent text-accent-foreground" : "bg-input-bg"
                  }`}
                >
                  {m.body}
                </span>
              </li>
            );
          })}
        </ul>
      </div>

      <form onSubmit={handleSubmit} className="flex gap-2 border-t border-card-border p-3">
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Nhập tin nhắn"
          className="flex-1 rounded-lg border border-input-border bg-input-bg px-3 py-2 text-sm outline-none focus:border-accent focus:ring-2 focus:ring-accent/25"
        />
        <button
          type="submit"
          disabled={!draft.trim()}
          className="rounded-lg bg-accent px-3 py-2 text-sm font-medium text-accent-foreground disabled:opacity-50"
        >
          Gửi
        </button>
      </form>
    </aside>
  );
}
