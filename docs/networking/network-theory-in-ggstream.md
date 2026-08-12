# Các lý thuyết Mạng máy tính được áp dụng trong GGStream

Tài liệu này tổng hợp kiến thức Mạng máy tính được sử dụng trực tiếp trong
GGStream, phục vụ báo cáo và bảo vệ đồ án.

## 1. Control plane và media plane

GGStream tách lưu lượng thành hai mặt phẳng:

    Control plane: Browser -- HTTPS/WebSocket -- Spring Boot
    Media plane:   Browser A == WebRTC/SRTP == Browser B

- Control plane xử lý xác thực, phòng họp, participant, chat, SDP và ICE.
- Media plane truyền audio, video và nội dung chia sẻ màn hình.
- Backend không nhận và chuyển tiếp media trong mô hình Mesh hiện tại.

Việc tách hai đường truyền giúp giảm tải backend và giảm độ trễ media. Chúng có
failure mode riêng: WebSocket có thể hoạt động nhưng đường media vẫn bị hỏng,
hoặc ngược lại.

## 2. HTTP và WebSocket

REST API phù hợp với mô hình request-response và được dùng để đăng nhập, tạo
phòng, tham gia, rời phòng, kết thúc phòng và lấy cấu hình ICE server.

WebSocket là kết nối TCP lâu dài, full-duplex. Nó mang các message OFFER,
ANSWER, ICE candidate, chat, trạng thái media, PING và PONG. TCP bảo đảm giao
hàng tin cậy và đúng thứ tự nên phù hợp với signaling.

Media không đi qua WebSocket vì retransmission và head-of-line blocking của TCP
có thể làm tăng độ trễ. WebRTC ưu tiên UDP cho dữ liệu thời gian thực.

## 3. Signaling

WebRTC không quy định cách hai peer tìm nhau. GGStream dùng WebSocket làm
signaling channel. Message điểm-điểm chứa targetId để backend relay đến đúng
participant; sự kiện cấp phòng được broadcast.

    Browser A -- WEBRTC_OFFER, targetId=B --> Spring Boot --> Browser B

Backend xác định sender từ WebSocket session, không tin senderId do client tự
gửi. Backend không cần phân tích SDP và không tham gia đường RTP/RTCP.

## 4. SDP Offer/Answer

SDP mô tả audio/video track, codec, hướng truyền, ICE và DTLS. Nó chỉ mô tả
phiên, không mang dữ liệu media.

    A: createOffer và setLocalDescription
    A ---------------- SDP offer ----------------> B
    B: setRemoteDescription, createAnswer
    B <--------------- SDP answer ---------------- A
    A: setRemoteDescription

GGStream chọn một peer làm offerer để tránh offer glare, tức hai bên cùng tạo
offer và gây xung đột signaling state.

## 5. NAT, ICE, STUN và TURN

Thiết bị thường có địa chỉ private và được router NAT ánh xạ sang địa chỉ
public. Peer ngoài Internet không thể kết nối trực tiếp tới địa chỉ private.

ICE thu thập và kiểm tra các candidate:

| Candidate | Ý nghĩa |
|---|---|
| host | Địa chỉ local hoặc LAN |
| srflx | Địa chỉ public do STUN phát hiện |
| prflx | Địa chỉ peer-reflexive phát hiện khi kiểm tra |
| relay | Địa chỉ do TURN relay cấp |

STUN cho client biết IP và port public mà server nhìn thấy. STUN không chuyển
tiếp media.

Với symmetric NAT hoặc firewall nghiêm ngặt, kết nối trực tiếp có thể thất bại.
Khi đó media đi qua TURN:

    Browser A --> TURN relay --> Browser B

TURN tăng khả năng kết nối nhưng tăng độ trễ, băng thông và chi phí server.
GGStream cấp TURN credential ngắn hạn từ backend, không đưa shared secret vào
frontend.

## 6. UDP, RTP, RTCP, DTLS và SRTP

WebRTC ưu tiên UDP vì packet đến quá trễ thường không còn giá trị trong cuộc
gọi thời gian thực. Chấp nhận mất một số packet tốt hơn chờ TCP truyền lại.

RTP mang audio/video:

- sequence number giúp phát hiện mất hoặc đảo thứ tự packet;
- timestamp giúp phát media đúng thời điểm;
- payload type xác định định dạng media.

RTCP cung cấp phản hồi chất lượng như packet loss, jitter và RTT. Trình duyệt
tổng hợp các số liệu này trong RTCPeerConnection.getStats().

Sau khi ICE chọn candidate pair, DTLS handshake tạo khóa. RTP sau đó được mã
hóa thành SRTP:

    ICE hoàn tất --> DTLS handshake --> sinh khóa --> SRTP media

