import { apiFetch } from "@/lib/api/client";
import { API_URL, WS_URL } from "@/lib/api/config";

export type LivestreamStatus = "LIVE" | "ENDED";

export interface LivestreamState {
  /** Only present in the response to startLivestream — the credential needed to call stopLivestream. Never returned to public viewers. */
  id: string | null;
  code: string;
  title: string | null;
  status: LivestreamStatus;
  hlsUrl: string | null;
}

function authHeaders(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

/** Livestream is its own feature — no meeting is created or involved, see docs/networking/livestream.md. */
export function startLivestream(token: string, title?: string): Promise<LivestreamState> {
  return apiFetch<LivestreamState>("/api/v1/live/start", {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ title }),
  });
}

export function stopLivestream(token: string, id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/live/${id}/stop`, {
    method: "POST",
    headers: authHeaders(token),
  });
}

/** Public — no auth. Backs the /watch/{code} viewer page. */
export function getLivestreamStatus(code: string): Promise<LivestreamState> {
  return apiFetch<LivestreamState>(`/api/v1/live/${encodeURIComponent(code)}/status`);
}

/** Full playable URL for the `hlsUrl` path segment the backend returns (e.g. "/livestreams/{id}/stream.m3u8"). */
export function resolveHlsUrl(hlsUrl: string): string {
  return `${API_URL}${hlsUrl}`;
}

export function livestreamIngestUrl(id: string, token: string): string {
  return `${WS_URL}/ws/live/${id}/ingest?token=${encodeURIComponent(token)}`;
}
