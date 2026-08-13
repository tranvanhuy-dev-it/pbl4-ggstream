# Thiết kế hạ tầng mạng — GGStream

Tài liệu này mô tả **toàn bộ hạ tầng mạng** của hệ thống: mỗi thành phần
là gì (cơ chế), nằm ở đâu trong kiến trúc, vì sao được chọn, và được cấu
hình/vận hành cụ thể như thế nào. Khác với
[network-theory-in-ggstream.md](../networking/network-theory-in-ggstream.md)
(tập trung vào *lý thuyết* Mạng máy tính áp dụng ở đâu trong code), tài
liệu này tập trung vào *hạ tầng* — service nào chạy, lắng nghe cổng nào,
nói giao thức gì với ai, và tại sao hệ thống được ghép nối như vậy.

## 1. Sơ đồ tổng thể

```text id="topology"
                                   ┌─────────────────────┐
                                   │   PostgreSQL 18      │  cổng 5432 (chỉ nội bộ)
                                   │   (cài trực tiếp)     │
                                   └──────────▲───────────┘
                                              │ JDBC
┌──────────────┐   HTTP REST (TCP 8080)  ┌────┴────────────┐
│              │ ───────────────────────▶│                 │
│  Next.js Web │                          │  Spring Boot     │
│  (trình duyệt)│  WebSocket (TCP 8080)   │  backend         │
│              │ ◀──────────────────────▶│  (control plane) │
└──────┬───────┘                          └────┬────────┬───┘
       │                                        │        │
       │ WebRTC/SRTP (UDP, ưu tiên)              │        │ REST + WS
       │ trực tiếp trình duyệt-trình duyệt        │        │ (LiveKit)
       │ (Mesh) hoặc trình duyệt-SFU              │        │
       ▼                                        ▼        ▼
┌──────────────┐                    ┌────────────────┐  ┌────────────────┐
│ STUN (Google  │                    │ LiveKit SFU     │  │ FFmpeg          │
│ public hoặc   │                    │ 7880 WS/HTTP    │  │ (child process,  │
│ Coturn 3478)  │                    │ 7881 TCP RTC    │  │ không network,   │
└──────────────┘                    │ 50000-60000 UDP │  │ ghi HLS ra đĩa)   │
                                     └────────┬────────┘  └────────┬────────┘
                                              │ Redis (multi-node) │ static file
                                              ▼                    ▼
                                     ┌────────────────┐  ┌────────────────┐
                                     │ Redis 6379      │  │ nginx cache      │
                                     │ (chỉ khi cluster)│  │ / Spring static  │
                                     └────────────────┘  │ 8081 hoặc 8080    │
                                                          └────────┬────────┘
                                                                   │ HTTP
                                                                   ▼
                                                          Viewer livestream
                                                          (bất kỳ trình duyệt)
```

Nguyên tắc xuyên suốt: **control plane** (HTTP REST + WebSocket, luôn đi
qua Spring Boot) tách hoàn toàn khỏi **media plane** (WebRTC/SRTP, RTP,
HLS) — backend không bao giờ đọc/giải mã nội dung audio/video, chỉ điều
phối ai nói chuyện với ai.

## 2. HTTP REST API

**Cơ chế**: request-response qua TCP, JSON, JWT trong header
`Authorization: Bearer <token>`.

**Được dùng ở đâu**: đăng ký/đăng nhập, tạo/tham gia/rời/kết thúc cuộc
họp, danh sách participant, lịch sử chat, cấp ICE server, cấp SFU token,
bắt đầu/dừng livestream, tra trạng thái livestream công khai.

**Lý do dùng**: các hành động này là request-response một lần, có kết quả
xác định (thành công/thất bại), không cần độ trễ thấp liên tục — HTTP
REST đơn giản hơn WebSocket rất nhiều cho nhóm việc này (không cần quản
lý kết nối lâu dài, tận dụng được cache/tooling HTTP chuẩn).

