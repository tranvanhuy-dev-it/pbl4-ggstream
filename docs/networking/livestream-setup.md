# Cài đặt FFmpeg cục bộ (không dùng Docker)

**Trạng thái: FFmpeg đã cài và xác minh chạy được (Milestone 12); tích hợp**
**backend/frontend đang được triển khai theo từng bước.**

## Vì sao cần FFmpeg, và vì sao không dùng LiveKit Egress

Cách "chuẩn" để lấy luồng đã được ghép (composite) từ một room LiveKit ra
ngoài là **LiveKit Egress** — nhưng Egress **chỉ được phân phối dưới dạng**
**Docker image** (đã xác minh qua GitHub API: release của
`livekit/egress` không có file binary tải về, khác hẳn `livekit/livekit`
tự nó). Vì project này không cho phép Docker ở bất cứ đâu, Egress không
dùng được.

Thay vào đó, trình duyệt của người bắt đầu livestream (thường là chủ
phòng) tự ghi lại đúng luồng cục bộ của họ (camera/mic và/hoặc màn hình
đang chia sẻ) bằng `MediaRecorder`, gửi từng đoạn (chunk) qua WebSocket
tới backend, và backend đẩy các byte đó thẳng vào **FFmpeg** — FFmpeg
chuyển mã sang H.264/AAC rồi cắt thành HLS (`.m3u8` + các file `.ts`) để
trình duyệt khác xem được **mà không cần WebRTC**.

Giới hạn cần biết rõ: đây là livestream **một luồng cục bộ**, không phải
bản ghép hình ảnh của toàn bộ participant trong room SFU — vì công cụ duy
nhất làm được việc ghép đó mà không cần trình duyệt (LiveKit Egress) lại
yêu cầu Docker.

## 1. Cài đặt

Cài qua winget (đã xác minh hoạt động):

```powershell
winget install --id Gyan.FFmpeg -e
```

FFmpeg được cài vào:

```text
%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0-full_build\bin\ffmpeg.exe
```

winget có thêm một "App Execution Alias" tên `ffmpeg` vào PATH, nhưng alias
này chỉ có hiệu lực ở phiên shell mới (mở terminal mới) — nếu backend chạy
trong một tiến trình đã mở từ trước khi cài, `ffmpeg` trên PATH sẽ chưa
nhận được. Vì vậy nên cấu hình đường dẫn tuyệt đối thay vì dựa vào PATH.

Kiểm tra đã cài đúng:

```powershell
ffmpeg -version
```

(mở terminal mới nếu lệnh trên báo "not recognized"). Bản cài kèm sẵn
`libx264` (H.264) và `aac`/`libopus` — đủ cho pipeline HLS của Milestone
12, không cần cài thêm codec riêng.

## 2. Cấu hình backend

Thêm vào `apps/server/.env` (dùng đường dẫn tuyệt đối tới `ffmpeg.exe` cho
chắc chắn, thay vì chỉ ghi `ffmpeg` và trông chờ vào PATH):

```text
LIVESTREAM_FFMPEG_PATH=C:\Users\<user>\AppData\Local\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0-full_build\bin\ffmpeg.exe
LIVESTREAM_OUTPUT_DIR=./livestream-output
```

`LIVESTREAM_OUTPUT_DIR` là nơi FFmpeg ghi `.m3u8`/`.ts` — thư mục này nằm
trong `.gitignore`, nội dung hoàn toàn tạm thời (mất khi dừng livestream),
không phải dữ liệu cần lưu trữ lâu dài.

Nếu không cấu hình `LIVESTREAM_FFMPEG_PATH`, mặc định là `ffmpeg` (dựa vào
PATH) — chỉ hoạt động nếu App Execution Alias của winget đã có hiệu lực
trong phiên chạy backend.

## 3. Chuyển sang mã hóa bằng phần cứng (tùy chọn)

**Trạng thái: hỗ trợ sẵn qua cấu hình — mặc định vẫn dùng phần mềm.** Xem
[architecture/scalability-plan.md](../architecture/scalability-plan.md)
(mục 4): mã hóa H.264 bằng phần mềm (`libx264`) rất tốn CPU — 10 livestream
chạy cùng lúc nhiều khả năng làm CPU quá tải trên máy không có GPU mạnh.

Bản FFmpeg đã cài (winget `Gyan.FFmpeg`) đã build sẵn `nvenc` (NVIDIA),
`amf` (AMD), và `mediafoundation` (mã hóa cứng có sẵn trên mọi máy Windows,
không cần GPU rời) — không cần cài lại gì, chỉ cần đổi biến môi trường
`LIVESTREAM_VIDEO_CODEC_ARGS` trong `apps/server/.env`:

```text
# Mặc định — phần mềm, chạy được trên mọi máy:
LIVESTREAM_VIDEO_CODEC_ARGS=-c:v libx264 -preset veryfast

# NVIDIA:
LIVESTREAM_VIDEO_CODEC_ARGS=-c:v h264_nvenc -preset p4

# AMD:
LIVESTREAM_VIDEO_CODEC_ARGS=-c:v h264_amf

# Windows Media Foundation (không cần GPU rời):
LIVESTREAM_VIDEO_CODEC_ARGS=-c:v h264_mf
```

`FfmpegProcessLauncher` tách chuỗi này theo khoảng trắng và chèn thẳng vào
danh sách tham số `ffmpeg` — không giới hạn chỉ 2 tham số, có thể thêm bất
kỳ cờ nào FFmpeg hỗ trợ cho encoder tương ứng.

## 4. Reverse proxy cache cho HLS output (khi có nhiều viewer)

**Trạng thái: cấu hình mẫu, chưa triển khai thật.** Xem
[architecture/scalability-plan.md](../architecture/scalability-plan.md)
(mục 6): phục vụ `.m3u8`/`.ts` trực tiếp từ đĩa qua chính Spring Boot
(`WebConfig.addResourceHandlers`, đường dẫn `/livestreams/**`) chỉ ổn với
lượng viewer nhỏ — hàng trăm viewer cùng poll đúng segment trong cùng
khung thời gian nên dồn vào một cache ở lớp trước, thay vì để mỗi request
đều chạm tới Tomcat/đĩa.

File cấu hình mẫu: `services/livestream/nginx-hls.conf.example` — nginx
cache `.ts` gần như vĩnh viễn (segment không đổi sau khi ghi xong) và
`.m3u8` trong ~2 giây (khớp `-hls_time 2` của FFmpeg, đủ để hàng trăm
viewer không dồn hết vào backend nhưng vẫn thấy segment mới kịp thời).

Cài nginx cho Windows (có bản native, không cần Docker):

```powershell
# Tải bản Windows từ https://nginx.org/en/download.html, giải nén, rồi:
copy services\livestream\nginx-hls.conf.example <thư mục nginx>\conf\hls.conf
# include hls.conf trong nginx.conf chính, hoặc dùng thẳng làm server block
nginx.exe
```

Sau khi chạy, viewer trỏ tới `http://localhost:8081` (cổng nginx trong file
mẫu) thay vì thẳng cổng 8080 của backend.
