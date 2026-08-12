// Mirrors the backend's SignalingEnvelope / SignalingMessageType
// (apps/server .../signaling/domain). See docs/api/websocket-protocol.md.

export type SignalingMessageType =
  | "MEETING_JOIN"
  | "MEETING_LEAVE"
  | "PARTICIPANT_JOINED"
  | "PARTICIPANT_LEFT"
  | "WEBRTC_OFFER"
  | "WEBRTC_ANSWER"
  | "ICE_CANDIDATE"
  | "MIC_STATE_CHANGED"
  | "CAMERA_STATE_CHANGED"
  | "SCREEN_SHARE_STARTED"
  | "SCREEN_SHARE_STOPPED"
  | "CHAT_MESSAGE"
  | "PARTICIPANT_WAITING"
  | "PARTICIPANT_APPROVED"
  | "PARTICIPANT_REJECTED"
  | "HOST_MUTE_PARTICIPANT"
  | "HOST_REMOVE_PARTICIPANT"
  | "PING"
  | "PONG"
  | "ERROR";

export interface SignalingEnvelope<TPayload = unknown> {
  type: SignalingMessageType;
  requestId: string | null;
  meetingId: string | null;
  senderId: string | null;
  targetId: string | null;
  timestamp: number;
  payload: TPayload | null;
}

export interface ParticipantPresencePayload {
  userId: string;
  displayName: string;
  role: "HOST" | "CO_HOST" | "PARTICIPANT";
}
