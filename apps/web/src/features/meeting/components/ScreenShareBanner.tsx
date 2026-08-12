export function ScreenShareBanner({ onStop }: { onStop: () => void }) {
  return (
    <div className="flex flex-shrink-0 items-center justify-between gap-3 rounded-lg border border-card-border bg-card px-3 py-2 text-sm shadow-sm">
      <span className="flex items-center gap-2 font-medium">
        <span className="h-2 w-2 rounded-full bg-green-500" />
        Bạn đang chia sẻ màn hình
      </span>
      <button onClick={onStop} className="rounded-md bg-danger px-3 py-1.5 text-xs font-semibold text-white hover:opacity-90">
        Dừng chia sẻ
      </button>
    </div>
  );
}