**Dùng như thế nào**: `apps/server` (Spring Boot, package theo tính năng
— `auth/`, `meeting/`, `participant/`, `chat/`, `rtc/`, `sfu/`,
`livestream/`), lắng nghe cổng `SERVER_PORT` (mặc định `8080`). Xác thực
qua `JwtAuthenticationFilter` (`OncePerRequestFilter`), một số route công
khai (`/api/v1/auth/**`, `/actuator/health`, `GET /api/v1/live/*/status`,
`GET /livestreams/**`) được khai báo `permitAll()` trong
`SecurityConfig`. Xem [rest.md](../api/rest.md) cho danh sách endpoint đầy
đủ.

## 3. WebSocket Signaling (control plane thời gian thực)

**Cơ chế**: một kết nối TCP full-duplex lâu dài, giữ nguyên trong suốt
thời gian ở trong phòng/livestream. JWT xác thực **tại lúc handshake**
(qua query param `?token=`, không phải header — trình duyệt không set
được header tùy ý trên request nâng cấp WebSocket).

**Được dùng ở đâu**: hai endpoint riêng biệt, dùng chung cơ chế nhưng
không dùng chung interceptor xác thực (vì hai tính năng độc lập nhau —
xem [livestream.md](../networking/livestream.md)):

| Endpoint | Việc gì | Interceptor xác thực |
|---|---|---|
| `/ws/meetings/{meetingId}` | presence, relay SDP/ICE (Mesh), chat, phòng chờ, điều khiển chủ phòng, heartbeat | `SignalingHandshakeInterceptor` (tra `Meeting` trong DB) |
| `/ws/live/{id}/ingest` | nhận chunk nhị phân từ `MediaRecorder` của broadcaster, ghi vào stdin FFmpeg | `LivestreamHandshakeInterceptor` (không tra DB nào cả) |

**Lý do dùng**: các sự kiện này cần độ trễ thấp, hai chiều, và tần suất
cao (mỗi ICE candidate, mỗi tin nhắn chat, mỗi nhịp heartbeat) — mở một
request HTTP mới cho từng sự kiện sẽ tốn overhead handshake TCP+TLS lặp
lại liên tục. Presence (ai vào/ra phòng) cũng cần được đẩy (push) từ
server, điều REST một chiều không làm được mà không phải polling.

**Dùng như thế nào**: `SignalingWebSocketHandler`/`LivestreamIngestWebSocketHandler`
(`extends TextWebSocketHandler`/`BinaryWebSocketHandler`), đăng ký trong
`WebSocketConfig`. Mỗi phòng xử lý lệnh (join/leave/relay) tuần tự qua
`RoomCommandDispatcher` — một chuỗi `CompletableFuture` riêng cho từng
phòng, chạy trên `Executors.newVirtualThreadPerTaskExecutor()` (Java 21)
dùng chung cho mọi phòng — các phòng khác nhau chạy hoàn toàn song song,
trong một phòng thì lệnh chạy đúng thứ tự. Xem
[signaling.md](../networking/signaling.md).

## 4. STUN

**Cơ chế**: giao thức UDP đơn giản — client gửi request, server trả về
đúng địa chỉ IP:port public mà nó nhìn thấy request tới từ đâu (giúp
client biết địa chỉ public của chính mình sau NAT).

**Được dùng ở đâu**: bước thu thập ICE candidate (`srflx`) ở cả hai phía
Mesh call; LiveKit cũng tự dùng STUN cho chính nó khi `rtc.use_external_ip: true`.

**Lý do dùng**: hầu hết thiết bị nằm sau NAT (router chia sẻ một IP public
cho nhiều thiết bị) — không có STUN, hai trình duyệt không thể biết địa
chỉ nào của nhau là "kết nối được từ Internet".

**Dùng như thế nào**: mặc định dùng STUN public của Google
(`stun:stun.l.google.com:19302`, cấu hình qua `STUN_URL`), trả về từ
`GET /api/v1/rtc/ice-servers`. Không cần chạy STUN server riêng — STUN
không cần trạng thái, không cần xác thực, dùng chung server public an
toàn.

## 5. TURN

