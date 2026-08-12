# Luồng kết nối

Toàn bộ trình tự cho một cuộc gọi 1:1, theo đúng cách hệ thống hiện tại đang được triển khai (`useWebRtcPeers` + `PeerConnectionManager` ở frontend, cơ chế relay tổng quát trong `SignalingWebSocketHandler` ở backend — xem [signaling.md](./signaling.md)):

```text
Browser A (userId nhỏ hơn)          Java (chỉ relay)          Browser B
       │                                   │                          │
       │  cả hai đã kết nối tới /ws/meetings/{id}, biết userId của   │
       │  nhau thông qua PARTICIPANT_JOINED (snapshot/broadcast)     │
       │                                   │                          │
  createOffer()                            │                          │
  setLocalDescription(offer)               │                          │
       │──── WEBRTC_OFFER, targetId=B ────▶│──── relay theo targetId ─▶│
       │                                   │                          │
       │                                   │                          │  setRemoteDescription(offer)
       │                                   │                          │  createAnswer()
       │                                   │                          │  setLocalDescription(answer)
       │◀──── relay theo targetId ─────────│◀── WEBRTC_ANSWER ────────│
  setRemoteDescription(answer)             │                          │
       │                                   │                          │
       │  (trong suốt quá trình này, theo cả hai chiều, khi ICE agent│
       │   của mỗi browser phát hiện các candidate)                  │
       │──── ICE_CANDIDATE, targetId=B ───▶│──── relay ──────────────▶│
       │◀──── relay ────────────────────────│◀─── ICE_CANDIDATE ──────│
       │                                   │                          │
       │        ICE connectivity checks tìm ra một cặp candidate hoạt động
       │        DTLS handshake thương lượng các khóa SRTP
       │                                   │                          │
       ║═══════════════ SRTP media (audio/video RTP) ═════════════════║
       ║          truyền trực tiếp giữa các browser, không qua backend ║
```

Java chỉ nằm trên đường truyền đối với đúng bốn loại message (`WEBRTC_OFFER`/`WEBRTC_ANSWER`/`ICE_CANDIDATE`, được relay theo `targetId`) và không bao giờ can thiệp vào bất kỳ thứ gì sau đó — quá trình DTLS handshake và toàn bộ lưu lượng RTP/RTCP chỉ diễn ra trực tiếp từ browser này sang browser kia.

## Bên nào là "A"

Không có vai trò caller/callee cố định — `useWebRtcPeers` sẽ quyết định cho từng cặp theo cách xác định trước, bằng cách so sánh các `userId` (bên có `userId` nhỏ hơn theo thứ tự từ điển sẽ là bên khởi tạo). Xem [webrtc.md](./webrtc.md#offer-glare-and-who-initiates) để biết lý do.