Signaling backend không nắm khóa và không đọc nội dung media.

## 7. Các chỉ số chất lượng mạng

Panel chẩn đoán lấy mẫu getStats() mỗi 2 giây cho từng peer connection.

### RTT

RTT là thời gian tín hiệu đi đến peer và phản hồi quay về:

    RTT = thời điểm nhận phản hồi - thời điểm gửi

- dưới 150 ms: tốt;
- 150 đến 300 ms: có thể cảm nhận độ trễ;
- trên 300 ms: hội thoại dễ chồng tiếng.

RTT không phải one-way delay. Khi đường truyền tương đối đối xứng, one-way
delay có thể ước lượng bằng RTT chia hai.

### Jitter

Jitter là độ biến thiên delay giữa các packet. Jitter buffer giúp phát media
đều hơn nhưng buffer càng lớn thì độ trễ càng tăng.

    20, 21, 19, 20 ms  => jitter thấp
    20, 80, 25, 100 ms => jitter cao

### Packet loss

    Packet loss (%) =
    packetsLost / (packetsReceived + packetsLost) * 100

Mất gói làm audio đứt quãng, video vỡ hình và có thể khiến WebRTC giảm bitrate
hoặc độ phân giải.

### Bitrate

    bitrate = (chênh lệch bytes * 8) / chênh lệch thời gian

Bitrate là tốc độ media thực tế, khác bandwidth là năng lực của đường mạng.
Candidate type trong panel còn cho biết kết nối đang đi trực tiếp hay qua TURN.

## 8. Heartbeat và phát hiện lỗi

TCP/WebSocket có thể chưa báo ngắt ngay khi thiết bị mất mạng hoặc đổi mạng.
GGStream dùng heartbeat:

    Client -- PING --> Server
    Client <-- PONG -- Server

Server lưu thời điểm heartbeat cuối. HeartbeatReaper đóng session quá timeout
và dọn participant. Đây là bài toán failure detection: timeout quá ngắn gây
false positive, còn timeout quá dài làm participant ma tồn tại lâu.

## 9. Reconnection và ICE restart

Khi signaling socket mất, client thử reconnect. Backend giữ participant trong
một grace period để gián đoạn ngắn không làm participant liên tục rời và vào.

WebSocket reconnect chỉ phục hồi control plane. Khi đổi Wi-Fi, 4G, IP hoặc NAT
mapping, media path có thể cần ICE restart. Offer mới với tùy chọn iceRestart
sẽ thu thập candidate và chạy connectivity check lại mà không phá toàn bộ peer
connection, codec và trạng thái media.

## 10. Mesh topology và khả năng mở rộng

Mỗi participant trong Mesh kết nối trực tiếp với mọi participant khác.

    Số peer connection = N * (N - 1) / 2
    Upload mỗi người gần bằng (N - 1) * bitrate mỗi luồng

Với 6 người và video 1 Mbps, toàn phòng có 15 peer connection và mỗi người có
thể phải upload khoảng 5 Mbps.

Mesh đơn giản, phù hợp phòng nhỏ và nghiên cứu P2P. Tuy nhiên tổng số kết nối
tăng bậc hai. SFU là hướng mở rộng:

    A --+    B ----> SFU ----> các participant
    C --/

SFU giảm upload phía client nhưng cần media server, dải cổng UDP và băng thông
trung tâm.

## 11. Liên hệ mô hình TCP/IP

| Tầng | Công nghệ trong GGStream |
|---|---|
| Application | HTTP, WebSocket, SDP, STUN, TURN |
| Transport | TCP cho HTTP/WebSocket; UDP ưu tiên cho media |
| Internet | IPv4/IPv6, định tuyến và NAT |
| Link | Ethernet, Wi-Fi, 4G/5G |
| Bảo mật media | DTLS và SRTP |
| Media thời gian thực | RTP và RTCP |

## 12. Chức năng hiện tại đang dùng công nghệ nào?