**Cơ chế**: relay UDP/TCP có trạng thái — khi hai peer không thể kết nối
trực tiếp (symmetric NAT, firewall chặn UDP), toàn bộ media đi qua TURN
server làm trung gian.

**Được dùng ở đâu**: fallback cuối cùng trong ICE candidate gathering cho
Mesh call, khi `srflx`/`host` candidate không ghép được cặp nào hoạt
động.

**Lý do dùng**: không phải mọi mạng đều cho phép kết nối P2P trực tiếp —
TURN là cách duy nhất đảm bảo cuộc gọi vẫn kết nối được trong trường hợp
xấu nhất, đổi lại băng thông/độ trễ/chi phí server tăng (media đi vòng
qua TURN thay vì đường ngắn nhất).

**Dùng như thế nào**: **Trạng thái: đã thiết kế, chưa cài đặt thật**
(Coturn không có bản build Windows chính thức — xem
[turn-setup.md](../networking/turn-setup.md)). Cơ chế cấp credential đã
hoàn chỉnh: `TurnCredentialService` ký `Base64(HMAC-SHA1(TURN_SECRET,
"<hạn dùng>:<userId>"))` — đúng quy ước `use-auth-secret` của Coturn —
nên **secret dùng chung không bao giờ rời khỏi backend**, chỉ credential
đã ký, ngắn hạn, mới tới trình duyệt qua `GET /api/v1/rtc/ice-servers`.
Khi `TURN_HOST`/`TURN_SECRET` trống, endpoint tự suy biến về chỉ-STUN,
không lỗi.

## 6. ICE

**Cơ chế**: thuật toán ghép cặp — mỗi bên thu thập toàn bộ candidate có
thể (host/srflx/relay), trao đổi qua signaling, rồi thử kết nối từng cặp
(connectivity check) để tìm ra cặp candidate hoạt động được, ưu tiên cặp
ít tốn kém nhất (host > srflx > relay).

**Được dùng ở đâu**: mọi kết nối WebRTC — cả Mesh (trình duyệt-trình
duyệt) lẫn SFU (trình duyệt-LiveKit).

**Lý do dùng**: STUN/TURN chỉ cung cấp *nguyên liệu* (candidate) — ICE là
thứ quyết định *dùng candidate nào*, tự động, không cần người dùng chọn
tay.

**Dùng như thế nào**: hoàn toàn trong trình duyệt (`RTCPeerConnection`),
`PeerConnectionManager.ts` chỉ quản lý việc gửi/nhận candidate qua
WebSocket (`ICE_CANDIDATE` envelope) và xếp hàng candidate đến trước khi
`setRemoteDescription` sẵn sàng.

## 7. SDP Offer/Answer

**Cơ chế**: mô tả phiên (session description) dạng text — track nào,
codec nào, hướng truyền nào — trao đổi một lần qua signaling trước khi
media chạy, và trao đổi lại (renegotiate) mỗi khi tập track thay đổi
(chia sẻ màn hình, ICE restart).

**Được dùng ở đâu**: thiết lập Mesh call, và mỗi lần renegotiate (bắt
đầu/dừng chia sẻ màn hình, ICE restart).

**Lý do dùng**: hai bên phải thống nhất *sẽ gửi cái gì* trước khi biết
*gửi bằng đường nào* (đó là việc của ICE) — tách hai mối quan tâm.

**Dùng như thế nào**: `WEBRTC_OFFER`/`WEBRTC_ANSWER` relay qua WebSocket
theo `targetId`, backend không đọc nội dung SDP. Bên khởi tạo được chọn
xác định bằng so sánh `userId` (nhỏ hơn thì khởi tạo) để tránh "offer
glare" mà không cần thêm message điều phối.

## 8. DTLS-SRTP

**Cơ chế**: sau khi ICE tìm được candidate pair, DTLS handshake (TLS chạy
trên UDP) sinh khóa mã hóa; RTP sau đó luôn được mã hóa thành SRTP.

**Được dùng ở đâu**: mọi luồng media WebRTC — Mesh lẫn SFU.

