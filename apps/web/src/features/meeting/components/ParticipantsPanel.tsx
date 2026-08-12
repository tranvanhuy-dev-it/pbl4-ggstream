"use client";

import { MicOffIcon, MicOnIcon, RemoveParticipantIcon } from "@/components/icons";

export interface WaitingRequest {
  userId: string;
  displayName: string;
}

export interface RosterEntry {
  userId: string;
  displayName: string;
  isSelf: boolean;
  isHost: boolean;
  micEnabled: boolean;
}

export function ParticipantsPanel({
  waiting,
  roster,
  isHost,
  onAdmit,
  onDeny,
  onMute,
  onRemove,
  onClose,
}: {
  waiting: WaitingRequest[];
  roster: RosterEntry[];
  isHost: boolean;
  onAdmit: (userId: string) => void;
  onDeny: (userId: string) => void;
  onMute: (userId: string) => void;
  onRemove: (userId: string) => void;
  onClose: () => void;
}) {
  return (
    <aside className="flex h-full w-80 flex-shrink-0 flex-col border-l border-card-border bg-card">
      <div className="flex items-center justify-between border-b border-card-border px-4 py-3">
        <h2 className="text-sm font-semibold">Thành viên ({roster.length})</h2>
        <button onClick={onClose} aria-label="Đóng danh sách thành viên" className="text-muted hover:text-foreground">
          ✕
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3">
        {isHost && waiting.length > 0 && (
          <div className="mb-4 flex flex-col gap-2 rounded-lg border border-accent/30 bg-accent/5 p-3">
            <h3 className="text-sm font-medium">Yêu cầu tham gia ({waiting.length})</h3>
            <ul className="flex flex-col gap-2">
              {waiting.map((w) => (
                <li key={w.userId} className="flex items-center justify-between gap-3">
                  <span className="text-sm">{w.displayName}</span>
                  <div className="flex gap-2">
                    <button
                      onClick={() => onAdmit(w.userId)}
                      className="rounded-md bg-accent px-2.5 py-1 text-xs font-medium text-accent-foreground hover:bg-accent-hover"
                    >
                      Chấp nhận
                    </button>
                    <button
                      onClick={() => onDeny(w.userId)}
                      className="rounded-md border border-input-border px-2.5 py-1 text-xs font-medium hover:bg-black/5 dark:hover:bg-white/10"
                    >
                      Từ chối
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        )}

        <ul className="flex flex-col gap-1">
          {roster.map((p) => (
            <li key={p.userId} className="flex items-center justify-between gap-2 rounded-md px-2 py-1.5">
              <span className="text-sm">
                {p.displayName}
                {p.isSelf && " (bạn)"}
                {p.isHost && <span className="ml-1.5 rounded bg-accent/15 px-1.5 py-0.5 text-[10px] font-medium text-accent">Chủ phòng</span>}
              </span>
              {isHost && !p.isSelf && (
                <div className="flex gap-1">
                  <button
                    onClick={() => onMute(p.userId)}
                    disabled={!p.micEnabled}
                    aria-label={p.micEnabled ? `Tắt micro của ${p.displayName}` : `${p.displayName} đã tắt micro`}
                    title={p.micEnabled ? "Tắt micro người này" : "Người này đã tắt micro"}
                    className="flex h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white hover:bg-black/80 disabled:cursor-default disabled:text-white/65 [&>svg]:h-4 [&>svg]:w-4"
                  >
                    {p.micEnabled ? MicOnIcon : MicOffIcon}
                  </button>
                  <button
                    onClick={() => onRemove(p.userId)}
                    aria-label={`Mời ${p.displayName} ra khỏi cuộc họp`}
                    title="Mời người này ra khỏi cuộc họp"
                    className="flex h-7 w-7 items-center justify-center rounded-full bg-black/60 text-white hover:bg-danger/90 [&>svg]:h-4 [&>svg]:w-4"
                  >
                    {RemoveParticipantIcon}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
}
