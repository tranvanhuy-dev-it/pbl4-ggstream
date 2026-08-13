# Livestream (Milestone 12)

**Trạng thái: đã triển khai đầy đủ — broadcaster (host) → FFmpeg → HLS →**
**viewer công khai. Xem [livestream-setup.md](./livestream-setup.md) để**
**cài FFmpeg cục bộ.**

## Kiến trúc

```text id="ls-flow"
Host (trình duyệt)              Backend (Spring Boot)         FFmpeg (child process)
  │                                     │                            │
  │  POST /livestream/start             │                            │
  │ ───────────────────────────────────▶│  spawn ffmpeg -i pipe:0 …  │
  │                                     │ ──────────────────────────▶│
  │  { status: LIVE, hlsUrl }            │                            │
  │ ◀───────────────────────────────────│                            │
  │                                                                   │
  │  MediaRecorder(localStream)                                      │
  │  → WebSocket nhị phân                                            │
  │  /ws/meetings/{id}/livestream-ingest                             │
  │ ─────────────────────────────────────────────────────────────▶  │  ghi vào stdin
  │                                                                   │
  │                                                    ffmpeg ghi ──▶ {outputDir}/{id}/stream.m3u8
  │                                                                          + *.ts
Viewer (bất kỳ ai, không cần đăng nhập)
  │  GET /api/v1/meetings/code/{code}/livestream  (poll mỗi 4s)
  │  → { status, hlsUrl }
  │  <video> + hls.js phát trực tiếp /livestreams/{id}/stream.m3u8
```

## Giới hạn có chủ đích: không phải bản ghép toàn bộ room SFU

Cách "chuẩn" trong hệ sinh thái LiveKit để lấy ra một luồng đã ghép
(composite) toàn bộ participant trong room là **LiveKit Egress** — nhưng
Egress **chỉ phân phối dưới dạng Docker image** (đã xác minh qua GitHub
API: release của `livekit/egress` không có file binary nào để tải, khác
hẳn `livekit/livekit` — xem [sfu-setup.md](./sfu-setup.md)). Vì project
này không cho phép Docker, Egress không dùng được.

Vì vậy livestream ở đây phát đúng **luồng cục bộ của người bắt đầu
livestream** (`localStream` — camera/mic hiện tại của họ trong
`MeetingRoomView`), không phải hình ảnh ghép của mọi người trong cuộc họp.
Đây là đánh đổi có chủ đích, không phải thiếu sót — được ghi lại rõ ràng ở
đây để không ai nhầm tưởng đây là bản ghép room SFU thật.

## Vì sao ingest qua WebSocket nhị phân, không qua REST

`MediaRecorder` tạo ra một luồng byte **liên tục** (mỗi `ondataavailable`
là một mảnh của cùng một file WebM đang ghi dở, không phải một file độc
lập tự giải mã được) — vừa khớp với việc FFmpeg đọc từ một `stdin` duy
nhất (`pipe:0`). WebSocket giữ đúng thứ tự byte và không có overhead của
từng request HTTP riêng lẻ cho mỗi mảnh nhỏ (~1 giây/mảnh).

Endpoint ingest (`LivestreamIngestWebSocketHandler`) dùng lại nguyên
`SignalingHandshakeInterceptor` để xác thực (JWT qua `?token=`, giống hệt
WebSocket signaling) — chỉ khác là chấp nhận **binary frame** thay vì JSON
envelope, và chỉ cho phép đúng người đã gọi `/livestream/start` (chủ
phòng) ghi vào tiến trình FFmpeg của chính họ.

## Vì sao trạng thái livestream không lưu DB

`LivestreamSession`/`LivestreamRegistry` (package `livestream.application`)
hoàn toàn trong bộ nhớ — cùng triết lý với `MeetingRuntime` cho signaling
presence (xem
[database/schema.md](../database/schema.md#what-is-not-persisted)):
một livestream không còn giá trị gì sau khi kết thúc (không có tính năng
xem lại/VOD ở milestone này), nên không đáng lưu trữ lâu dài. Tiến trình
FFmpeg + đường dẫn output là trạng thái runtime thuần túy.

## Lời gọi mạng tới FFmpeg là best-effort, việc ký token thì không

Khởi động FFmpeg (`ProcessBuilder`) có thể thất bại (thiếu binary, sai
đường dẫn) — `StartLivestreamUseCase` để lỗi đó nổi lên như một lỗi thật
(`500 INTERNAL_ERROR`), vì nếu FFmpeg không khởi động được thì đơn giản là
không có gì để livestream, khác với TURN/SFU nơi có STUN/Mesh làm
fallback. Không có "chế độ suy giảm" nào hợp lý cho livestream cả.
