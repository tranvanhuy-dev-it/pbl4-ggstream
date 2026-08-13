import { apiFetch } from "@/lib/api/client";
import { API_URL, WS_URL } from "@/lib/api/config";

export type LivestreamStatus = "LIVE" | "ENDED";

export interface LivestreamState {
  status: LivestreamStatus;
  hlsUrl: string | null;
}

function authHeaders(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

export function startLivestream(token: string, meetingId: string): Promise<LivestreamState> {
  return apiFetch<LivestreamState>(`/api/v1/meetings/${meetingId}/livestream/start`, {
    method: "POST",
    headers: authHeaders(token),
  });
}

export function stopLivestream(token: string, meetingId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/meetings/${meetingId}/livestream/stop`, {
    method: "POST",
    headers: authHeaders(token),
  });
}

/** Public — no auth, a viewer isn't a meeting participant. */
export function getLivestreamStatus(meetingId: string): Promise<LivestreamState> {
  return apiFetch<LivestreamState>(`/api/v1/meetings/${meetingId}/livestream`);
}

/** Public — used by /watch/{code}, which only ever has the join code, not the meeting's UUID. */
export function getLivestreamStatusByCode(code: string): Promise<LivestreamState> {
  return apiFetch<LivestreamState>(`/api/v1/meetings/code/${encodeURIComponent(code)}/livestream`);
}

/** Full playable URL for the `hlsUrl` path segment the backend returns (e.g. "/livestreams/{id}/stream.m3u8"). */
export function resolveHlsUrl(hlsUrl: string): string {
  return `${API_URL}${hlsUrl}`;
}

export function livestreamIngestUrl(meetingId: string, token: string): string {
  return `${WS_URL}/ws/meetings/${meetingId}/livestream-ingest?token=${encodeURIComponent(token)}`;
}
