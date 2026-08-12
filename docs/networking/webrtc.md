# WebRTC

**Trạng thái: gọi 1:1 (Milestone 4) và group Mesh với 3+ participant**
**(Milestone 6) đều đã được triển khai — cùng sử dụng một codebase, xem chi tiết bên dưới. SFU thuộc**
**Milestone 11.**

## SDP offer/answer, ICE và lý do media không đi qua backend

`RTCPeerConnection` thương lượng một cuộc gọi thông qua ba bước đan xen nhau, tất cả đều diễn ra phía client trong `lib/webrtc/PeerConnectionManager`:

1. **SDP offer/answer** — mỗi phía mô tả khả năng media của mình
   (codec, track) dưới dạng một SDP blob. Một phía gọi `createOffer()`, gửi
   offer đó sang phía còn lại thông qua signaling; phía còn lại gọi
   `setRemoteDescription()` rồi `createAnswer()`, sau đó gửi answer ngược trở lại.
   Khi cả hai phía đã gọi `setLocalDescription`/`setRemoteDescription` với
   cặp offer/answer tương ứng, connection đã thống nhất được _sẽ gửi những gì_.

2. **Thu thập ICE candidate** — song song với quá trình trên, mỗi browser yêu cầu hệ điều hành
   cung cấp tất cả các đường mạng mà nó có thể được truy cập thông qua
   (địa chỉ LAN local và, thông qua STUN, địa chỉ public phía sau NAT — xem
   [ice-stun-turn.md](./ice-stun-turn.md)), sau đó gửi từng candidate sang phía
   còn lại ngay khi candidate được phát hiện (`onicecandidate`).

   Bước này quyết định _hai browser thực sự sẽ kết nối với nhau bằng cách nào_.

3. Khi ICE tìm được một cặp candidate hoạt động, một **DTLS handshake** sẽ được thực hiện
   để thương lượng khóa mã hóa cho chính luồng media
   (**SRTP** — RTP được mã hóa), sau đó các packet audio/video **RTP**
   sẽ truyền trực tiếp giữa hai browser.

Spring Boot backend chỉ tham gia đúng vào bước 1 và phần trao đổi candidate trong bước 2 —
với vai trò là một relay đơn giản
(`WEBRTC_OFFER`/`WEBRTC_ANSWER`/`ICE_CANDIDATE`, được định tuyến theo `targetId`, xem
[signaling.md](./signaling.md)) — và hoàn toàn không nhìn thấy DTLS handshake hay bất kỳ
RTP packet nào.

Đây không chỉ là một tối ưu hóa; đây chính là mục đích cốt lõi của việc sử dụng WebRTC
thay vì định tuyến media xuyên qua application server.

Nếu backend phải xử lý media, nó sẽ phải gánh băng thông tăng theo số lượng cuộc gọi
trong toàn hệ thống.

## Offer glare và bên nào khởi tạo

Nếu cả hai phía trong một cặp peer cùng gọi `createOffer()` tại cùng một thời điểm
(ví dụ cả hai cùng phản ứng khi thấy người còn lại vừa join),
hệ thống sẽ có hai offer cạnh tranh nhau — tình huống này được gọi là **glare**.

`useWebRtcPeers` tránh vấn đề này mà không cần thêm bất kỳ message điều phối nào:

participant có `userId` nhỏ hơn theo thứ tự từ điển sẽ luôn là bên khởi tạo offer,
một cách xác định trước, cho từng cặp peer.

Phía còn lại chỉ chờ nhận offer và trả lời bằng answer.

Ví dụ:

```text id="g33bmz"
userId A = "123"
userId B = "456"

"123" < "456"

→ A gọi createOffer()
→ B chờ offer và createAnswer()
```

Quy tắc này được áp dụng độc lập cho từng cặp participant.

## ICE restart (Milestone 8)

