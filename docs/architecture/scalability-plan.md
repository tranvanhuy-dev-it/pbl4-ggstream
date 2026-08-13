# Kế hoạch mở rộng quy mô (scalability plan)

**Mục tiêu cụ thể được đặt ra:**

```text id="scale-target"
1 cuộc họp     : tối đa 100 người
Đồng thời      : 100 cuộc họp cùng lúc
Livestream     : 10 người livestream cùng lúc, mỗi người 100 người xem
```

Tài liệu này rà soát lại **cấu trúc mạng hiện tại của từng chức năng**,
chỉ ra điểm sẽ nghẽn trước tiên khi đạt tới các con số trên, và đề xuất kế
hoạch tối ưu theo thứ tự ưu tiên. Đây là phân tích + kế hoạch — **chưa
triển khai** bất kỳ thay đổi nào trong tài liệu này.

## Tổng khối lượng thực tế ở quy mô mục tiêu

```text id="scale-math"
100 cuộc họp × 100 người           = tối đa 10.000 kết nối WebSocket signaling đồng thời
10 livestream × 100 người xem      = 1.000 kết nối HLS viewer đồng thời
10 livestream                      = 10 tiến trình FFmpeg chạy song song
```

Đây là **trường hợp xấu nhất** (worst case) — tất cả cuộc họp đều đầy
người cùng lúc. Kế hoạch bên dưới thiết kế để chịu được mức này, không chỉ
mức trung bình.

---

## 1. Mesh (WebRTC 1:1/nhóm nhỏ) — **không thể dùng cho cuộc họp 100 người**

### Cấu trúc hiện tại

Mỗi participant mở một `RTCPeerConnection` tới **từng** participant khác
(`PeerConnectionManager`, xem [webrtc.md](../networking/webrtc.md)). Với
`n` người:

```text id="mesh-math"
Tổng peer connection = n(n-1)/2
Mỗi người upload      = n-1 lần chính luồng của mình

n = 6   →  15 connections,  upload ×5
n = 20  →  190 connections, upload ×19
n = 100 →  4.950 connections, upload ×99   ← không khả thi
```

Ở `n = 100`, một máy client bình thường (điện thoại, laptop) không đủ
CPU/băng thông để encode và upload luồng của mình 99 lần cùng lúc — đây là
giới hạn **vật lý của kiến trúc Mesh**, không phải giới hạn có thể tối ưu
bằng code.

### Kế hoạch

- **Bắt buộc SFU cho phòng đông người**: hiện `mediaMode` (MESH/SFU) do
  chủ phòng chọn tự do lúc tạo phòng (`CreateMeetingUseCase`), không có
  ràng buộc nào. Cần thêm giới hạn cứng: **backend từ chối `join` (Mesh)**
  khi số người đang hoạt động trong `MeetingRuntime` vượt ngưỡng (số liệu
  benchmark thật ở Milestone 10 sẽ quyết định ngưỡng chính xác, ước tính
  khoảng 6–8 người) — trả lỗi rõ ràng gợi ý host nên tạo lại phòng ở chế
  độ SFU, thay vì để client tự sập vì quá tải.
- Không cần sửa gì ở tầng Mesh code hiện có — Mesh vẫn đúng vai trò của nó
  (phòng nhỏ, độ trễ thấp nhất, không phụ thuộc SFU), chỉ cần **chặn** nó
  khỏi kịch bản 100 người.

---

## 2. SFU (LiveKit) — điểm nghẽn lớn nhất cho mục tiêu 100×100

### Cấu trúc hiện tại

Một tiến trình `livekit-server.exe` **duy nhất**, tự chọn:

```text id="livekit-log"
"using single-node routing"
```

(thấy trực tiếp trong log khi khởi động — LiveKit tự nhận diện không có
Redis nên chạy chế độ 1 node). Toàn bộ 100 cuộc họp × tối đa 100 người sẽ
dồn hết vào **một** node này.

Hai giới hạn khác nhau cần tách riêng:

**a) Số phòng/participant một node LiveKit chịu được** — về lý thuyết một
node LiveKit đơn có thể xử lý hàng nghìn participant nếu máy đủ mạnh (CPU
nhiều lõi cho SFU forwarding + băng thông mạng lớn), nhưng đây là **một
điểm lỗi duy nhất** (single point of failure) — node chết là mất toàn bộ
100 cuộc họp cùng lúc, không riêng gì hiệu năng.

**b) Băng thông trong MỘT phòng 100 người** — kể cả với SFU, nếu mọi
người subscribe video của mọi người khác thì:

```text id="sfu-bw"
Mỗi người download tối đa 99 luồng video cùng lúc → không khả thi trên trình duyệt/mạng gia đình
```

