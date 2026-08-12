# ICE, STUN, TURN và NAT Traversal

**Trạng thái: STUN đã được triển khai (Milestone 4). Cơ chế cấp TURN credential đã**
**được triển khai (Milestone 5), nhưng hiện tại chưa có Coturn server nào được**
**deploy trong môi trường development của project — xem**
**[turn-setup.md](./turn-setup.md#status-backendfrontend-turn-credential-plumbing-is-implemented-and-active)**
**để biết vì sao đây là một bước được chủ động trì hoãn và hiện tại chưa cần thiết.**

## Vấn đề: NAT

Hầu hết thiết bị, kể cả khi nằm trong cùng mạng gia đình hoặc văn phòng, đều ở phía sau NAT
(Network Address Translation) — chúng không có một địa chỉ IP public mà phần còn lại của
Internet có thể truy cập trực tiếp.

Hai browser nằm trên hai mạng khác nhau không thể đơn giản kết nối tới địa chỉ local của nhau
(`192.168.x.x`, `10.x.x.x`); mỗi bên chỉ biết địa chỉ _private_ của chính mình, chứ không biết
địa chỉ _public_ sau khi được NAT ánh xạ mà phía còn lại cần dùng để kết nối.

## STUN: khám phá địa chỉ public của chính mình

Nhiệm vụ duy nhất của một STUN server
(Session Traversal Utilities for NAT) là: client gửi cho server một UDP packet,
sau đó server trả lời rằng:

> "Đây là IP:port public mà tôi nhìn thấy packet này được gửi tới từ."

Đó chính là địa chỉ của client sau khi được NAT ánh xạ — một
**server-reflexive candidate**.

Browser sẽ thu thập candidate này cùng với candidate local của chính nó
(**host candidate**) và gửi cả hai sang peer còn lại thông qua signaling.

Sau đó ICE sẽ thử tất cả các tổ hợp candidate có thể có:

```text
host ↔ host
host ↔ srflx
srflx ↔ srflx
```

và chọn cặp candidate nào thực sự hoàn thành được connectivity check.

Project này mặc định sử dụng STUN server public của Google:

```text
stun:stun.l.google.com:19302
```

có thể cấu hình thông qua:

```text
NEXT_PUBLIC_STUN_URL
```

và được định nghĩa trong:

```text
lib/webrtc/iceServers.ts
```

STUN miễn phí, không yêu cầu credential, và đủ dùng với phần lớn các loại NAT
như:

- full-cone NAT
- restricted-cone NAT
- port-restricted-cone NAT

miễn là _ít nhất một phía_ không nằm sau trường hợp khó nhất:

## Khi chỉ STUN là chưa đủ: symmetric NAT

Symmetric NAT cấp một public port _khác nhau_ cho mỗi destination khác nhau
mà client kết nối tới.

STUN vẫn có thể trả về chính xác:

> "Đây là địa chỉ mà STUN server nhìn thấy."

Nhưng địa chỉ đó chỉ hợp lệ đối với traffic gửi tới _chính STUN server đó_,
chứ không nhất thiết hợp lệ khi kết nối tới peer khác.

Lý do là NAT có thể cấp một port public hoàn toàn khác khi client giao tiếp
với một destination mới.

Ví dụ:

```text
Client → STUN Server
private: 192.168.1.10:5000
public : 203.0.113.10:62001
```

Nhưng khi client kết nối tới peer:

```text
Client → Peer B
private: 192.168.1.10:5000
public : 203.0.113.10:63124
```

Public port đã thay đổi.

Vì vậy candidate mà STUN trả về:

```text
203.0.113.10:62001
```

không còn dùng được để peer B kết nối trực tiếp tới client.

Hai peer cùng nằm sau symmetric NAT
(hoặc một bên symmetric NAT kết hợp với phía còn lại có NAT/firewall hạn chế)
thường không thể thiết lập được đường truyền trực tiếp,
bất kể họ trao đổi bao nhiêu STUN candidate.

Đây chính xác là vấn đề mà TURN giải quyết.

Thay vì cố gắng kết nối peer-to-peer,
cả hai phía đều kết nối tới một TURN _relay server_.

Mỗi kết nối tới TURN vẫn chỉ là một outbound connection bình thường,
loại kết nối mà hầu hết NAT đều cho phép.

TURN server sau đó chuyển tiếp traffic giữa hai phía.

```text
Browser A
    │
    │ outbound connection
    ▼
TURN Relay Server
    │
    │ forwarded traffic
    ▼
Browser B
```

Điều này làm TURN tốn bandwidth trên server nằm trong đường truyền —
thứ mà WebRTC thông thường cố gắng tránh —
nhưng trong một số cấu hình NAT thì đây là cách duy nhất để kết nối thành công.

ICE luôn ưu tiên thử:

```text
host candidate
        ↓
server-reflexive candidate
        ↓
relay candidate
```

Tức là ICE sẽ thử host candidate và server-reflexive candidate trước.

Relay candidate từ TURN chỉ là phương án fallback cuối cùng,
không phải là thứ thay thế STUN.

## TURN credentials (Milestone 5)

TURN credential không bao giờ được lưu dưới dạng credential tĩnh ở frontend.

Browser sẽ lấy một bộ credential tạm thời, có thời hạn ngắn, do backend cấp
thông qua endpoint:

```text
GET /api/v1/rtc/ice-servers
```

Logic này được triển khai tại:

```text
rtc.application.TurnCredentialService
```

Nhờ vậy TURN shared secret thực sự sẽ không bao giờ được gửi xuống client.

Credential được tạo theo công thức:

```text
Base64(
    HMAC-SHA1(
        TURN_SECRET,
        "<expiryUnixTimestamp>:<userId>"
    )
)
```

Đây là cơ chế xác thực REST API tiêu chuẩn của Coturn khi sử dụng:

```text
use-auth-secret
```

Username thường có dạng:

```text
<expiryUnixTimestamp>:<userId>
```

Ví dụ:

```text
1786543200:550e8400-e29b-41d4-a716-446655440000
```

Backend sử dụng `TURN_SECRET` để tạo HMAC.

Coturn cũng giữ một bản sao của cùng `TURN_SECRET` và tự tính lại HMAC
để kiểm tra credential.

Do đó:

```text
Spring Boot
    │
    │ TURN_SECRET
    │
    ├──── tạo temporary credential
    │
    ▼
Browser
```

và:

```text
Coturn
   │
   │ cùng TURN_SECRET
   │
   └──── tự verify credential
```

Backend và Coturn không cần dùng chung database chứa TURN user hoặc credential.

Chỉ cần hai service giữ cùng một shared secret.

Nếu:

```text
TURN_HOST
```

hoặc:

```text
TURN_SECRET
```

chưa được cấu hình, endpoint:

```text
GET /api/v1/rtc/ice-servers
```

sẽ không trả lỗi.

Thay vào đó, nó chỉ trả cấu hình STUN.

Ví dụ khi TURN chưa được cấu hình:

```json
{
  "iceServers": [
    {
      "urls": ["stun:stun.l.google.com:19302"]
    }
  ]
}
```

Khi TURN đã được cấu hình:

```json
{
  "iceServers": [
    {
      "urls": ["stun:stun.l.google.com:19302"]
    },
    {
      "urls": ["turn:turn.example.com:3478"],
      "username": "1786543200:user-id",
      "credential": "temporary-hmac-credential"
    }
  ]
}
```

Xem [turn-setup.md](./turn-setup.md) để biết hướng dẫn cài đặt và cấu hình Coturn.
