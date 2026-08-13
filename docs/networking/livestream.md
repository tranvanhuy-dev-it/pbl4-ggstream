# Livestream (Milestone 12)

**Trạng thái: đã triển khai đầy đủ — broadcaster → FFmpeg → HLS → viewer**
**công khai. Livestream là một tính năng độc lập, không tạo/dùng/đụng tới**
**bất kỳ `Meeting` nào. Xem [livestream-setup.md](./livestream-setup.md)**
**để cài FFmpeg cục bộ.**

## Livestream không phải là một cuộc họp

Ban đầu tính năng này được implement như một nút bên trong phòng họp
(broadcast luồng của meeting hiện tại), và phiên bản đầu của phần "độc
lập" vẫn ngầm tạo một `Meeting` chỉ để mượn `code`/quyền chủ phòng có sẵn.
Cả hai cách đó đều sai về mặt khái niệm: **livestream (kiểu TikTok/YouTube
Live) và meeting (kiểu Google Meet) là hai tính năng khác nhau** — một cái
là một người phát cho nhiều người xem thụ động, cái kia là nhiều người
tham gia bình đẳng vào một cuộc gọi hai chiều. Vì vậy package
`com.project.meet.livestream` **không phụ thuộc vào package `meeting`**
theo bất kỳ cách nào:

- Không tạo `Meeting`, không có `meetingId` ở đâu cả.
- `LivestreamSession` tự có `id` riêng (định danh nội bộ, chỉ broadcaster
  biết, dùng để gọi `/stop`) và `code` riêng (định danh công khai ngắn,
  dùng cho link `/watch/{code}`) — sinh bởi `LivestreamCodeGenerator`
  (cùng thuật toán với `MeetingCodeGenerator` nhưng là bản sao riêng, cố ý
  không dùng chung để hai package không phụ thuộc lẫn nhau).
- "Chủ" một livestream chỉ đơn giản là `hostUserId` — người gọi
  `POST /live/start` — không có khái niệm "meeting host" nào liên quan.

## Kiến trúc

```text id="ls-flow"
Broadcaster (trình duyệt)       Backend (Spring Boot)         FFmpeg (child process)
  │                                     │                            │
  │  POST /api/v1/live/start            │                            │
  │ ───────────────────────────────────▶│  spawn ffmpeg -i pipe:0 …  │
  │                                     │ ──────────────────────────▶│
  │  { id, code, status: LIVE, hlsUrl }  │                            │
  │ ◀───────────────────────────────────│                            │
  │                                                                   │
  │  MediaRecorder(localStream)                                      │
  │  → WebSocket nhị phân                                            │
  │  /ws/live/{id}/ingest                                            │
  │ ─────────────────────────────────────────────────────────────▶  │  ghi vào stdin
  │                                                                   │
  │                                                    ffmpeg ghi ──▶ {outputDir}/{id}/stream.m3u8
  │                                                                          + *.ts
Viewer (bất kỳ ai, không cần đăng nhập, không phải "join" gì cả)
  │  GET /api/v1/live/{code}/status  (poll mỗi 4s)
  │  → { code, title, status, hlsUrl }   (không có id — xem bên dưới)
  │  <video> + hls.js phát trực tiếp /livestreams/{id}/stream.m3u8
```

## Giới hạn có chủ đích: không phải bản ghép toàn bộ room SFU

Cách "chuẩn" trong hệ sinh thái LiveKit để lấy ra một luồng đã ghép
(composite) toàn bộ participant trong một room SFU là **LiveKit Egress** —
nhưng Egress **chỉ phân phối dưới dạng Docker image** (đã xác minh qua
GitHub API: release của `livekit/egress` không có file binary nào để tải,
khác hẳn `livekit/livekit` — xem [sfu-setup.md](./sfu-setup.md)). Vì
project này không cho phép Docker, Egress không dùng được — và vì
livestream giờ tách hẳn khỏi meeting/SFU, điều này không còn là giới hạn
quan trọng nữa: livestream ở đây **luôn luôn** chỉ là luồng camera/mic của
một broadcaster, không bao giờ có ý định ghép nhiều người — đúng với mô
hình TikTok/YouTube Live thật (một người phát, nhiều người xem).

## Vì sao ingest qua WebSocket nhị phân, không qua REST

`MediaRecorder` tạo ra một luồng byte **liên tục** (mỗi `ondataavailable`
là một mảnh của cùng một file WebM đang ghi dở, không phải một file độc
lập tự giải mã được) — vừa khớp với việc FFmpeg đọc từ một `stdin` duy
nhất (`pipe:0`). WebSocket giữ đúng thứ tự byte và không có overhead của
từng request HTTP riêng lẻ cho mỗi mảnh nhỏ (~1 giây/mảnh).

Endpoint ingest (`LivestreamIngestWebSocketHandler`, đường dẫn
`/ws/live/{id}/ingest`) dùng `LivestreamHandshakeInterceptor` riêng để xác
thực (JWT qua `?token=`, cùng quy ước với WebSocket signaling của meeting)
— nhưng **không** dùng chung `SignalingHandshakeInterceptor`, vì
interceptor đó tra cứu `Meeting` trong lúc handshake, còn livestream không
có `Meeting` nào để tra cứu cả. Interceptor riêng chỉ xác thực JWT và lấy
`id` từ path; việc `id` đó có đúng là một livestream đang chạy hay không,
và người gọi có đúng là chủ của nó không, được kiểm tra ngay sau đó trong
`LivestreamIngestWebSocketHandler.afterConnectionEstablished`.

## Vì sao trạng thái livestream không lưu DB

`LivestreamSession`/`LivestreamRegistry` (package `livestream.application`)
hoàn toàn trong bộ nhớ — cùng triết lý với `MeetingRuntime` cho signaling
presence (xem
[database/schema.md](../database/schema.md#what-is-not-persisted)):
một livestream không còn giá trị gì sau khi kết thúc (không có tính năng
xem lại/VOD ở milestone này), nên không đáng lưu trữ lâu dài. Hệ quả trực
tiếp: `GET /live/{code}/status` cho một `code` **chưa từng tồn tại** và
cho một `code` **đã từng live nhưng đã dừng** trả về **y hệt nhau**
(`ENDED`, `200 OK`, không phải `404`) — vì không có gì được lưu lại để
phân biệt hai trường hợp đó, và cả hai đều có ý nghĩa như nhau với người
xem: "không có gì để xem ở đây bây giờ".

## `id` không bao giờ lộ ra cho người xem công khai

`LivestreamStatusResponse.id` chỉ có giá trị trong response của
`POST /live/start` — đây là "chìa khóa" duy nhất để gọi `/live/{id}/stop`
hay kết nối WebSocket ingest. Endpoint công khai
`GET /live/{code}/status` luôn trả `id: null`, để một người xem ẩn danh
không có cách nào tự ý dừng livestream của người khác dù biết `code` công
khai của họ.

## Lời gọi mạng tới FFmpeg là best-effort, việc sinh id/code thì không

Khởi động FFmpeg (`ProcessBuilder`) có thể thất bại (thiếu binary, sai
đường dẫn) — `StartLivestreamUseCase` để lỗi đó nổi lên như một lỗi thật
(`500 INTERNAL_ERROR`), vì nếu FFmpeg không khởi động được thì đơn giản là
không có gì để livestream, khác với TURN/SFU nơi có STUN/Mesh làm
fallback. Sinh `id`/`code` là phép tính cục bộ (UUID ngẫu nhiên + ký tự
ngẫu nhiên), không bao giờ là nguồn lỗi.
