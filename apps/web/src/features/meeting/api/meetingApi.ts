import { apiFetch } from "@/lib/api/client";

export type MeetingStatus = "CREATED" | "SCHEDULED" | "WAITING" | "ACTIVE" | "ENDED" | "CANCELLED";
export type MeetingAccessType = "PUBLIC" | "INVITED" | "APPROVAL_REQUIRED";
export type ParticipantRole = "HOST" | "CO_HOST" | "PARTICIPANT";
/** MESH: every participant connects directly to every other (Milestone 4/6). SFU: everyone connects once to a LiveKit SFU (Milestone 11). Fixed at creation, like accessType. */
export type MediaMode = "MESH" | "SFU";

export interface Meeting {
  id: string;
  code: string;
  title: string;
  hostId: string;
  hostDisplayName: string;
  status: MeetingStatus;
  accessType: MeetingAccessType;
  mediaMode: MediaMode;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
  scheduledStartAt: string | null;
  scheduledEndAt: string | null;
  timezone: string | null;
}

export interface Participant {
  id: string;
  userId: string | null;
  displayName: string;
  role: ParticipantRole;
  joinedAt: string;
  leftAt: string | null;
}

function authHeaders(token: string): HeadersInit {
  return { Authorization: `Bearer ${token}` };
}

export function createMeeting(
  token: string,
  title: string,
  accessType?: MeetingAccessType,
  schedule?: { scheduledStartAt: string; scheduledEndAt: string; timezone: string },
  mediaMode?: MediaMode,
): Promise<Meeting> {
  return apiFetch<Meeting>("/api/v1/meetings", {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ title, accessType, ...schedule, mediaMode }),
  });
}

export function listMyMeetings(token: string, from: string, to: string): Promise<Meeting[]> {
  const query = new URLSearchParams({ from, to });
  return apiFetch<Meeting[]>(`/api/v1/meetings/mine?${query}`, { headers: authHeaders(token) });
}

export function startMeeting(token: string, meetingId: string): Promise<Meeting> {
  return apiFetch<Meeting>(`/api/v1/meetings/${meetingId}/start`, {
    method: "POST", headers: authHeaders(token),
  });
}

export function cancelMeeting(token: string, meetingId: string): Promise<Meeting> {
  return apiFetch<Meeting>(`/api/v1/meetings/${meetingId}/cancel`, {
    method: "POST", headers: authHeaders(token),
  });
}

export function getMeetingByCode(token: string, code: string): Promise<Meeting> {
  return apiFetch<Meeting>(`/api/v1/meetings/code/${encodeURIComponent(code)}`, {
    headers: authHeaders(token),
  });
}

export function joinMeeting(token: string, meetingId: string): Promise<Participant> {
  return apiFetch<Participant>(`/api/v1/meetings/${meetingId}/join`, {
    method: "POST",
    headers: authHeaders(token),
  });
}

export function leaveMeeting(token: string, meetingId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/meetings/${meetingId}/leave`, {
    method: "POST",
    headers: authHeaders(token),
  });
}

export function endMeeting(token: string, meetingId: string): Promise<Meeting> {
  return apiFetch<Meeting>(`/api/v1/meetings/${meetingId}/end`, {
    method: "POST",
    headers: authHeaders(token),
  });
}

export function listParticipants(token: string, meetingId: string): Promise<Participant[]> {
  return apiFetch<Participant[]>(`/api/v1/meetings/${meetingId}/participants`, {
    headers: authHeaders(token),
  });
}

export interface SfuToken {
  url: string;
  token: string;
}

/** SFU equivalent of fetchIceServers — issues a short-lived, per-user LiveKit access token. Only valid for mediaMode "SFU" meetings. */
export function fetchSfuToken(token: string, meetingId: string): Promise<SfuToken> {
  return apiFetch<SfuToken>(`/api/v1/meetings/${meetingId}/sfu-token`, {
    method: "POST",
    headers: authHeaders(token),
  });
}
