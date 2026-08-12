# Signaling

WebSocket endpoint:

```text
ws://localhost:8080/ws/meetings/{meetingId}?token=<JWT>
```

được triển khai bởi `SignalingWebSocketHandler` / `WebSocketConfig`, thuộc package `signaling`.

## Xác thực khi kết nối

Browser không thể gắn `Authorization` header vào native WebSocket upgrade request, vì vậy JWT được truyền thông qua query parameter `?token=` thay thế.

Đây là ngoại lệ có chủ đích duy nhất so với quy tắc "JWT luôn được gửi qua header" ở các API còn lại.

`SignalingHandshakeInterceptor` chạy _trước khi_ WebSocket handshake hoàn tất:

1. Lấy `{meetingId}` từ path variable của request.

   `SimpleUrlHandlerMapping` của Spring sẽ đưa các path variable vào:

   ```text
   HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
   ```

   trên `HttpServletRequest` gốc đối với các WebSocket handler được đăng ký bằng URL pattern.

   Đây cũng chính là cơ chế tương tự `@PathVariable` trong MVC controller.

2. Parse và verify query parameter `token` bằng cùng `JwtService` đang được sử dụng cho REST authentication.

3. Tìm meeting tương ứng và từ chối kết nối với HTTP `404` nếu meeting không tồn tại hoặc meeting đã kết thúc.

4. Nếu xác thực thành công, `AuthenticatedUser` đã được resolve và `meetingId` sẽ được lưu vào attributes của WebSocket session để `SignalingWebSocketHandler` sử dụng sau đó.

Một handshake bị từ chối sẽ không bao giờ đi vào JWT filter chain của `SecurityConfig`.

Đường dẫn:

```text
/ws/**
```

được cấu hình:

```text
permitAll()
```

trong `SecurityConfig`, bởi vì `SignalingHandshakeInterceptor` chính là cơ chế authentication dành riêng cho WebSocket path này.

## Định tuyến envelope

Mọi `SignalingEnvelope` sau khi được parse, xem thêm:

[api/websocket-protocol.md](../api/websocket-protocol.md)

sẽ được xử lý dựa trên `type`.

### `PING`

Cập nhật heartbeat timestamp của sender và trả `PONG` trực tiếp về đúng connection đó.

Không broadcast `PONG` cho participant khác.

### `MEETING_JOIN` / `MEETING_LEAVE`

Được chấp nhận nhưng không thực hiện logic trực tiếp.

Presence của participant được quản lý dựa trên lifecycle của WebSocket connection:

```text
afterConnectionEstablished
afterConnectionClosed
```

thay vì dựa vào message do client gửi.

Lý do là nếu connection bị mất đột ngột thì participant vẫn phải được xem là đã rời phòng, ngay cả khi client không kịp gửi `MEETING_LEAVE`.

### Các message có xử lý riêng

Các loại message sau có custom handling:

```text
CHAT_MESSAGE
PARTICIPANT_APPROVED
PARTICIPANT_REJECTED
HOST_MUTE_PARTICIPANT
HOST_REMOVE_PARTICIPANT
```

Xem các section tương ứng để biết chi tiết.

### Các message còn lại

Các message khác như:

```text
WEBRTC_OFFER
WEBRTC_ANSWER
ICE_CANDIDATE

SCREEN_SHARE_STARTED
SCREEN_SHARE_STOPPED
```

được xử lý bởi generic relay.

Nếu envelope có:

```text
targetId
```

message chỉ được gửi tới WebSocket session của participant tương ứng.

Đây là kiểu truyền point-to-point:

```text
Participant A
      │
      │ targetId = B
      ▼
Java Signaling Server
      │
      ▼
Participant B
```

Nếu envelope không có `targetId`, message sẽ được broadcast tới tất cả WebSocket session khác trong cùng room.

Ví dụ:

```text
Participant A
      │
      ▼
Java Signaling Server
   ┌──┼──┐
   ▼  ▼  ▼
   B  C  D
```

Các field:

```text
senderId
meetingId
```

trên mọi envelope luôn được backend ghi lại từ authenticated WebSocket session.

Backend không bao giờ tin `senderId` hoặc `meetingId` do client tự gửi lên.

Điều này ngăn một connection giả mạo rằng nó đang gửi message dưới danh nghĩa user khác.

## Participant snapshot khi kết nối

Khi một participant mới kết nối vào room, backend gửi cho participant đó một `PARTICIPANT_JOINED` envelope cho từng participant đã có mặt trong room.

Các message này chỉ được gửi cho participant mới và đóng vai trò như một snapshot.

Ví dụ room hiện có:

```text
A
B
C
```

khi `D` kết nối:

```text
Server → D: PARTICIPANT_JOINED(A)
Server → D: PARTICIPANT_JOINED(B)
Server → D: PARTICIPANT_JOINED(C)
```

Sau đó backend broadcast participant mới tới những người còn lại:

```text
Server → A: PARTICIPANT_JOINED(D)
Server → B: PARTICIPANT_JOINED(D)
Server → C: PARTICIPANT_JOINED(D)
```

Nhờ vậy client có thể xây dựng participant roster ban đầu mà không cần thêm một message type riêng kiểu:

```text
ROOM_STATE
```

## Mô hình concurrency

```text
WebSocket connections
        │
        ▼
SignalingWebSocketHandler
        │
        │ parse envelope
        │ không trực tiếp thay đổi room state
        ▼
RoomCommandDispatcher
        │
    ┌───┼────┐
    ▼   ▼    ▼
 Room A Room B Room C
```

Các room khác nhau có thể được xử lý hoàn toàn concurrent.

Trong cùng một room, các command như:

```text
join
leave
PING
relay
```