| Chức năng | Công nghệ hoặc cơ chế |
|---|---|
| Đăng ký, đăng nhập | HTTP REST, TCP, JWT |
| Tạo và lên lịch cuộc họp | HTTP REST, TCP, PostgreSQL |
| Lấy danh sách cuộc họp | HTTP GET, TCP |
| Bắt đầu hoặc kết thúc cuộc họp | HTTP POST kết hợp sự kiện WebSocket |
| Tham gia hoặc rời cuộc họp | REST lưu trạng thái, WebSocket cập nhật thời gian thực |
| Phòng chờ và phê duyệt | WebSocket signaling |
| Danh sách thành viên | REST khi tải ban đầu, WebSocket khi có thay đổi |
| Chat trong phòng | WebSocket broadcast |
| Chủ phòng tắt micro người khác | WebSocket point-to-point |
| Chủ phòng mời thành viên ra | WebSocket và đóng session đích |
| Thông báo kết thúc cuộc họp | WebSocket broadcast MEETING_ENDED |
| Bật hoặc tắt micro | MediaStream track và WebSocket đồng bộ trạng thái |
| Bật hoặc tắt camera | MediaStream track truyền bằng WebRTC |
| Chia sẻ màn hình | Screen Capture API, WebRTC renegotiation và SRTP |
| Ghim khung video | Trạng thái giao diện frontend, không sử dụng mạng |
| Gọi audio và video | WebRTC, RTP/RTCP, UDP, DTLS-SRTP |
| Thương lượng codec và track | SDP Offer/Answer |
| Tìm đường giữa hai thiết bị | ICE |
| Phát hiện địa chỉ public | STUN |
| Vượt NAT hoặc firewall nghiêm ngặt | TURN |
| Khôi phục signaling bị ngắt | WebSocket reconnect |
| Khôi phục đường media | ICE restart |
| Phát hiện client mất kết nối | PING/PONG heartbeat và timeout |
| Chẩn đoán chất lượng mạng | RTCPeerConnection.getStats() và RTCP |
| Xác thực WebSocket | JWT trong WebSocket handshake |

### Luồng mạng khi bắt đầu gọi video

    1. HTTP REST
       Kiểm tra phòng và lưu participant

    2. WebSocket
       Thông báo participant và trao đổi SDP, ICE candidate

    3. SDP Offer/Answer
       Thương lượng audio, video và codec

    4. ICE + STUN/TURN
       Tìm candidate pair có thể kết nối

    5. DTLS
       Thương lượng khóa bảo mật

    6. SRTP, thường qua UDP
       Truyền audio và video

    7. RTCP và getStats()
       Đo RTT, jitter, packet loss và bitrate

### Phân loại theo kênh truyền

**HTTP REST** được dùng cho dữ liệu cần lưu bền vững như tài khoản, cuộc họp,
lịch họp, participant, trạng thái bắt đầu hoặc kết thúc và cấu hình STUN/TURN.

**WebSocket** được dùng cho sự kiện thời gian thực như presence, phòng chờ,
SDP, ICE candidate, chat, trạng thái micro, chia sẻ màn hình, điều khiển của chủ
phòng, kết thúc cuộc họp và heartbeat.

**WebRTC** được dùng cho audio, camera, chia sẻ màn hình và thống kê chất lượng
đường media. Có thể ghi nhớ ngắn gọn: HTTP lưu trạng thái, WebSocket điều phối
thời gian thực, WebRTC truyền media.

## 13. Đọc gì để hiểu và tìm mã triển khai?

### Kiến trúc tổng thể

1. [Kiến trúc tổng quan](../architecture/overview.md): hiểu control plane,
   media plane, Mesh và hướng chuyển sang SFU.
2. [Kiến trúc frontend](../architecture/frontend.md): hiểu hook, manager,
   signaling client và cách giao diện phòng họp kết nối các thành phần.
3. [Kiến trúc backend](../architecture/backend.md): hiểu REST, WebSocket
   runtime, concurrency, JWT và TURN credential.

### HTTP, dữ liệu và xác thực

- [REST API](../api/rest.md): endpoint tạo, tham gia, rời, bắt đầu và kết thúc
  cuộc họp.
- [Database schema](../database/schema.md): dữ liệu meeting, participant và
  trạng thái được lưu bền vững như thế nào.
- Mã frontend: apps/web/src/features/meeting/api/meetingApi.ts.
- Mã backend: apps/server/src/main/java/com/project/meet/meeting.

### WebSocket và signaling

1. [WebSocket signaling](./signaling.md): handshake, định tuyến message,
   heartbeat, reconnect và mô hình concurrency.
2. [Giao thức WebSocket](../api/websocket-protocol.md): cấu trúc envelope và ý
   nghĩa từng event.
3. [Luồng thiết lập kết nối](./connection-flow.md): sequence từ join đến khi
   media truyền được.

Mã triển khai chính:

- apps/web/src/lib/websocket/signalingClient.ts;
- apps/web/src/features/meeting/hooks/useMeetingSocket.ts;
- apps/server/src/main/java/com/project/meet/signaling/infrastructure/SignalingWebSocketHandler.java;
- apps/server/src/main/java/com/project/meet/signaling/infrastructure/HeartbeatReaper.java.

### WebRTC và media

1. [WebRTC](./webrtc.md): SDP, offer glare, track, renegotiation, trạng thái
   kết nối, ICE restart và getStats().
