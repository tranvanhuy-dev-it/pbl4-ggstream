import { ThemeToggle } from "@/components/ThemeToggle";

type SocketStatus = "connecting" | "open" | "reconnecting" | "closed";

const statusLabel: Record<SocketStatus, string> = {
  open: "Đã kết nối",
  connecting: "Đang kết nối…",
  reconnecting: "Đang kết nối lại…",
  closed: "Mất kết nối",
};

export function MeetingTopbar({
  title,
  code,
  socketStatus,
}: {
  title: string;
  code: string;
  socketStatus?: SocketStatus;
}) {
  async function copyMeetingLink() {
    await navigator.clipboard.writeText(window.location.href.replace(/\/room$/, ""));
  }

  return (
    <header className="z-30 flex h-16 flex-shrink-0 items-center justify-between gap-4 border-b border-card-border bg-background/95 px-4 backdrop-blur md:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg bg-accent text-accent-foreground" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 10l4.5-2.3A1 1 0 0121 8.6v6.8a1 1 0 01-1.5.9L15 14" /><rect x="3" y="6" width="12" height="12" rx="2" />
          </svg>
        </div>
        <div className="min-w-0">
          <h1 className="truncate text-sm font-semibold md:text-base">{title}</h1>
          <div className="flex items-center gap-2 text-xs text-muted">
            <span className="hidden sm:inline">Mã phòng:</span>
            <span className="font-mono">{code}</span>
            <button onClick={copyMeetingLink} className="rounded px-1.5 py-0.5 hover:bg-black/5 dark:hover:bg-white/10" title="Sao chép liên kết tham gia">
              Sao chép
            </button>
          </div>
        </div>
      </div>
      <div className="flex flex-shrink-0 items-center gap-3">
        {socketStatus && (
          <span className="hidden items-center gap-2 text-xs text-muted sm:inline-flex">
            <span className={`h-2 w-2 rounded-full ${socketStatus === "open" ? "bg-green-500" : socketStatus === "closed" ? "bg-red-500" : "animate-pulse bg-yellow-400"}`} />
            {statusLabel[socketStatus]}
          </span>
        )}
        <ThemeToggle />
      </div>
    </header>
  );
}
