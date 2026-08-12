# Cài đặt Coturn cục bộ (không dùng Docker)

**Trạng thái: cơ chế cấp TURN credential ở backend/frontend đã được triển khai và**
**đang hoạt động; tuy nhiên Coturn server hiện vẫn chưa được cài đặt trong môi trường**
**development của project này**.

Xem thêm [ice-stun-turn.md](./ice-stun-turn.md) để biết lý do: STUN đã đủ cho việc
test trong cùng mạng, còn TURN chỉ thực sự cần khi test các trường hợp symmetric NAT
hoặc gọi giữa các mạng khác nhau.

Endpoint:

```text
GET /api/v1/rtc/ice-servers
```

đã được thiết kế để tự động fallback về chế độ chỉ dùng STUN nếu:

```text
TURN_HOST
TURN_SECRET
```

chưa được cấu hình.

Vì vậy project vẫn hoạt động bình thường dù Coturn chưa được cài đặt.

## Tại sao dùng Coturn, và tại sao không dùng Docker

Coturn là implementation TURN/STUN server mã nguồn mở được sử dụng rất phổ biến
trong các hệ thống WebRTC production.

Đây là một service Linux native và không có bản build Windows chính thức.

Nếu máy development đang sử dụng Windows, có hai lựa chọn thực tế:

1. **Chạy Coturn trong WSL2** (Windows Subsystem for Linux).

   WSL2 không phải container mà là một môi trường Linux nhẹ được tích hợp vào Windows.

   Việc sử dụng WSL2 không được xem là "dùng Docker" theo quy ước của project này:
   - không có Docker image
   - không có image layer
   - không có container orchestration
   - không có `docker-compose.yml`

   Về mặt development, nó tương tự như việc cài PostgreSQL trực tiếp,
   chỉ khác là Coturn chạy trong Linux subsystem thay vì chạy native trên Windows.

2. **Deploy Coturn lên một Linux host/VPS thực tế** nếu có.

   Đây là phương án gần với production nhất và cũng là phương án mà phần cấu hình
   bên dưới giả định.

## 1. Cài đặt

Trên Debian/Ubuntu, bao gồm cả Ubuntu trong WSL2:

```bash
sudo apt update
sudo apt install coturn
```

Trên Fedora/RHEL:

```bash
sudo dnf install coturn
```

Kiểm tra Coturn đã được cài đặt:

```bash
turnserver --version
```

## 2. Cấu hình

Chỉnh sửa file:

```text
/etc/turnserver.conf
```

Cấu hình tối thiểu tương thích với backend hiện tại
(`TurnCredentialService`, các property `app.turn.*`):

```ini
# Port TURN/STUN tiêu chuẩn
listening-port=3478

# Public IP của máy này hoặc địa chỉ mà browser có thể truy cập được.
# Nếu server nằm trong mạng gia đình phía sau router thì cần cấu hình
# port forwarding — xem bước 4.
external-ip=YOUR_PUBLIC_OR_REACHABLE_IP

# Sử dụng cơ chế xác thực credential có thời hạn.
#
# Cơ chế này khớp chính xác với TurnCredentialService:
#
# HMAC-SHA1(secret, "expiryTimestamp:userId")
#
# Đây là "REST API authentication mode" được Coturn hỗ trợ sẵn,
# không phải custom protocol do project tự định nghĩa.
use-auth-secret
static-auth-secret=REPLACE_WITH_THE_SAME_VALUE_AS_TURN_SECRET_IN_.ENV

# Realm là bắt buộc trong chế độ authentication này.
# Giá trị cụ thể không quá quan trọng, miễn là được cấu hình nhất quán.
realm=meet-platform.local

# Dải relay port.
#
# Đây là các port Coturn thực sự sử dụng để forward media khi một
# connection phải sử dụng TURN relay.
#
# Giữ range tương đối nhỏ để việc cấu hình firewall ở bước 4 đơn giản hơn.
min-port=49152
max-port=49452

# Ghi log vào file thông thường thay vì syslog để dễ theo dõi khi test.
log-file=/var/log/turnserver.log
verbose

# Không relay traffic tới/từ các địa chỉ loopback/private của chính server.
#
# Đây là một biện pháp hardening tiêu chuẩn nhằm tránh Coturn bị lợi dụng
# như một open relay để truy cập vào mạng nội bộ của server.
no-loopback-peers
no-multicast-peers
```

Tạo một secret ngẫu nhiên:

```bash
openssl rand -base64 32
```

Sử dụng **cùng một giá trị** cho:

```text
static-auth-secret
```

trong `/etc/turnserver.conf` và:

```text
TURN_SECRET
```

trong:

```text
apps/server/.env
```

Ví dụ:

```text
Coturn:
static-auth-secret=abc123...

Spring Boot:
TURN_SECRET=abc123...
```

Hai giá trị này phải giống nhau.

## 3. Chạy Coturn

Chạy trực tiếp:

```bash
sudo turnserver -c /etc/turnserver.conf
```

Hoặc chạy dưới dạng service thường trực bằng systemd
(trên hầu hết Linux distribution có sẵn unit file):

```bash
sudo systemctl enable --now coturn
```

Kiểm tra trạng thái:

```bash
sudo systemctl status coturn
```

Theo dõi log:

```bash
tail -f /var/log/turnserver.log
```

## 4. Mở các port cần thiết

Coturn tối thiểu cần:

- **UDP + TCP 3478** — STUN/TURN control port, được sử dụng trong quá trình
  candidate gathering và TURN allocation.
