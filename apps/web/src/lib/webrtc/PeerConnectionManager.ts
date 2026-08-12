export interface PeerConnectionManagerOptions {
  iceServers: RTCIceServer[];
  localStream: MediaStream;
  onRemoteStream: (participantId: string, stream: MediaStream) => void;
  onRemoteScreenStream?: (participantId: string, stream: MediaStream | null) => void;
  onIceCandidate: (participantId: string, candidate: RTCIceCandidateInit) => void;
  /** Called for every offer that needs sending over signaling — the initial one and any later renegotiation (e.g. starting/stopping screen share). */
  onOffer: (participantId: string, offer: RTCSessionDescriptionInit) => void;
  onConnectionStateChange?: (participantId: string, state: RTCPeerConnectionState) => void;
}

interface PeerEntry {
  pc: RTCPeerConnection;
  /**
   * Guards onnegotiationneeded: the very first offer/answer for a peer is
   * always driven explicitly (connect()/handleOffer()), not by this event —
   * adding the initial camera/mic tracks during peer creation can itself
   * trigger negotiationneeded before that explicit flow has run. Only once
   * the first offer/answer round trip has completed do later track changes
   * (screen share) get to trigger renegotiation automatically.
   */
  negotiationReady: boolean;
  screenSender: RTCRtpSender | null;
  primaryRemoteStreamId: string | null;
  /**
   * True only for the side that called connect() first for this pair — the
   * same "smaller userId offers" glare-avoidance rule that picked the
   * original offerer also governs who's allowed to send an ICE-restart
   * offer later, so a recovering connection doesn't get two competing
   * restart offers from both sides at once.
   */
  isInitiator: boolean;
}

/**
 * Owns one RTCPeerConnection per remote participant
 * (Map<participantId, RTCPeerConnection>) so the rest of the app never
 * touches the WebRTC API directly. Built mesh-ready from the start — this
 * class already handles N peers, not just one.
 */
export class PeerConnectionManager {
  private readonly peers = new Map<string, PeerEntry>();
  private readonly pendingCandidates = new Map<string, RTCIceCandidateInit[]>();
  private screenStream: MediaStream | null = null;

  constructor(private readonly options: PeerConnectionManagerOptions) {}

  hasPeer(participantId: string): boolean {
    return this.peers.has(participantId);
  }

  connectedPeerIds(): string[] {
    return Array.from(this.peers.keys());
  }

  isScreenSharing(): boolean {
    return this.screenStream !== null;
  }

  /** Initiator side of a new connection — ensures the peer exists (creating it adds local tracks) and sends the initial offer. */
  async connect(participantId: string): Promise<void> {
    const entry = this.getOrCreatePeer(participantId);
    entry.isInitiator = true;
    const offer = await entry.pc.createOffer();
    await entry.pc.setLocalDescription(offer);
    entry.negotiationReady = true;
    this.options.onOffer(participantId, offer);
  }

  /**
   * Re-negotiates a peer's connection with a fresh ICE gathering pass —
   * recovers a connection whose underlying network path changed (wifi
   * roam, reconnect after a drop) without tearing down and recreating the
   * whole RTCPeerConnection (which would also lose codec/DTLS state).
   * Only the original initiator side sends the restart offer; the other
   * side just answers whatever offer arrives, same as initial connect.
   */
  async restartIce(participantId: string): Promise<void> {
    const entry = this.peers.get(participantId);
    if (!entry || !entry.isInitiator) return;
    try {
      const offer = await entry.pc.createOffer({ iceRestart: true });
      await entry.pc.setLocalDescription(offer);
      this.options.onOffer(participantId, offer);
    } catch {
      // best-effort — if the connection is still broken, connectionstatechange fires again
    }
  }

  async handleOffer(participantId: string, offer: RTCSessionDescriptionInit): Promise<RTCSessionDescriptionInit> {
    const entry = this.getOrCreatePeer(participantId);
    await entry.pc.setRemoteDescription(offer);
    await this.flushPendingCandidates(participantId);
    const answer = await entry.pc.createAnswer();
    await entry.pc.setLocalDescription(answer);
    entry.negotiationReady = true;
    return answer;
  }

  async handleAnswer(participantId: string, answer: RTCSessionDescriptionInit): Promise<void> {
    const entry = this.peers.get(participantId);
    if (!entry) return;
    await entry.pc.setRemoteDescription(answer);
    await this.flushPendingCandidates(participantId);
    entry.negotiationReady = true;
  }

