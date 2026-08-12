import { apiFetch } from "@/lib/api/client";

export type MeetingStatus = "CREATED" | "WAITING" | "ACTIVE" | "ENDED";
export type MeetingAccessType = "PUBLIC" | "INVITED" | "APPROVAL_REQUIRED";
export type ParticipantRole = "HOST" | "CO_HOST" | "PARTICIPANT";

export interface Meeting {
  id: string;
  code: string;
  title: string;
  hostId: string;
  hostDisplayName: string;
  status: MeetingStatus;
  accessType: MeetingAccessType;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
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

export function createMeeting(token: string, title: string, accessType?: MeetingAccessType): Promise<Meeting> {
  return apiFetch<Meeting>("/api/v1/meetings", {
    method: "POST",
    headers: authHeaders(token),
    body: JSON.stringify({ title, accessType }),
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