LiveKit hỗ trợ sẵn (nhưng project **chưa cấu hình khai thác**):
`simulcast` (mỗi người publish nhiều độ phân giải, subscriber chọn độ phân
giải phù hợp), giới hạn subscribe (chỉ hiển thị/nghe N người đang nói to
nhất — "active speaker"), và tắt video khi ra khỏi khung nhìn (giống các
app họp lớn thật).

### Kế hoạch

1. **Multi-node LiveKit + Redis** (ưu tiên cao nhất cho mục tiêu 100 cuộc
   họp đồng thời): chạy nhiều tiến trình `livekit-server.exe` (mỗi cái vẫn
   là một binary Windows, không Docker), tất cả trỏ về cùng một Redis
   instance để chia sẻ trạng thái routing — LiveKit tự cân bằng phòng
   giữa các node. Redis trên Windows: dùng bản port của Microsoft
   (`Memurai` hoặc WSL2, cùng tinh thần "không phải Docker" đã áp dụng cho
   Coturn ở [turn-setup.md](../networking/turn-setup.md)).
2. **Bật simulcast + giới hạn subscription** trong cấu hình client
   `livekit-client` (`Room` options) và server (`livekit.yaml`) — đây là
   thay đổi **cấu hình**, không phải viết lại kiến trúc, vì LiveKit đã hỗ
   trợ sẵn.
3. **Load balancer đặt trước các node LiveKit** (ví dụ nginx) để phân phối
   kết nối WebSocket signaling nội bộ của LiveKit — cần thiết một khi có
   ≥2 node.

---

## 3. Signaling WebSocket (Spring Boot) — giới hạn ở việc chạy nhiều instance

### Cấu trúc hiện tại

