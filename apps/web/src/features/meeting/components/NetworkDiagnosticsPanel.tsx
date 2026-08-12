import type { WebRtcPeerStats } from "@/features/meeting/hooks/useWebRtcStats";

const candidateLabels: Record<string, string> = {
  host: "Trực tiếp (host)",
  srflx: "Qua STUN (srflx)",
  prflx: "Peer reflexive",
  relay: "Qua TURN (relay)",
};

function metric(value: number | null, suffix: string, digits = 0) {
  return value === null ? "—" : `${value.toFixed(digits)} ${suffix}`;
}

function quality(stats: WebRtcPeerStats) {
  if ((stats.packetLossPercent ?? 0) > 5 || (stats.rttMs ?? 0) > 300) return { label: "Kém", color: "bg-red-500" };
  if ((stats.packetLossPercent ?? 0) > 2 || (stats.rttMs ?? 0) > 150) return { label: "Trung bình", color: "bg-yellow-400" };
  return { label: "Tốt", color: "bg-green-500" };
}

export function NetworkDiagnosticsPanel({
  stats,
  participantNames,
  onClose,
}: {
  stats: Map<string, WebRtcPeerStats>;
  participantNames: Map<string, string>;
  onClose: () => void;
}) {
  return (
    <aside className="flex h-full w-80 flex-shrink-0 flex-col border-l border-card-border bg-card">
      <div className="flex items-center justify-between border-b border-card-border px-4 py-3">
        <div>
          <h2 className="text-sm font-semibold">Chẩn đoán mạng</h2>
          <p className="text-[11px] text-muted">Cập nhật mỗi 2 giây</p>
        </div>
        <button onClick={onClose} aria-label="Đóng chẩn đoán mạng" className="text-xl leading-none text-muted hover:text-foreground">×</button>
      </div>

      <div className="flex-1 overflow-y-auto p-3">
        {stats.size === 0 && (
          <div className="rounded-xl border border-card-border p-4 text-center">
            <p className="text-sm font-medium">Chưa có kết nối peer</p>
            <p className="mt-1 text-xs text-muted">Số liệu xuất hiện khi có người khác trong cuộc họp.</p>
          </div>
        )}
        <div className="flex flex-col gap-3">
          {Array.from(stats, ([participantId, peerStats]) => {
            const connectionQuality = quality(peerStats);
            return (
              <section key={participantId} className="rounded-xl border border-card-border bg-background/40 p-3">
                <div className="mb-3 flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-semibold">{participantNames.get(participantId) ?? "Người tham gia"}</h3>
                    <p className="truncate font-mono text-[10px] text-muted">{participantId}</p>
                  </div>
                  <span className="inline-flex items-center gap-1.5 text-xs">
                    <span className={`h-2 w-2 rounded-full ${connectionQuality.color}`} />{connectionQuality.label}
                  </span>
                </div>
                <dl className="grid grid-cols-2 gap-x-3 gap-y-2 text-xs">
                  <div><dt className="text-muted">Độ trễ RTT</dt><dd className="mt-0.5 font-semibold tabular-nums">{metric(peerStats.rttMs, "ms")}</dd></div>
                  <div><dt className="text-muted">Độ dao động</dt><dd className="mt-0.5 font-semibold tabular-nums">{metric(peerStats.jitterMs, "ms")}</dd></div>
                  <div><dt className="text-muted">Mất gói</dt><dd className="mt-0.5 font-semibold tabular-nums">{metric(peerStats.packetLossPercent, "%", 2)}</dd></div>
                  <div><dt className="text-muted">Tốc độ bit nhận</dt><dd className="mt-0.5 font-semibold tabular-nums">{metric(peerStats.bitrateKbps, "kbps")}</dd></div>
                  <div><dt className="text-muted">Loại kết nối</dt><dd className="mt-0.5 font-semibold">{peerStats.candidateType ? candidateLabels[peerStats.candidateType] ?? peerStats.candidateType : "—"}</dd></div>
                  <div><dt className="text-muted">Giao thức</dt><dd className="mt-0.5 font-semibold uppercase">{peerStats.protocol ?? "—"}</dd></div>
                  <div><dt className="text-muted">Codec</dt><dd className="mt-0.5 font-semibold">{peerStats.codec ?? "—"}</dd></div>
                  <div><dt className="text-muted">Khung hình bị bỏ</dt><dd className="mt-0.5 font-semibold tabular-nums">{peerStats.framesDropped ?? "—"}</dd></div>
                </dl>
              </section>
            );
          })}
        </div>
      </div>
      <p className="border-t border-card-border px-4 py-3 text-[10px] leading-relaxed text-muted">
        Số liệu lấy trực tiếp từ RTCPeerConnection.getStats() trên thiết bị này.
      </p>
    </aside>
  );
}
