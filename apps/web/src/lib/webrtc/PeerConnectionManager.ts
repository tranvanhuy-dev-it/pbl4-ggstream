export interface PeerConnectionManagerOptions {
  iceServers: RTCIceServer[];
  localStream: MediaStream;
  onRemoteStream: (participantId: string, stream: MediaStream) => void;
  onIceCandidate: (participantId: string, candidate: RTCIceCandidateInit) => void;
  onConnectionStateChange?: (participantId: string, state: RTCPeerConnectionState) => void;
}

/**
 * Owns one RTCPeerConnection per remote participant
 * (Map<participantId, RTCPeerConnection>) so the rest of the app never
 * touches the WebRTC API directly. Built mesh-ready from the start — this
 * class already handles N peers, not just one — but Milestone 4 only
 * exercises it with a single remote peer; Milestone 6 is what actually
 * puts 3+ peers in the same room.
 */
export class PeerConnectionManager {
  private readonly peers = new Map<string, RTCPeerConnection>();
  private readonly pendingCandidates = new Map<string, RTCIceCandidateInit[]>();

  constructor(private readonly options: PeerConnectionManagerOptions) {}

  hasPeer(participantId: string): boolean {
    return this.peers.has(participantId);
  }

  connectedPeerIds(): string[] {
    return Array.from(this.peers.keys());
  }

  async createOffer(participantId: string): Promise<RTCSessionDescriptionInit> {
    const pc = this.getOrCreatePeer(participantId);
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    return offer;
  }

  async handleOffer(participantId: string, offer: RTCSessionDescriptionInit): Promise<RTCSessionDescriptionInit> {
    const pc = this.getOrCreatePeer(participantId);
    await pc.setRemoteDescription(offer);
    await this.flushPendingCandidates(participantId);
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    return answer;
  }

  async handleAnswer(participantId: string, answer: RTCSessionDescriptionInit): Promise<void> {
    const pc = this.peers.get(participantId);
    if (!pc) return;
    await pc.setRemoteDescription(answer);
    await this.flushPendingCandidates(participantId);
  }

  /**
   * ICE candidates can arrive before the remote description is set (the
   * offer/answer round trip and ICE gathering race each other over the
   * network) — addIceCandidate() throws InvalidStateError in that case, so
   * candidates that arrive too early are queued and flushed once the
   * remote description lands instead of being dropped.
   */
  async addIceCandidate(participantId: string, candidate: RTCIceCandidateInit): Promise<void> {
    const pc = this.peers.get(participantId);
    if (!pc || !pc.remoteDescription) {
      const queued = this.pendingCandidates.get(participantId) ?? [];
      queued.push(candidate);
      this.pendingCandidates.set(participantId, queued);
      return;
    }
    await pc.addIceCandidate(candidate);
  }

  private async flushPendingCandidates(participantId: string): Promise<void> {
    const queued = this.pendingCandidates.get(participantId);
    const pc = this.peers.get(participantId);
    if (!queued || !pc) return;
    this.pendingCandidates.delete(participantId);
    for (const candidate of queued) {
      try {
        await pc.addIceCandidate(candidate);
      } catch {
        // best-effort — a candidate that fails here just means one fewer
        // path tried, ICE still has others to attempt
      }
    }
  }

  replaceTrack(track: MediaStreamTrack): void {
    for (const pc of this.peers.values()) {
      const sender = pc.getSenders().find((s) => s.track?.kind === track.kind);
      sender?.replaceTrack(track);
    }
  }

  removePeer(participantId: string): void {
    this.peers.get(participantId)?.close();
    this.peers.delete(participantId);
    this.pendingCandidates.delete(participantId);
  }

  closeAll(): void {
    for (const pc of this.peers.values()) pc.close();
    this.peers.clear();
    this.pendingCandidates.clear();
  }

  private getOrCreatePeer(participantId: string): RTCPeerConnection {
    const existing = this.peers.get(participantId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({ iceServers: this.options.iceServers });
    this.options.localStream.getTracks().forEach((track) => pc.addTrack(track, this.options.localStream));

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.options.onIceCandidate(participantId, event.candidate.toJSON());
      }
    };
    pc.ontrack = (event) => {
      if (event.streams[0]) {
        this.options.onRemoteStream(participantId, event.streams[0]);
      }
    };
    if (this.options.onConnectionStateChange) {
      pc.onconnectionstatechange = () => this.options.onConnectionStateChange!(participantId, pc.connectionState);
    }

    this.peers.set(participantId, pc);
    return pc;
  }
}
