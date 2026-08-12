import { apiFetch } from "@/lib/api/client";

interface IceServerDto {
  urls: string[];
  username: string | null;
  credential: string | null;
}

interface IceServersResponseDto {
  iceServers: IceServerDto[];
}

const FALLBACK_STUN_URL = process.env.NEXT_PUBLIC_STUN_URL ?? "stun:stun.l.google.com:19302";

/**
 * Fetches STUN + (if configured) short-lived TURN credentials from the
 * backend rather than hardcoding them client-side — the TURN shared secret
 * must never reach the browser, only the time-boxed credential the backend
 * derives from it (see GetIceServersUseCase). Falls back to a bare public
 * STUN entry if the backend call fails, so a transient API hiccup degrades
 * a call's NAT-traversal options rather than blocking it outright.
 */
export async function fetchIceServers(token: string): Promise<RTCIceServer[]> {
  try {
    const response = await apiFetch<IceServersResponseDto>("/api/v1/rtc/ice-servers", {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.iceServers.map((server) => ({
      urls: server.urls,
      username: server.username ?? undefined,
      credential: server.credential ?? undefined,
    }));
  } catch {
    return [{ urls: [FALLBACK_STUN_URL] }];
  }
}