Việc WebSocket reconnect
(xem [signaling.md](./signaling.md#reconnect-grace-period-milestone-8))
chỉ khôi phục signaling channel.

WebRTC media path là một kết nối UDP/TCP riêng biệt với failure mode riêng và có thể bị hỏng vì:

- thay đổi mạng
- NAT rebinding
- chuyển Wi-Fi
- thay đổi interface mạng

Media path có thể hỏng độc lập với signaling, thậm chí trong khi WebSocket vẫn đang hoạt động bình thường.

`RTCPeerConnection.connectionState` là nguồn trạng thái chính xác cho việc này.

### `disconnected`

Trạng thái:

```text id="ow2hsq"
"disconnected"
```

thường chỉ là tạm thời.

Ví dụ ICE connectivity check có thể thất bại trong một khoảng ngắn rồi tự phục hồi sau vài giây.

Vì vậy `PeerConnectionManager` cố ý **không phản ứng ngay** với trạng thái này,
để tránh restart một connection đang chuẩn bị tự phục hồi.

### `failed`

Trạng thái:

```text id="1u020w"
"failed"
```

có nghĩa browser đã xác định connection không thể phục hồi bằng cơ chế ICE hiện tại.

Khi đó `PeerConnectionManager` gọi:

```text id="gsdsfr"
restartIce(participantId)
```

Logic tương đương:

```typescript id="l6mwd1"
createOffer({ iceRestart: true });
```

sau đó tiếp tục flow thông thường:

```text id="bn8tez"
createOffer({ iceRestart: true })
        ↓
setLocalDescription()
        ↓
send WEBRTC_OFFER through signaling
        ↓
remote peer creates answer
        ↓
ICE candidate gathering runs again
```

Cơ chế này chạy lại ICE candidate gathering và tìm một candidate pair hoạt động mới mà
không cần phá bỏ và tạo lại toàn bộ `RTCPeerConnection`.

Điều này quan trọng vì việc tạo mới connection hoàn toàn có thể làm mất các trạng thái
đã được negotiation trước đó.

### Tránh glare khi ICE restart

Quy tắc tránh glare từ lần kết nối đầu tiên vẫn được sử dụng khi restart.

Chỉ phía vốn là offerer ban đầu mới gửi restart offer.

Thông tin này được lưu trong:

```text id="j2zeyb"
PeerEntry.isInitiator
```

và được set một lần trong:

```text id="polfwl"
connect()
```

Phía còn lại chỉ trả lời bất kỳ offer nào nhận được, giống hoàn toàn với lần kết nối đầu tiên.

Flow:

```text id="allqzm"
Initiator
   │
   │ createOffer({ iceRestart: true })
   ▼
Signaling
   │
   ▼
Non-initiator
   │
   │ createAnswer()
   ▼
Signaling
   │
   ▼
Initiator
```

### `PARTICIPANT_RECONNECTED`

Một signaling event:

```text id="g79m6l"
PARTICIPANT_RECONNECTED
```

được gửi tới tất cả participant khác, ngoại trừ chính user vừa reconnect.

Event này cũng kích hoạt:

```text id="r3lv7w"
restartIce
```

đối với peer tương ứng như một biện pháp chủ động.

Lý do là việc WebSocket reconnect thành công không đảm bảo media path cũng đã hồi phục.

Có trường hợp:

```text id="4wmx2b"
WebSocket
DISCONNECTED
    ↓
RECONNECTED
```

nhưng:

```text id="49iuf7"
WebRTC media path
old NAT mapping
        ↓
no longer valid
```

và `connectionState` có thể chưa kịp chuyển sang `"failed"`.

`PARTICIPANT_RECONNECTED` giúp chủ động kích hoạt quá trình ICE restart trong trường hợp đó.

## Sẵn sàng cho Mesh ngay từ kiến trúc ban đầu

`PeerConnectionManager` ngay từ Milestone 4 đã được xây dựng theo dạng:

```typescript id="04xanj"
Map<participantId, RTCPeerConnection>;
```

Class này chưa bao giờ giả định chỉ tồn tại đúng một remote peer.

Vì vậy khi triển khai group Mesh ở Milestone 6, hệ thống **không cần thay đổi**
`PeerConnectionManager`, `useWebRtcPeers` hoặc logic broadcast routing phía backend.

Nói cách khác, code 1:1 ban đầu thực chất đã có cấu trúc hỗ trợ nhiều peer.

Ví dụ một room có:

```text id="hxwfbu"
A
B
C
D
```

thì phía A có:

```text id="j5g5ej"
PeerConnectionManager
└── peers
    ├── B → RTCPeerConnection
    ├── C → RTCPeerConnection
    └── D → RTCPeerConnection
```

Phía B:

```text id="kox2xi"
PeerConnectionManager
└── peers
    ├── A → RTCPeerConnection
    ├── C → RTCPeerConnection
    └── D → RTCPeerConnection
```

và tương tự cho các participant còn lại.

### Milestone 6 thực sự bổ sung những gì

Milestone 6 chủ yếu bổ sung việc kiểm chứng.

Backend integration test:

```text id="y8s6j0"
MeshSignalingIntegrationTest
```

kiểm tra các trường hợp:

- participant thứ 4 join và nhận đầy đủ snapshot của 3 participant đã có trong room;
- tất cả participant hiện tại đều được thông báo khi participant mới join;
- khi một participant leave, toàn bộ participant còn lại đều nhận được notification tương ứng.

Ngoài ra còn có một lần chạy thực tế với 4 client trên dev server thật,
xác nhận số lượng notification join/snapshot đúng với công thức toán học dự kiến.

Quy tắc tránh glare:

```text id="xkvf2v"
userId nhỏ hơn → initiator
```

cũng không cần bất kỳ logic riêng nào theo số lượng participant.

Nó được đánh giá độc lập cho từng cặp.

Ví dụ với:

```text id="9au8cm"
A < B < C < D
```

các cặp có initiator:

```text id="xmqvig"
A-B → A
A-C → A
A-D → A

B-C → B
B-D → B

C-D → C
```

Mỗi cặp luôn chỉ có đúng một initiator.

## Mesh và SFU

### Mesh

Trong Mesh, mỗi participant mở một `RTCPeerConnection` tới từng participant còn lại.

Với `n` participant, tổng số peer connection là:

```text id="m31ba9"
n(n - 1)
────────
   2
```

Ví dụ:

```text id="2ptktt"
2 participant → 1 connection
3 participant → 3 connections
4 participant → 6 connections
5 participant → 10 connections
6 participant → 15 connections
```

Mỗi participant cũng phải upload stream của chính mình:

```text id="jvbpob"
n - 1
```

lần.

Ví dụ 4 participant:

```text id="mftr2e"
          B
         ▲
         │ stream A
         │
C ◀──────A──────▶ D
   stream A   stream A
```

A phải upload cùng media stream tới:

```text id="eif86p"
B
C
D
```

tức 3 lần.

Do đó bandwidth upload và CPU encoding trên mỗi participant tăng gần tuyến tính theo kích thước room.

Mesh phù hợp cho room nhỏ.

Milestone 6 nhắm tới:

```text id="5uinhm"
3–6 participant
```

và sẽ nhanh chóng gặp giới hạn khi room lớn hơn.

### SFU

Với SFU, mỗi participant chỉ gửi **một** upload stream tới một relay trung tâm.

```text id="dxcn9m"
              ┌─────────┐
              │    A    │
              └────┬────┘
                   │
                   │ 1 upload
                   ▼
              ┌─────────┐
              │   SFU   │
              └─┬──┬──┬─┘
                │  │  │
                ▼  ▼  ▼
                B  C  D
```

SFU sau đó forward stream tới những participant còn lại mà không cần re-encode.

Chi phí upload của participant trở thành gần như cố định:

```text id="paoe1z"
Mesh:
upload ≈ n - 1 streams

SFU:
upload ≈ 1 stream
```

SFU chịu trách nhiệm fan-out.

Do đó:

```text id="vsytr8"
Participant bandwidth
        ↓
ổn định hơn

SFU bandwidth
        ↓
tăng theo số participant
```

Milestone 10 sẽ đo hành vi thực tế của Mesh với:

```text id="uxo68v"
2
3
4
5
6
```

participant thông qua các chỉ số:

- bandwidth
- CPU
- RTT
- packet loss

Sau đó Milestone 11 mới sử dụng dữ liệu đó để giải thích và thiết kế quá trình chuyển sang SFU.

Xem milestone plan trong root `README`.

## Triển khai SFU (Milestone 11)

SFU thực tế của project là một server [LiveKit](https://livekit.io) tự
host, chạy trực tiếp trên máy qua `livekit-server.exe` — xem
[sfu-setup.md](./sfu-setup.md) để biết lý do chọn LiveKit thay vì tự viết
SFU bằng mediasoup (chủ yếu vì máy development không có sẵn toolchain C++
để compile mediasoup's native worker, còn LiveKit có binary Windows chính
thức, không cần compile).

Khác với Mesh, SFU-mode meeting không dùng `PeerConnectionManager` ở
frontend hay relay `WEBRTC_OFFER`/`ANSWER`/`ICE_CANDIDATE` ở backend —
LiveKit tự quản lý toàn bộ signaling + SFU nội bộ của chính nó, tách biệt
hoàn toàn khỏi WebSocket signaling của project (`SignalingWebSocketHandler`).
Kết nối tới LiveKit đi qua một transport hoàn toàn khác:

```text id="sfu-flow"
Client                          Backend (Spring Boot)         LiveKit server
  │                                     │                            │
  │  POST /meetings/{id}/sfu-token      │                            │
  │ ───────────────────────────────────▶                            │
  │                                     │  ký AccessToken (JWT)      │
  │                                     │  cục bộ, không gọi mạng    │
  │  { url, token }                     │                            │
  │ ◀───────────────────────────────────                            │
  │                                                                  │
  │  Room.connect(url, token)  (livekit-client)                     │
  │ ─────────────────────────────────────────────────────────────▶ │
  │                                                                  │
  │  audio/video của MỌI participant khác, qua MỘT kết nối duy nhất │
  │ ◀───────────────────────────────────────────────────────────── │
```

Chỉ transport media bị thay: chat, phòng chờ, tắt tiếng do chủ phòng, và
reconnect (Milestone 7–8) vẫn chạy qua WebSocket signaling gốc, không đổi
gì — vì những tính năng đó vốn thuộc tầng ứng dụng/signaling, không thuộc
tầng media.

### Vì sao chỉ có một `RTCPeerConnection`

`RemoteTrack` của LiveKit không tự gắn nhãn "đây là participant nào" —
`lib/sfu/liveKitClient.ts` phân biệt bằng `participant.identity`, chính là
`userId` được backend nhúng vào token lúc ký (`IssueSfuTokenUseCase` →
`AccessToken.setIdentity(userId)`). Track camera/mic của một participant
được gộp vào một `MediaStream` theo `identity` đó; track chia sẻ màn hình
(`Track.Source.ScreenShare`) được tách riêng — cùng quy ước với Mesh, nơi
`primaryRemoteStreamId` (thứ tự `MediaStream.id` xuất hiện) đóng vai trò
tương tự.

### Token ngắn hạn, không gọi mạng để cấp

`IssueSfuTokenUseCase` không hề gọi tới LiveKit server để cấp token — ký
JWT bằng `io.livekit.server.AccessToken` là phép tính cục bộ (HMAC dựa
trên `LIVEKIT_API_SECRET`), giống hệt cách `TurnCredentialService` tính
TURN credential bằng HMAC-SHA1. Điều này có nghĩa endpoint
`POST /meetings/{id}/sfu-token` vẫn trả lời được ngay cả khi
`livekit-server.exe` đang tắt — client sẽ chỉ thất bại ở bước
`Room.connect()` sau đó, không phải ở bước xin token.

## Thống kê WebRTC

Sau khi media bắt đầu truyền
(Milestone 9), hệ thống định kỳ gọi:

```typescript id="6hp65t"
RTCPeerConnection.getStats();
```

để thu thập các chỉ số networking.

Bao gồm:

```text id="orjjwq"
RTT
jitter
packet loss
bitrate
frames dropped
candidate type
protocol
codec
```

Các chỉ số này được hiển thị trong developer/debug panel.

Ví dụ:

```text id="4dfl9d"
Peer: participant-B

Connection:
  state: connected
  protocol: udp
  candidate: srflx

Network:
  RTT: 42 ms
  jitter: 7 ms
  packet loss: 0.8%

Video:
  bitrate: 1.8 Mbps
  frames dropped: 3
  codec: VP8
```

Đây là nguồn dữ liệu chính được sử dụng cho phần phân tích mạng của báo cáo đồ án.

### Cách triển khai Milestone 9

`PeerConnectionManager.getStats()` gọi `RTCPeerConnection.getStats()` cho
từng kết nối trong Mesh và trả về một `Map<participantId, RTCStatsReport>`.
`useWebRtcStats` chỉ poll khi panel chẩn đoán đang mở, với chu kỳ 2 giây,
để tránh tạo công việc nền không cần thiết.

Các report được ghép như sau:

- `candidate-pair` được chọn cung cấp `currentRoundTripTime` (RTT).
- `local-candidate` mà candidate pair tham chiếu cung cấp
  `candidateType` và `protocol`.
- `inbound-rtp` cung cấp jitter, packets lost/received, bytes received,
  frames dropped và `codecId`.
- `codecId` được tra ngược sang report `codec` để lấy `mimeType`.

Bitrate không phải giá trị tức thời có sẵn. Hook tính từ hai lần lấy mẫu:

```text
bitrate (kbps) = (bytesReceived mới - bytesReceived cũ) × 8
                 ───────────────────────────────────────────
                    thời gian giữa hai mẫu (milliseconds)
```

Tỷ lệ mất gói là giá trị tích lũy của kết nối:

```text
packet loss (%) = packetsLost / (packetsReceived + packetsLost) × 100
```

Panel chỉ phản ánh thống kê mà trình duyệt hiện tại quan sát được cho từng
peer; backend không thu thập hoặc tổng hợp dữ liệu này.
