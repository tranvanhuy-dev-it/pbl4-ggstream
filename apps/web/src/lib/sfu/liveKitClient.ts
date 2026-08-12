import { Room, RoomEvent, Track } from "livekit-client";
import type { RemoteParticipant, RemoteTrack, RemoteTrackPublication } from "livekit-client";

export interface LiveKitClientOptions {
  /** Fired whenever a participant's camera/mic stream gains a track — the caller should (re)store this exact MediaStream reference; adding a second track later mutates the same object rather than replacing it. */
  onRemoteStream: (participantId: string, stream: MediaStream) => void;
  onRemoteStreamRemoved: (participantId: string) => void;
  onRemoteScreenStream: (participantId: string, stream: MediaStream | null) => void;
}

/**
 * Thin wrapper around LiveKit's `Room` for SFU-mode meetings — the
 * counterpart to `lib/webrtc/PeerConnectionManager.ts` for Mesh mode.
 * Translates LiveKit's participant/track events into the same
 * `Map<participantId, MediaStream>` shape the UI already consumes, so
 * `MeetingRoomView` doesn't need transport-specific rendering code.
 *
 * Unlike Mesh, there's exactly one `RTCPeerConnection` here (LiveKit's own,
 * managed internally by `Room`) — every participant's audio/video arrives
 * over that single connection, keyed by `participant.identity` (the userId
 * the backend embedded in the access token, see IssueSfuTokenUseCase).
 */
export class LiveKitClient {
  private readonly room = new Room();
  private readonly remoteStreamsByParticipant = new Map<string, MediaStream>();

  constructor(private readonly options: LiveKitClientOptions) {
    this.room.on(RoomEvent.TrackSubscribed, this.handleTrackSubscribed);
    this.room.on(RoomEvent.TrackUnsubscribed, this.handleTrackUnsubscribed);
    this.room.on(RoomEvent.ParticipantDisconnected, this.handleParticipantDisconnected);
  }

  async connect(url: string, token: string): Promise<void> {
    await this.room.connect(url, token);
  }

  /** Publishes every track of the local camera/mic stream (already captured by useLocalMedia) — LiveKit doesn't get to call getUserMedia itself, so there's no second permission prompt. */
  async publishLocalStream(stream: MediaStream): Promise<void> {
    for (const track of stream.getTracks()) {
      await this.room.localParticipant.publishTrack(track);
    }
  }

  async startScreenShare(stream: MediaStream): Promise<void> {
    const track = stream.getVideoTracks()[0];
    if (!track) return;
    await this.room.localParticipant.publishTrack(track, { source: Track.Source.ScreenShare });
  }

  async stopScreenShare(): Promise<void> {
    for (const publication of this.room.localParticipant.trackPublications.values()) {
      if (publication.source === Track.Source.ScreenShare && publication.track) {
        await this.room.localParticipant.unpublishTrack(publication.track.mediaStreamTrack, true);
      }
    }
  }

  disconnect(): void {
    this.room.disconnect();
  }

  private handleTrackSubscribed = (track: RemoteTrack, publication: RemoteTrackPublication, participant: RemoteParticipant) => {
    const participantId = participant.identity;
    if (publication.source === Track.Source.ScreenShare) {
      this.options.onRemoteScreenStream(participantId, new MediaStream([track.mediaStreamTrack]));
      return;
    }
    let stream = this.remoteStreamsByParticipant.get(participantId);
    if (!stream) {
      stream = new MediaStream();
      this.remoteStreamsByParticipant.set(participantId, stream);
    }
    stream.addTrack(track.mediaStreamTrack);
    this.options.onRemoteStream(participantId, stream);
  };

  private handleTrackUnsubscribed = (track: RemoteTrack, publication: RemoteTrackPublication, participant: RemoteParticipant) => {
    const participantId = participant.identity;
    if (publication.source === Track.Source.ScreenShare) {
      this.options.onRemoteScreenStream(participantId, null);
      return;
    }
    this.remoteStreamsByParticipant.get(participantId)?.removeTrack(track.mediaStreamTrack);
  };

  private handleParticipantDisconnected = (participant: RemoteParticipant) => {
    const participantId = participant.identity;
    this.remoteStreamsByParticipant.delete(participantId);
    this.options.onRemoteStreamRemoved(participantId);
    this.options.onRemoteScreenStream(participantId, null);
  };
}