- **UDP 49152–49452** — hoặc range tương ứng với `min-port` / `max-port`
  đã cấu hình — dùng để relay media thực tế.

Ví dụ với `ufw`:

```bash
sudo ufw allow 3478/tcp
sudo ufw allow 3478/udp
sudo ufw allow 49152:49452/udp
```

Nếu Coturn chạy trên một máy nằm phía sau router gia đình:

```text
Internet
   │
   ▼
Router
   │
   ▼
Coturn Server
```

thì ngoài firewall ở hệ điều hành, bạn còn phải cấu hình **port forwarding**
trên router cho:

```text
3478 TCP
3478 UDP
49152-49452 UDP
```

đến máy đang chạy Coturn.

## 5. Cấu hình backend kết nối tới Coturn

Trong:

```text
apps/server/.env
```

thêm:

```env
TURN_HOST=your-coturn-host-or-ip
TURN_PORT=3478
TURN_SECRET=the-same-secret-from-static-auth-secret-above
TURN_CREDENTIAL_TTL_SECONDS=3600
```

Ví dụ:

```env
TURN_HOST=203.0.113.10
TURN_PORT=3478
TURN_SECRET=your-secret
TURN_CREDENTIAL_TTL_SECONDS=3600
```

Sau đó restart backend.

Endpoint authenticated:

```text
GET /api/v1/rtc/ice-servers
```

bây giờ phải trả về cả STUN và TURN server.

Ví dụ:

```json
{
  "iceServers": [
    {
      "urls": ["stun:stun.l.google.com:19302"]
    },
    {
      "urls": ["turn:203.0.113.10:3478"],
      "username": "1786543200:user-id",
      "credential": "generated-hmac-credential"
    }
  ]
}
```

`username` và `credential` sẽ được backend tạo mới với thời hạn nhất định.

## 6. Kiểm tra relay candidate có thực sự hoạt động

Cách đáng tin cậy nhất để kiểm tra TURN là buộc cuộc gọi chỉ sử dụng
**relay candidate**.

Lý do là ICE luôn ưu tiên đường truyền trực tiếp nếu có thể:

```text
host
  ↓
server-reflexive (STUN)
  ↓
relay (TURN)
```

Do đó một cuộc gọi bình thường thành công **không chứng minh TURN đang hoạt động**.

Nó có thể chỉ đang sử dụng:

```text
host
```

hoặc:

```text
srflx
```

candidate.

### Kiểm tra nhanh bằng `turnutils_uclient`

`turnutils_uclient` được cài cùng Coturn.

Có thể chạy:

```bash
turnutils_uclient -u dummy -w dummy -y your-coturn-host
```

Đây chỉ là kiểm tra connectivity cơ bản.

Một TURN allocation đầy đủ cần credential HMAC thực tế, vì vậy test trực tiếp
từ browser thường đại diện cho hệ thống thật tốt hơn.

### Kiểm tra từ browser

Sử dụng:

[Trickle ICE](https://webrtc.github.io/samples/src/content/peerconnection/trickle-ice/)

Thêm một STUN server và một TURN server.

TURN credential lấy bằng cách gọi:

```text
GET /api/v1/rtc/ice-servers
```

với JWT hợp lệ.

Ví dụ có thể gọi API bằng `curl`, sau đó copy:

```text
username
credential
```

từ response.

Sau khi bắt đầu gather ICE candidate, kiểm tra danh sách candidate.

Nếu chỉ thấy:

```text
host
srflx
```

thì TURN relay chưa được sử dụng hoặc chưa hoạt động.

Nếu thấy:

```text
relay
```

thì browser đã tạo thành công TURN relay candidate.

Đó là bằng chứng tốt nhất rằng:

```text
Browser
   │
   ▼
Coturn
   │
   ▼
relay candidate
```

đang hoạt động đúng.

## Thời hạn credential

Credential do `TurnCredentialService` tạo sẽ hết hạn sau:

```text
TURN_CREDENTIAL_TTL_SECONDS
```

Giá trị mặc định:

```text
3600 giây
```

tức:

```text
1 giờ
```

Backend tạo username theo dạng:

```text
<expiryUnixTimestamp>:<userId>
```

Ví dụ:

```text
1786543200:550e8400-e29b-41d4-a716-446655440000
```

Credential được tính:

```text
Base64(
    HMAC-SHA1(
        TURN_SECRET,
        "<expiryUnixTimestamp>:<userId>"
    )
)
```

Coturn nhận:

```text
username
credential
```

từ browser và tự tính lại HMAC bằng:

```text
static-auth-secret
```

của chính nó.

Nếu kết quả trùng khớp và timestamp vẫn chưa hết hạn:

```text
credential valid
```

Nếu timestamp đã hết hạn:

```text
credential invalid
```

Do đó không cần có database dùng chung giữa Spring Boot và Coturn.

Kiến trúc authentication:

```text
                   cùng shared secret
                 ┌────────────────────┐
                 │                    │
                 ▼                    ▼
          Spring Boot              Coturn
              │                       │
     tạo credential             verify credential
              │                       ▲
              ▼                       │
            Browser ──────────────────┘
```

Không có TURN credential store cần quản lý hoặc revoke ở cả hai phía.

Chỉ cần đảm bảo:

```text
apps/server/.env
TURN_SECRET
```

và:

```text
/etc/turnserver.conf
static-auth-secret
```

luôn sử dụng cùng một secret.
