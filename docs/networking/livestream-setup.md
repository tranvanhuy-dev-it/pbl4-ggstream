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