  /**
   * ICE candidates can arrive before the remote description is set (the
   * offer/answer round trip and ICE gathering race each other over the
   * network) — addIceCandidate() throws InvalidStateError in that case, so
   * candidates that arrive too early are queued and flushed once the
   * remote description lands instead of being dropped.
   */
  async addIceCandidate(participantId: string, candidate: RTCIceCandidateInit): Promise<void> {
    const entry = this.peers.get(participantId);
    if (!entry || !entry.pc.remoteDescription) {
      const queued = this.pendingCandidates.get(participantId) ?? [];
      queued.push(candidate);
      this.pendingCandidates.set(participantId, queued);
      return;
    }
    await entry.pc.addIceCandidate(candidate);
  }

  replaceTrack(track: MediaStreamTrack): void {
    for (const entry of this.peers.values()) {
      const sender = entry.pc.getSenders().find((s) => s.track?.kind === track.kind && s !== entry.screenSender);
      sender?.replaceTrack(track);
    }
  }

  /** Adds the screen track to every current peer (and any peer connecting later, via getOrCreatePeer) — each addTrack triggers automatic renegotiation via onnegotiationneeded. */
  async startScreenShare(stream: MediaStream): Promise<void> {
    this.screenStream = stream;
    const track = stream.getVideoTracks()[0];
    if (!track) return;

    for (const [participantId, entry] of this.peers) {
      if (entry.screenSender) continue;
      entry.screenSender = entry.pc.addTrack(track, stream);
      void participantId; // renegotiation is handled by onnegotiationneeded, nothing else to do per-peer here
    }
  }

  stopScreenShare(): void {
    for (const entry of this.peers.values()) {
      if (entry.screenSender) {
        entry.pc.removeTrack(entry.screenSender);
        entry.screenSender = null;
      }
    }
    this.screenStream?.getTracks().forEach((t) => t.stop());
    this.screenStream = null;
  }

  removePeer(participantId: string): void {
    this.peers.get(participantId)?.pc.close();
    this.peers.delete(participantId);
    this.pendingCandidates.delete(participantId);
  }

  closeAll(): void {
    for (const entry of this.peers.values()) entry.pc.close();
    this.peers.clear();
    this.pendingCandidates.clear();
    this.screenStream?.getTracks().forEach((t) => t.stop());
    this.screenStream = null;
  }

  private async flushPendingCandidates(participantId: string): Promise<void> {
    const queued = this.pendingCandidates.get(participantId);
    const entry = this.peers.get(participantId);
    if (!queued || !entry) return;
    this.pendingCandidates.delete(participantId);
    for (const candidate of queued) {
      try {
        await entry.pc.addIceCandidate(candidate);
      } catch {
        // best-effort — a candidate that fails here just means one fewer
        // path tried, ICE still has others to attempt
      }
    }
  }

  private getOrCreatePeer(participantId: string): PeerEntry {
    const existing = this.peers.get(participantId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({ iceServers: this.options.iceServers });
    const entry: PeerEntry = { pc, negotiationReady: false, screenSender: null, primaryRemoteStreamId: null, isInitiator: false };

    this.options.localStream.getTracks().forEach((track) => pc.addTrack(track, this.options.localStream));
    if (this.screenStream) {
      const screenTrack = this.screenStream.getVideoTracks()[0];
      if (screenTrack) {
        entry.screenSender = pc.addTrack(screenTrack, this.screenStream);
      }
    }

    pc.onnegotiationneeded = async () => {
      if (!entry.negotiationReady) return;
      try {
        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);
        this.options.onOffer(participantId, offer);
      } catch {
        // renegotiation attempt failed — next track change will retry via the same event
      }
    };

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.options.onIceCandidate(participantId, event.candidate.toJSON());
      }
    };

    pc.ontrack = (event) => {
      const stream = event.streams[0];
      if (!stream) return;

      if (entry.primaryRemoteStreamId === null) {
        entry.primaryRemoteStreamId = stream.id;
        this.options.onRemoteStream(participantId, stream);
      } else if (stream.id !== entry.primaryRemoteStreamId) {
        this.options.onRemoteScreenStream?.(participantId, stream);
        stream.getVideoTracks()[0]?.addEventListener("ended", () => {
          this.options.onRemoteScreenStream?.(participantId, null);
        });
      }
    };

    pc.onconnectionstatechange = () => {
      this.options.onConnectionStateChange?.(participantId, pc.connectionState);
      // "disconnected" is often transient (a brief network hiccup that
      // resolves itself within seconds) and restarting ICE for every one
      // would be overly aggressive; "failed" means the browser has already
      // given up, so that's the point where a restart is worth attempting.
      if (pc.connectionState === "failed") {
        void this.restartIce(participantId);
      }
    };

    this.peers.set(participantId, entry);
    return entry;
  }
}
