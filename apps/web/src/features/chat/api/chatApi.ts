import { apiFetch } from "@/lib/api/client";

export interface ChatMessage {
  id: string;
  meetingId: string;
  senderId: string;
  senderDisplayName: string;
  body: string;
  createdAt: string;
}

export function fetchChatHistory(token: string, meetingId: string): Promise<ChatMessage[]> {
  return apiFetch<ChatMessage[]>(`/api/v1/meetings/${meetingId}/messages`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}