**Lý do dùng**: bắt buộc theo chuẩn WebRTC (không có "RTP trần" trong
WebRTC) — đảm bảo audio/video không bị nghe lén trên đường truyền, kể cả
khi đi qua TURN relay (TURN chỉ chuyển tiếp byte đã mã hóa, không giải
mã được).

**Dùng như thế nào**: hoàn toàn trong trình duyệt/LiveKit, backend
Spring Boot không giữ khóa và không tham gia bước này.

## 9. RTP/RTCP

**Cơ chế**: RTP đóng gói audio/video theo thời gian thực qua UDP
(sequence number + timestamp); RTCP là kênh báo cáo đi kèm (packet loss,
jitter, RTT).

**Được dùng ở đâu**: truyền media thật sự (Mesh: trình duyệt↔trình
duyệt; SFU: trình duyệt↔LiveKit); RTCP là nguồn dữ liệu cho
`NetworkDiagnosticsPanel` qua `RTCPeerConnection.getStats()`.

**Lý do dùng**: UDP không đảm bảo thứ tự/không mất gói — RTP tự bổ sung
đúng phần cần thiết (thứ tự, thời điểm phát) mà không cần cơ chế
retransmit nặng nề như TCP (gói đến trễ trong cuộc gọi thời gian thực
không còn giá trị để chờ).

**Dùng như thế nào**: xử lý hoàn toàn bởi engine WebRTC của trình duyệt —
không có code tự viết nào xử lý RTP packet trực tiếp trong project này.

## 10. Mesh (kết nối trực tiếp trình duyệt-trình duyệt)

**Cơ chế**: mỗi participant mở một `RTCPeerConnection` với **từng**
participant khác — `n(n-1)/2` kết nối cho `n` người.

**Được dùng ở đâu**: `mediaMode: "MESH"` (mặc định khi tạo cuộc họp).

**Lý do dùng**: đơn giản nhất — không cần hạ tầng media server nào, độ
trễ thấp nhất (media đi đường ngắn nhất). Phù hợp phòng nhỏ.

**Dùng như thế nào**: `PeerConnectionManager.ts` (frontend, giữ
`Map<participantId, RTCPeerConnection>`). Backend **không tham gia** —
chỉ relay SDP/ICE. Giới hạn cứng ở
`MESH_MAX_PARTICIPANTS` (mặc định 6, enforce trong
`SignalingWebSocketHandler.handleJoin`) — xem
[scalability-plan.md](./scalability-plan.md) mục 1 cho lý do (tăng bậc
hai, không phải giới hạn tùy ý).

## 11. SFU — LiveKit

**Cơ chế**: mỗi participant chỉ upload **một lần** tới SFU; SFU forward
lại cho những người còn lại — chi phí upload không tăng theo số người.

**Được dùng ở đâu**: `mediaMode: "SFU"` (chủ phòng chọn lúc tạo cuộc
họp, dành cho phòng đông người).

**Lý do dùng**: Mesh vỡ nhanh khi phòng đông (xem mục 10) — SFU là cách
duy nhất thực tế để một phòng có tới 100 người. Chọn LiveKit (không tự
viết SFU) vì có binary Windows chính thức, không cần Docker, không cần
compile — xem [sfu-setup.md](../networking/sfu-setup.md) cho lý do đầy
đủ (mediasoup cần MSVC C++ Build Tools, egress cần Docker).

**Dùng như thế nào**: tiến trình `livekit-server.exe` riêng biệt, độc lập
với Spring Boot:

| Cổng | Giao thức | Việc gì |
|---|---|---|
| 7880 | HTTP/WebSocket | signaling nội bộ của LiveKit + REST admin API |
| 7881 | TCP | RTC fallback khi UDP bị chặn |
| 50000–60000 | UDP | RTP/RTCP media thật sự |