`MeetingRuntimeRegistry`/`MeetingRuntime` (trạng thái ai-đang-ở-phòng-nào)
là **`Map` trong bộ nhớ của một JVM duy nhất** (xem
[signaling.md](../networking/signaling.md#concurrency-model)).
`RoomCommandDispatcher` dùng virtual thread (Java 21) — bản thân cơ chế
concurrency này **không phải điểm nghẽn**: virtual thread xử lý hàng chục
nghìn kết nối đồng thời trên một JVM thoải mái, 10.000 kết nối WebSocket
tổng cộng ở quy mô mục tiêu vẫn nằm trong khả năng của một máy chủ tốt.

**Điểm nghẽn thật sự**: vì trạng thái nằm trong bộ nhớ một JVM, **không
thể chạy nhiều instance backend song song** để phân tải và chống lỗi đơn
điểm — hai người trong cùng phòng nối vào hai instance khác nhau sẽ không
thấy nhau.

### Kế hoạch

- **Sticky routing theo `meetingId`** ở tầng load balancer (ví dụ
  consistent hashing trên `meetingId` trong URL `/ws/meetings/{meetingId}`)
  — mỗi cuộc họp luôn về đúng một instance backend cố định trong suốt thời
  gian sống của nó. Đây là cách rẻ nhất để chạy nhiều instance mà
  **không cần sửa `RoomCommandDispatcher`/`MeetingRuntime` chút nào** — một
  phòng 100 người vẫn nằm gọn trên một JVM (không cần chia nhỏ trong một
  phòng), chỉ cần phân phối *giữa các phòng khác nhau* ra nhiều máy.
- Đây là lý do thiết kế "một JVM tự xử lý mọi phòng, không thread cố định
  cho từng phòng" (đã có sẵn từ Milestone 3) hóa ra lại rất hợp với hướng
  scale-out này — không cần một hệ thống pub/sub phân tán (Redis) cho phần
  signaling, miễn còn dùng sticky routing theo phòng.

---

## 4. Database (PostgreSQL)

### Cấu trúc hiện tại

- Một Hikari connection pool, **không cấu hình `maximum-pool-size`** →
  dùng mặc định của Hikari (10 connection). Ở 100 cuộc họp đang hoạt động
  cùng lúc với lưu lượng ghi (join/leave/chat) liên tục, 10 connection là
  quá ít, request sẽ phải chờ connection rảnh.
- `meeting_participants.meeting_id` và `chat_messages.meeting_id` (khóa
  ngoại) **không có index tường minh** — Postgres không tự tạo index cho
  cột khóa ngoại (chỉ tự tạo cho khóa chính/unique). Hai bảng này là
  append-only (không bao giờ xóa dòng cũ), nên càng chạy lâu càng phình,
  và `findByMeetingIdAndLeftAtIsNull`/lấy lịch sử chat sẽ chuyển từ
  index scan sang sequential scan nếu không có index.

### Kế hoạch

1. Tăng `maximum-pool-size` (ví dụ 30–50, đo thực tế bằng tải giả lập
   trước khi chốt số) — một dòng cấu hình, không đổi code.
2. Thêm index tường minh trên `meeting_participants.meeting_id` và
   `chat_messages.meeting_id` (Hibernate hỗ trợ `@Index` trong
   `@Table(indexes = ...)`, `ddl-auto=update` sẽ tự tạo). Rủi ro thấp,
   lợi ích cao — nên làm sớm nhất trong toàn bộ kế hoạch này.

---

## 5. Livestream ingest + transcode (FFmpeg)

### Cấu trúc hiện tại

Mỗi livestream = **một tiến trình FFmpeg riêng** (`FfmpegProcessLauncher`),
encode H.264 bằng `libx264` (phần mềm, dùng CPU) — xem
[livestream.md](../networking/livestream.md). 10 livestream cùng lúc =
10 tiến trình `libx264` chạy song song, đều trên cùng một máy chủ ứng
dụng.

Encode H.264 bằng phần mềm rất tốn CPU — 10 luồng cùng lúc trên một máy
thông thường (không GPU chuyên dụng) nhiều khả năng sẽ khiến CPU quá tải,
làm giật/trễ *tất cả* các livestream cùng lúc, kể cả ảnh hưởng chéo sang
backend Spring Boot đang chạy chung máy.

### Kế hoạch

1. **Encode bằng phần cứng thay vì phần mềm**: bản FFmpeg đã cài
   (`ffmpeg -version`, xem [livestream-setup.md](../networking/livestream-setup.md))
   đã **build sẵn** hỗ trợ `nvenc` (NVIDIA), `amf` (AMD), và
   `mediafoundation` (mã hóa cứng có sẵn trên Windows) — chỉ cần đổi
   `-c:v libx264` thành `-c:v h264_nvenc` (hoặc tương ứng phần cứng máy
   chủ thật sự có) trong `FfmpegProcessLauncher`. Giảm tải CPU rất nhiều,
   không cần Docker/thư viện mới.
2. **Tách máy chủ transcode riêng khỏi máy chủ ứng dụng chính**: nếu vẫn
   không đủ dù đã dùng hardware encode, đặt một (hoặc vài) máy chuyên làm
   nhiệm vụ chạy FFmpeg, backend chính chỉ điều phối (chọn worker nào rảnh,
   forward WebSocket ingest tới worker đó) — đây là thay đổi kiến trúc lớn
   hơn, chỉ nên làm nếu đo thực tế cho thấy bước 1 không đủ.

---

## 6. Livestream delivery (phát HLS cho người xem)

### Cấu trúc hiện tại

`.m3u8`/`.ts` được `WebConfig.addResourceHandlers` phục vụ như **static
file trực tiếp từ đĩa của chính backend Spring Boot** (`/livestreams/**`).
Đây là điểm nghẽn rõ ràng nhất trong toàn bộ kế hoạch: mục đích tồn tại
của HLS là để **cacheable qua CDN/edge server**, còn phục vụ trực tiếp từ
một Tomcat instance nghĩa là:

```text id="hls-bottleneck"
1.000 viewer đồng thời × liên tục tải lại .m3u8 (poll mỗi vài giây)
                        × tải từng .ts mới (mỗi ~2 giây/segment)
→ dồn hết vào một Tomcat thread pool + một ổ đĩa + một đường băng thông
```

### Kế hoạch

1. **Đặt reverse proxy có cache trước backend** (nginx là lựa chọn phổ
   biến nhất, chạy trực tiếp trên host — không cần Docker) — cache
   `.m3u8`/`.ts` trong vài giây là đủ giảm tải rất nhiều, vì hàng trăm
   viewer của cùng một livestream đang xin *đúng cùng một file* trong
   cùng khung thời gian.
2. Nếu vượt quá khả năng một máy đơn (thực tế hiếm khi cần ở quy mô 1.000
   viewer), bước tiếp theo là đẩy segment lên một object storage/CDN thật
   sự — nhưng đây là bước sau, không cần thiết ngay để đạt mục tiêu đề ra.

---

## Thứ tự ưu tiên đề xuất

```text id="priority"
1. Index cho meeting_participants/chat_messages + tăng Hikari pool   (rủi ro thấp, lợi ích cao, làm trước tiên)
2. Giới hạn cứng Mesh theo số người (bắt buộc SFU cho phòng đông)
3. Hardware encode cho FFmpeg (đổi 1 dòng cấu hình)
4. Reverse proxy cache trước HLS output
5. Sticky routing theo meetingId (cần khi thật sự chạy ≥2 backend instance)
6. Multi-node LiveKit + Redis + simulcast (cần khi thật sự có nhiều
   phòng/người đồng thời — hạng mục phức tạp nhất, nên đo tải thật trước
   khi đầu tư)
```

Các mục 1–4 có thể làm ngay, rủi ro thấp, không đổi kiến trúc lớn. Mục 5–6
là hạ tầng multi-node thật sự — nên đo tải thực tế (giống tinh thần
Milestone 10 benchmark Mesh) trước khi quyết định quy mô cụm cần thiết,
thay vì scale trước khi biết giới hạn thật ở đâu.
