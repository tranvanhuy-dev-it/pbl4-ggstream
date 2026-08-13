# Cài đặt LiveKit (SFU) cục bộ (không dùng Docker)

**Trạng thái: đã tải và chạy thử `livekit-server.exe` cục bộ (Milestone 11);**
**tích hợp backend/frontend đang được triển khai theo từng bước.**

## Vì sao chọn LiveKit thay vì mediasoup

`mediasoup` — lựa chọn SFU phổ biến nhất cho Node.js — có phần lõi xử lý RTP
viết bằng:

```text
C++ (libmediasoup-worker)
```

Phần này được compile ngay trong lúc:

```text
npm install
```

thông qua:

```text
Meson + Ninja
```

Trên Windows, việc này đòi hỏi cài sẵn:

```text
Visual Studio Build Tools (C++ workload)
```

— khoảng 3–6GB, cài một lần nhưng rủi ro lỗi môi trường cao.

Máy development của project này không có sẵn:

```text
MSVC C++ Build Tools
Go toolchain
```

**LiveKit** giải quyết vấn đề này bằng cách phát hành sẵn một binary Windows
chính thức:

```text
livekit_<version>_windows_amd64.zip
```

chứa:

```text
livekit-server.exe
```

Không cần compile, không cần Docker — chạy trực tiếp trên host, đúng tinh
thần "không Docker" của project này, tương tự cách PostgreSQL đã được cài
đặt trực tiếp.

Đánh đổi: LiveKit là một binary đóng gói sẵn (Go), không có mã nguồn RTP
forwarding tự viết để trình bày trong báo cáo — phần được trình bày là
cách **tích hợp và điều khiển đúng** một SFU production-grade (vòng đời
room/token, lựa chọn giữa Mesh và SFU), không phải cách tự xây một SFU.

## 1. Tải binary

Đã tải sẵn về:

```text
services/sfu/bin/livekit-server.exe
```

(phiên bản 1.13.5, tải từ GitHub Releases của `livekit/livekit`). Thư mục
này nằm trong `.gitignore` — mỗi máy tự tải lại khi cần, giống cách
`node_modules/` không được commit.

Nếu cần tải lại từ đầu:

```powershell
# Tải file zip cho Windows amd64 từ:
# https://github.com/livekit/livekit/releases/latest
# rồi giải nén livekit-server.exe vào services/sfu/bin/
```

## 2. Sinh API key/secret

```powershell
services\sfu\bin\livekit-server.exe generate-keys
```

Lệnh này in ra một cặp:

```text
API Key:    APIxxxxxxxxxxxxx
API Secret: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## 3. Tạo file cấu hình

Copy file mẫu:

```powershell
copy services\sfu\livekit.yaml.example services\sfu\livekit.yaml
```

rồi thay hai placeholder trong mục `keys:` bằng cặp key/secret vừa sinh ở
bước 2.

```text
services/sfu/livekit.yaml
```

nằm trong `.gitignore` — đây là nơi duy nhất secret thật được lưu, không
commit lên git, đúng quy ước `.env`/`.env.example` đã dùng cho toàn bộ
project.

## 4. Cấu hình backend

Thêm vào `apps/server/.env` (cùng cặp key/secret ở bước 2):

```text
LIVEKIT_URL=ws://localhost:7880
LIVEKIT_API_KEY=APIxxxxxxxxxxxxx
LIVEKIT_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Secret này chỉ được đọc ở backend để ký access token cho từng participant
(`IssueSfuTokenUseCase`) — không bao giờ gửi thẳng secret cho frontend, chỉ
gửi token đã ký (ngắn hạn, gắn với một room + một user cụ thể). Cùng
nguyên tắc với `TURN_SECRET` — xem
[ice-stun-turn.md](./ice-stun-turn.md#turn-credentials-milestone-5).

## 5. Chạy server

```powershell
services\sfu\bin\livekit-server.exe --config services\sfu\livekit.yaml
```

Server mặc định lắng nghe:

```text
HTTP/WebSocket:  7880
RTC (TCP):       7881
RTC (UDP):       50000-60000
```

Kiểm tra đã chạy:

```bash
curl http://localhost:7880/
```

trả về `HTTP 200` (LiveKit healthz) là server đã sẵn sàng nhận kết nối.

## 6. `rtc.use_external_ip`

Được bật trong `livekit.yaml.example` — LiveKit tự dùng STUN để tìm địa chỉ
public của chính server mình, cần thiết để client bên ngoài mạng LAN nhận
được một candidate có thể kết nối tới. Đây là vấn đề NAT-traversal tương tự
lý do TURN tồn tại cho Mesh, chỉ khác là áp dụng cho chính SFU thay vì cho
từng cặp peer.

Nếu chỉ test trong cùng mạng LAN, có thể tắt tùy chọn này để giảm độ trễ
khởi động, nhưng để mặc định bật vẫn hoạt động bình thường.

## Multi-node + Redis

**Trạng thái: chưa triển khai — mục này là tài liệu hướng dẫn, không phải**
**hạ tầng đang chạy.** Xem lý do cần trong
[architecture/scalability-plan.md](../architecture/scalability-plan.md)
(mục 2): một `livekit-server.exe` duy nhất là điểm lỗi đơn (single point of
failure) cho toàn bộ 100 cuộc họp đồng thời, và mọi log khởi động hiện tại
đều in `"using single-node routing"` — nghĩa là LiveKit tự nhận diện chưa
có Redis nên chạy chế độ 1 node.

### Vì sao cần Redis

Nhiều tiến trình `livekit-server.exe` **không** tự biết về nhau nếu chỉ
chạy song song — mỗi tiến trình có bộ nhớ riêng. Redis đóng vai trò trạng
thái dùng chung: node nào đang giữ room nào, participant nào đang ở node
nào — để một client kết nối vào bất kỳ node nào trong cụm cũng tới đúng
room, dù room đó đang thực sự chạy trên node khác.

### Cài Redis trên Windows (không Docker)

Hai lựa chọn, cùng tinh thần đã áp dụng cho Coturn
([turn-setup.md](./turn-setup.md)):

1. **WSL2** — cài Redis native bên trong (`sudo apt install redis-server`),
   không phải container, không phải Docker image.
2. **Memurai** — bản build Redis-compatible chạy native trên Windows
   (không cần WSL2).

Mặc định Redis lắng nghe `localhost:6379` — khớp giá trị mẫu trong
`services/sfu/livekit-cluster.yaml.example`.

### Tự kiểm tra multi-node cục bộ (2 node trên cùng máy)

```powershell
copy services\sfu\livekit-cluster.yaml.example services\sfu\livekit-node1.yaml
copy services\sfu\livekit-cluster.yaml.example services\sfu\livekit-node2.yaml
# sửa livekit-node2.yaml: port: 7890 (node1 giữ nguyên port: 7880)
# cả hai file đều trỏ cùng redis.address, cùng cặp key/secret

services\sfu\bin\livekit-server.exe --config services\sfu\livekit-node1.yaml
services\sfu\bin\livekit-server.exe --config services\sfu\livekit-node2.yaml
```

Kết nối một client tới node 1, một client khác tới node 2 nhưng cùng
`roomName` — nếu cả hai thấy nhau trong cùng room, cụm đang hoạt động
đúng. Production thật cần thêm một load balancer (nginx/haproxy) đặt
trước cả cụm — `apps/server`'s `LIVEKIT_URL` chỉ nên trỏ vào load
balancer đó, không trỏ thẳng vào một node cụ thể.