Backend chỉ ký access token (`IssueSfuTokenUseCase`, JWT ký bằng
`LIVEKIT_API_SECRET`, cục bộ, không gọi mạng) — trình duyệt dùng token đó
kết nối **thẳng** tới LiveKit qua `livekit-client`, không qua Spring Boot.
Mở rộng nhiều node cần Redis dùng chung trạng thái routing (xem mục 13 và
`services/sfu/livekit-cluster.yaml.example`) — hiện tại chạy 1 node
("single-node routing").

## 12. Livestream — Ingest WebSocket + FFmpeg + HLS

**Cơ chế**: ba chặng khác hẳn nhau về giao thức, nối tiếp nhau:

```text id="livestream-chain"
Trình duyệt broadcaster
  │  MediaRecorder → chunk nhị phân mỗi ~1s
  ▼
WebSocket nhị phân (/ws/live/{id}/ingest)
  │  ghi thẳng vào stdin
  ▼
Tiến trình FFmpeg (child process, không qua mạng — pipe cục bộ)
  │  chuyển mã sang H.264/AAC, cắt thành HLS
  ▼
File .m3u8 + .ts trên đĩa
  │  HTTP GET thông thường
  ▼
Viewer (bất kỳ trình duyệt nào, qua thẻ <video> + hls.js)
```

**Được dùng ở đâu**: tính năng "Trực tiếp" độc lập (`/live`), không liên
quan Meeting/SFU/Mesh (xem [livestream.md](../networking/livestream.md)
cho lý do tách biệt).

**Lý do dùng**: WebSocket nhị phân cho chặng ingest vì `MediaRecorder`
sinh ra một luồng byte liên tục (không phải file rời rạc) — khớp với
FFmpeg đọc từ một `stdin` duy nhất, và giữ đúng thứ tự byte mà không tốn
overhead của một HTTP request riêng cho mỗi mảnh nhỏ. HLS (thay vì WebRTC)
cho chặng phát vì viewer có thể đông (hàng trăm/nghìn), và HLS là file
tĩnh qua HTTP — cache được, không cần một kết nối WebRTC riêng cho mỗi
viewer.

**Dùng như thế nào**: `FfmpegProcessLauncher` (`ProcessBuilder`, package
`livestream.infrastructure`) — codec cấu hình được qua
`LIVESTREAM_VIDEO_CODEC_ARGS` (mặc định phần mềm `libx264`, có thể đổi
sang phần cứng `h264_nvenc`/`h264_amf`/`h264_mf`). HLS output phục vụ qua
`WebConfig.addResourceHandlers` (`/livestreams/**` → đĩa), có thể đặt
nginx cache trước (xem mục 14) khi viewer đông.

## 13. Redis (khi mở rộng multi-node LiveKit)

**Cơ chế**: key-value store trong bộ nhớ, dùng làm "bảng định tuyến" dùng
chung — node LiveKit nào đang giữ room nào.

**Được dùng ở đâu**: **chưa dùng trong triển khai hiện tại** (1 node duy
nhất không cần) — chỉ cần khi chạy ≥2 tiến trình `livekit-server.exe`
song song.

**Lý do dùng**: nhiều tiến trình LiveKit không tự biết về nhau nếu chỉ
chạy song song (mỗi tiến trình có bộ nhớ riêng) — Redis là cách LiveKit
chọn để chia sẻ trạng thái routing giữa các node.