phải chạy đúng theo thứ tự chúng được dispatch.

Nếu không đảm bảo ordering, hai participant thực hiện hành động cùng thời điểm có thể tạo ra trạng thái không nhất quán.

Ví dụ:

```text
Participant A đang join room
```

đúng lúc:

```text
Participant B đang leave
```

và B trước đó là participant cuối cùng khiến logic xóa `MeetingRuntime` được kích hoạt.

Nếu hai thao tác chạy không có ordering, hệ thống có thể:

```text
xóa MeetingRuntime
```

trong khi participant mới vừa được thêm vào, hoặc các thread khác nhau quan sát hai phiên bản room state không đồng nhất.

## Không dùng một thread riêng cho mỗi room

Hệ thống **không** triển khai theo mô hình:

```text
Room A → Thread A
Room B → Thread B
Room C → Thread C
...
```

Cách này không scale tốt vì số lượng thread sẽ tăng theo số meeting đồng thời, trong khi phần lớn các thread có thể đang idle.

Thay vào đó, `RoomCommandDispatcher` giữ một chuỗi:

```java
CompletableFuture<Void>
```

cho mỗi room:

```java
Map<UUID, CompletableFuture<Void>>
```

Mỗi entry đại diện cho `tail` của command chain của room đó.

Khi có command mới:

```java
tail = tail.thenRunAsync(command, executor);
```

Nhờ đó các command trong cùng room được thực thi tuần tự:

```text
Command 1
   │
   ▼
Command 2
   │
   ▼
Command 3
```

nhưng không cần giữ một thread cố định cho room khi room không có command nào đang chạy.

Ví dụ:

```text
Room A
JOIN A
  ↓
PING A
  ↓
RELAY OFFER
  ↓
LEAVE A
```

được đảm bảo chạy đúng thứ tự.

Trong khi đó:

```text
Room A command
Room B command
Room C command
```

vẫn có thể chạy song song.

## Virtual Threads

Tất cả room dùng chung một executor:

```java
Executors.newVirtualThreadPerTaskExecutor()
```

với Java 21.

Virtual thread có chi phí thấp hơn nhiều so với platform thread.

Vì vậy mô hình:

```text
một virtual thread cho mỗi command đang thực sự chạy
```

phù hợp hơn nhiều so với:

```text
một platform thread cố định cho mỗi meeting
```

Đối với quy mô thực tế mà project này hướng tới, số lượng virtual thread cho các command đang in-flight không phải là vấn đề đáng kể.

Nếu một command bị lỗi:

```text
Command 1
   │
   X exception
```

exception sẽ được log nhưng không làm hỏng command chain của room.

Command tiếp theo vẫn tiếp tục chạy:

```text
Command 1
   │
   X
   │
   ▼
Command 2
   │
   ▼
Command 3
```

## Runtime state

Hai thành phần chính được `RoomCommandDispatcher` bảo vệ là:

```text
MeetingRuntime
MeetingRuntimeRegistry
```

`MeetingRuntime` chứa trạng thái runtime của từng room, bao gồm participant map.

`MeetingRuntimeRegistry` chứa mapping:

```text
meetingId → MeetingRuntime
```

Đây là state:

- chỉ tồn tại trong memory
- không persist
- có tính realtime
- gắn với WebSocket session hiện tại

Xem thêm:

[database/schema.md](../database/schema.md#what-is-not-persisted)

để biết lý do những dữ liệu này không được lưu vào PostgreSQL.

## Heartbeat

Client gửi:

```text
PING
```

tới server mỗi:

```text
WS_HEARTBEAT_INTERVAL_SECONDS
```

giây.

Giá trị mặc định:

```text
10 giây
```

Logic phía frontend nằm trong:

```text
SignalingClient
```

Flow:

```text
Client
   │
   │ PING
   ▼
Server
   │
   │ PONG
   ▼
Client
```

Khi nhận `PING`, server:

1. cập nhật heartbeat timestamp của participant tương ứng trong `RuntimeParticipant`;
2. trả `PONG` về connection đó.

Ngoài ra hệ thống có một `HeartbeatReaper` chạy bằng:

```text
@Scheduled
```

Reaper kiểm tra các connection định kỳ.

Khoảng thời gian kiểm tra độc lập với heartbeat timeout và mặc định là:

```text
5 giây
```

Nếu thời điểm heartbeat cuối cùng của một session cũ hơn:

```text
WS_HEARTBEAT_TIMEOUT_SECONDS
```

thì session đó được xem là dead.

Giá trị timeout mặc định:

```text
30 giây
```

Tức tương đương khoảng ba lần `PING` liên tiếp bị miss:

```text
PING interval = 10s

miss 1
   ↓
10s

miss 2
   ↓
20s

miss 3
   ↓
30s

session considered dead
```

Khi timeout:

```text
HeartbeatReaper
      │
      ▼
close WebSocket session
      │
      ▼
afterConnectionClosed
```

Việc đóng session sẽ kích hoạt cleanup path bình thường của `SignalingWebSocketHandler`.

Do đó `HeartbeatReaper` không duplicate bất kỳ logic nào liên quan tới:

- remove participant khỏi room
- update room state
- broadcast participant leave
- cleanup session

Nó chỉ chịu trách nhiệm quyết định:

> Khi nào một WebSocket session được xem là đã chết?

Toàn bộ cleanup vẫn được xử lý bởi:

```text
afterConnectionClosed
```

## Reconnection

Cơ chế reconnection, trong đó client kết nối lại sau timeout và tiếp tục session trước đó thay vì được xem như một participant hoàn toàn mới, hiện tại **chưa nằm trong phạm vi triển khai của milestone này**.

Phần đó thuộc:

```text
Milestone 8
```