2. [ICE, STUN, TURN và NAT traversal](./ice-stun-turn.md): lý do P2P cần STUN
   và khi nào phải dùng TURN.
3. [Cài đặt TURN](./turn-setup.md): cấu hình Coturn và kiểm tra relay candidate.

Mã triển khai chính:

- apps/web/src/lib/webrtc/PeerConnectionManager.ts;
- apps/web/src/lib/webrtc/iceServers.ts;
- apps/web/src/features/meeting/hooks/useWebRtcPeers.ts;
- apps/web/src/features/meeting/hooks/useScreenShare.ts;
- apps/web/src/hooks/useLocalMedia.ts.

### Chẩn đoán mạng

- Phần WebRTC statistics trong [WebRTC](./webrtc.md) giải thích nguồn dữ liệu
  của từng chỉ số.
- apps/web/src/features/meeting/hooks/useWebRtcStats.ts thực hiện polling,
  chuẩn hóa và tính bitrate.
- apps/web/src/features/meeting/components/NetworkDiagnosticsPanel.tsx hiển
  thị số liệu theo participant.

Khi kiểm thử nên so sánh ít nhất ba trường hợp: hai máy cùng LAN, hai máy khác
mạng dùng server-reflexive candidate, và kết nối ép qua TURN relay. Ghi lại RTT,
jitter, packet loss, bitrate, protocol và candidate type để thấy ảnh hưởng của
đường truyền.

### Khả năng mở rộng

- [Cài đặt SFU](./sfu-setup.md): LiveKit, cổng HTTP/WebSocket, TCP RTC và dải
  UDP media.
- Đọc phần Mesh topology trong tài liệu này trước để hiểu vì sao số connection
  tăng theo N nhân N trừ một chia hai.

### Tài liệu chuẩn bên ngoài

- [WebRTC API trên MDN](https://developer.mozilla.org/en-US/docs/Web/API/WebRTC_API):
  tổng quan API trình duyệt và RTCPeerConnection.
- [RTCPeerConnection.getStats trên MDN](https://developer.mozilla.org/en-US/docs/Web/API/RTCPeerConnection/getStats):
  cách lấy RTCStatsReport.
- [Media Capture and Streams trên MDN](https://developer.mozilla.org/en-US/docs/Web/API/Media_Capture_and_Streams_API):
  camera, micro, MediaStream và MediaStreamTrack.
- [Screen Capture API trên MDN](https://developer.mozilla.org/en-US/docs/Web/API/Screen_Capture_API):
  getDisplayMedia và chia sẻ màn hình.
- [RFC 8445 - ICE](https://www.rfc-editor.org/rfc/rfc8445):
  thuật toán ICE và candidate pair.
- [RFC 8489 - STUN](https://www.rfc-editor.org/rfc/rfc8489):
  giao thức khám phá địa chỉ sau NAT.
- [RFC 8656 - TURN](https://www.rfc-editor.org/rfc/rfc8656):
  cơ chế relay qua TURN server.
- [RFC 3550 - RTP/RTCP](https://www.rfc-editor.org/rfc/rfc3550):
  truyền media thời gian thực và báo cáo điều khiển.
- [RFC 8825 - WebRTC overview](https://www.rfc-editor.org/rfc/rfc8825):
  kiến trúc và nhóm giao thức WebRTC.
- [Coturn](https://github.com/coturn/coturn): TURN/STUN server được dùng khi
  triển khai relay.

Nên đọc tài liệu nội bộ trước vì chúng mô tả đúng cách GGStream đang triển
khai, sau đó dùng MDN để hiểu API trình duyệt và RFC để tra cứu định nghĩa
giao thức chính xác.

## 14. Kết luận

GGStream sử dụng nhiều kênh mạng cho những nhiệm vụ khác nhau: HTTP quản lý tài
nguyên, WebSocket quản lý signaling, SDP thương lượng media, ICE/STUN/TURN tìm
đường xuyên NAT, DTLS-SRTP bảo mật, còn RTP/RTCP truyền và đo chất lượng
audio/video. Heartbeat, reconnect và ICE restart tăng khả năng chịu lỗi; Mesh
phù hợp phòng nhỏ và SFU là hướng mở rộng cho phòng đông.

## Tài liệu liên quan

- [WebRTC](./webrtc.md)
- [ICE, STUN, TURN và NAT traversal](./ice-stun-turn.md)
- [WebSocket signaling](./signaling.md)
- [Luồng thiết lập kết nối](./connection-flow.md)
- [Kiến trúc tổng quan](../architecture/overview.md)
- [Kiến trúc frontend](../architecture/frontend.md)
- [Kiến trúc backend](../architecture/backend.md)
- [REST API](../api/rest.md)
- [Giao thức WebSocket](../api/websocket-protocol.md)