**Dùng như thế nào**: xem
[sfu-setup.md](../networking/sfu-setup.md#multi-node--redis) và
`services/sfu/livekit-cluster.yaml.example` — cấu hình mẫu, cổng mặc định
`6379`, chỉ truy cập nội bộ (không public).

## 14. nginx reverse proxy + cache (khi viewer livestream đông)

**Cơ chế**: đứng trước backend, cache các response HTTP tĩnh trong thời
gian ngắn thay vì để mỗi request đều chạm Spring Boot.

**Được dùng ở đâu**: **chưa triển khai thật** — cấu hình mẫu
`services/livestream/nginx-hls.conf.example`, dùng khi một livestream có
hàng trăm viewer cùng poll đúng file `.m3u8`/`.ts`.

**Lý do dùng**: HLS vốn được thiết kế để cacheable — phục vụ trực tiếp từ
một Tomcat instance không tận dụng được điều đó, dồn hết tải vào một
điểm.

**Dùng như thế nào**: cache `.ts` gần như vĩnh viễn (segment không đổi
sau khi ghi), `.m3u8` trong ~2 giây (khớp `-hls_time 2` của FFmpeg). Xem
[livestream-setup.md](../networking/livestream-setup.md#4-reverse-proxy-cache-cho-hls-output-khi-có-nhiều-viewer).

## 15. PostgreSQL

**Cơ chế**: kết nối TCP nội bộ (JDBC), không phải giao thức "mạng" theo
nghĩa signaling/media, nhưng là một thành phần hạ tầng của hệ thống.

**Được dùng ở đâu**: dữ liệu cần bền vững — tài khoản, cuộc họp, lịch sử
participant, chat. **Không** lưu trạng thái runtime (ai đang online, ai
đang livestream) — xem
[schema.md](../database/schema.md#what-is-not-persisted).

**Lý do dùng**: dữ liệu quan hệ, cần ACID cho các thao tác như "join là
idempotent", "end meeting đóng mọi participant đang active".

**Dùng như thế nào**: cài trực tiếp trên host (không Docker), cổng
`5432`, chỉ Spring Boot kết nối tới — không public ra ngoài. Hikari
connection pool (`maximum-pool-size: 30`, xem
[scalability-plan.md](./scalability-plan.md) mục 4).

## 16. Bảng tổng hợp cổng mạng

| Cổng | Service | Giao thức | Phạm vi truy cập |
|---|---|---|---|
| 3000 | Next.js dev server | HTTP | Máy phát triển / LAN |
| 8080 | Spring Boot (REST + WebSocket) | TCP (HTTP/WS) | Public (qua Internet nếu deploy) |
| 8081 | nginx (nếu bật cache HLS) | HTTP | Public thay cho 8080 khi có |
| 5432 | PostgreSQL | TCP (JDBC) | Chỉ nội bộ |
| 6379 | Redis (nếu bật multi-node) | TCP | Chỉ nội bộ |
| 3478 | TURN/STUN (Coturn, nếu cài) | UDP/TCP | Public |
| 7880 | LiveKit HTTP/WebSocket | TCP | Public |
| 7881 | LiveKit RTC (TCP fallback) | TCP | Public |
| 50000–60000 | LiveKit RTC media | UDP | Public |

## 17. Bảo mật hạ tầng — nguyên tắc chung

Xuyên suốt toàn hệ thống: **mọi shared secret chỉ tồn tại ở backend**,
trình duyệt chỉ bao giờ nhận credential đã ký, ngắn hạn:

| Secret | Ai giữ | Trình duyệt nhận gì |
|---|---|---|
| `TURN_SECRET` | Backend | Credential HMAC-SHA1 ngắn hạn (`TurnCredentialService`) |
| `LIVEKIT_API_SECRET` | Backend | JWT access token đã ký, gắn 1 room + 1 user |
| `JWT_SECRET` | Backend | Chính JWT (nhưng backend luôn verify chữ ký, client không tự tạo được) |

Không service nào trong danh sách ở mục 16 chạy trong Docker — mọi thứ
cài/chạy trực tiếp trên host, theo đúng ràng buộc của project.

## Tài liệu liên quan

- [Lý thuyết Mạng máy tính áp dụng trong GGStream](../networking/network-theory-in-ggstream.md) — góc nhìn khái niệm/lý thuyết
- [Kế hoạch mở rộng quy mô](./scalability-plan.md) — điểm nghẽn và hướng scale từng thành phần
- [WebSocket signaling](../networking/signaling.md)
- [WebRTC](../networking/webrtc.md)
- [ICE/STUN/TURN](../networking/ice-stun-turn.md)
- [Cài đặt TURN](../networking/turn-setup.md)
- [Cài đặt SFU (LiveKit)](../networking/sfu-setup.md)
- [Livestream](../networking/livestream.md)
- [Cài đặt Livestream (FFmpeg)](../networking/livestream-setup.md)
