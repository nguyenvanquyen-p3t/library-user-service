# Thiết kế API quản lý phiên và introspection

Nguồn bàn giao: 2026-10-22; biên tập ngày 2026-10-24, không đối chiếu lại Word/database. **Chỉ migration và docs, chưa triển khai API hoặc cấu hình JWT.** Chủ dự án đã yêu cầu 3 API quản lý phiên và introspection RFC 7662 cho Gateway. Tổng thiết kế: 7 bảng, 20 API. Web và Android tiếp tục nhận/gửi refresh token qua JSON; không chuyển sang cookie.

## Những điểm đã chốt

- Thêm UserSessions, liên kết RefreshTokens.SessionId. Không có bảng Devices riêng: thông tin thiết bị là metadata của phiên, không phải bằng chứng danh tính.
- Gateway gọi introspection bằng **Service JWT**, khác với access token người dùng được kiểm tra.
- Access token người dùng ký RS256; thiết kế mở rộng thêm `sid` chỉ phiên, `sub` chỉ user. Rotation giữ cùng sid.
- Migration V6 chỉ chạy khi **RefreshTokens hoàn toàn rỗng**, kể cả không còn bản ghi token hết hạn/đã thu hồi. Không tự xóa, thu hồi hoặc gán phiên giả cho token cũ.
- API quản lý role vẫn ngoài phạm vi. Không thêm Redis hoặc JWT blacklist.

Hành vi được ghi là **Đã xác nhận** tuân theo sổ quyết định; response, messageCode, giới hạn phân trang và chính sách lỗi ghi **Đề xuất** còn thuộc [Q09](../open-decisions.md#q09), trừ định dạng RFC được nêu riêng ở introspection. Ví dụ không tự chốt required/null hoặc validation; chưa có hành vi đang chạy.

Chính sách [token](../confirmed-decisions.md#user-tokens) và [thời hạn session](../confirmed-decisions.md#session-lifetime) được duy trì tại sổ quyết định. Session hết hạn thì access token bị introspection từ chối và refresh thất bại dù token còn hạn riêng; refresh không gia hạn session. LastSeenAt chỉ phục vụ hiển thị hoạt động gần nhất. Chi tiết Service JWT còn [Q11a](../open-decisions.md#q11a).

<a id="session-list"></a>

## Danh sách phiên của chính mình

`GET /api/v1/users/me/sessions?page=0&size=20`

Không có request body. Dùng `Authorization: Bearer <access-token>`. Lấy user và phiên hiện tại từ JWT đã được xác minh, không nhận userId từ query/header tùy ý. UC10 chỉ liệt kê phiên còn hiệu lực. Phân trang/sắp xếp đề xuất: page >= 0, size 1–100, CreatedAt giảm dần rồi Id.

**Đề xuất — response HTTP 200**, thời điểm minh họa với session có thời hạn 30 ngày:

```json
{
  "success": true,
  "messageCode": "SESSION_LIST_SUCCESS",
  "message": "Lấy danh sách phiên thành công",
  "data": {
    "items": [
      {
        "id": "2e8b95c4-1326-49d2-86f3-9a409eb70067",
        "deviceName": "Chrome trên Windows",
        "clientType": "WEB",
        "createdAt": "2026-10-19T02:00:00Z",
        "lastSeenAt": "2026-10-19T03:00:00Z",
        "expiresAt": "2026-11-18T02:00:00Z",
        "isCurrent": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1
  }
}
```

`isCurrent` do server so sánh với sid đã xác minh. Metadata thiết bị có thể null. Không trả token/hash hoặc UserAgent thô. LastSeenAt cần chính sách cập nhật giới hạn tần suất ở bước code; migration chỉ cung cấp cột.

<a id="session-revoke"></a>

## Đăng xuất một phiên khác (UC11)

`DELETE /api/v1/users/me/sessions/{sessionId}`

Không có request body. Người gọi phải có phiên hiện tại hợp lệ và chỉ được thu hồi phiên của chính mình. UC11 chỉ cho chọn phiên khác: UI ẩn hoặc vô hiệu hóa thao tác thu hồi tại phiên isCurrent; server vẫn từ chối sessionId trùng sid đã xác minh, không thay đổi phiên/token. Đăng xuất phiên hiện tại dùng UC04. Đây là yêu cầu đã chốt, thay đề xuất cũ cho chọn phiên hiện tại. Cập nhật RevokedAt/RevocationReason của phiên và thu hồi refresh token của phiên trong cùng transaction. Response bên dưới là **đề xuất**.

```json
{
  "success": true,
  "messageCode": "SESSION_REVOKE_SUCCESS",
  "message": "Đã đăng xuất phiên",
  "data": null
}
```

Đề xuất HTTP 404 cho phiên không tồn tại hoặc thuộc user khác. Thu hồi lại phiên của mình đã bị thu hồi trả thành công nếu phiên gọi vẫn hợp lệ. Phiên gọi được giữ nguyên. HTTP/messageCode khi cố chọn phiên hiện tại còn thuộc Q09; đề xuất HTTP 400, không coi là quyết định cuối cùng.

<a id="session-revoke-others"></a>

## Đăng xuất các phiên khác

`POST /api/v1/users/me/sessions/revoke-others`

Không có request body; không nhận currentSessionId từ client. Giữ phiên có sid lấy từ token đã xác minh, thu hồi các phiên còn hiệu lực khác và refresh token liên quan trong một transaction. Response bên dưới là **đề xuất**.

```json
{
  "success": true,
  "messageCode": "OTHER_SESSIONS_REVOKED",
  "message": "Đã đăng xuất các phiên khác",
  "data": { "revokedCount": 2 }
}
```

Đề xuất đếm số phiên còn hiệu lực vừa thu hồi; gọi lại có thể trả 0. Thao tác này không cấm đăng nhập mới sau transaction. Các writer phải tuần tự hóa với thu hồi theo [yêu cầu nhất quán](../requirements.md#tính-nhất-quán-khi-có-request-đồng-thời); chưa chọn thứ tự khóa hoặc isolation cụ thể.

## Lỗi API người dùng

Đề xuất: 400 tham số sai, 401 JWT/phiên không hợp lệ, 404 không tìm thấy phiên của mình, 503 khi không kiểm tra được trạng thái do hạ tầng lỗi. Đề xuất dùng envelope nguồn, còn Q09; ví dụ HTTP 401:

```json
{
  "success": false,
  "messageCode": "SESSION_INACTIVE",
  "message": "Phiên không còn hiệu lực. Vui lòng đăng nhập lại",
  "data": null
}
```

Client xóa token và yêu cầu đăng nhập lại khi phiên bị thu hồi; không refresh vô hạn. Lỗi 503 không có nghĩa token chắc chắn bị thu hồi.

<a id="introspect"></a>

## Introspection Endpoint RFC 7662

`POST /api/v1/internal/oauth2/introspect`

Gateway gửi Service JWT trong Authorization và access token người dùng trong form body. Hai token có mục đích khác nhau:

```http
POST /api/v1/internal/oauth2/introspect
Authorization: Bearer <gateway-service-jwt>
Content-Type: application/x-www-form-urlencoded
Accept: application/json

token=<user-access-token>&token_type_hint=access_token
```

Request là form URL encoded, **không phải JSON**. Response JSON không bọc envelope success/messageCode/message/data. Theo [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662.html), `active` là boolean bắt buộc; token không còn hiệu lực trả HTTP 200 với `active: false`. Gateway cần được xác thực/cấp quyền độc lập, kết nối bảo vệ bằng TLS.

Ví dụ token hợp lệ (giá trị thời gian/issuer/audience chỉ minh họa):

```json
{
  "active": true,
  "sub": "3",
  "token_type": "Bearer",
  "iss": "https://identity.example",
  "aud": ["library-api"],
  "exp": 1792403000,
  "iat": 1792399400,
  "sid": "2e8b95c4-1326-49d2-86f3-9a409eb70067",
  "roles": ["READER"]
}
```

`sid` và `roles` là extension của dự án. Điều kiện active: JWT đúng chữ ký/issuer/audience/thời hạn, user ACTIVE, phiên thuộc đúng user, chưa hết hạn và chưa bị thu hồi. Nguồn roles cần được thống nhất trong hợp đồng phân quyền; không mặc định coi Service JWT là token của một user ADMIN.

Token sai/hết hạn, phiên bị thu hồi/hết hạn, user BANNED hoặc token không được phép tiết lộ cho Gateway:

```json
{ "active": false }
```

Thiếu token hoặc form không hợp lệ: HTTP 400, đề xuất `{"error":"invalid_request"}`. Gateway Service JWT sai/hết hạn/không đủ quyền: HTTP 401 theo hợp đồng introspection; không trả `active: false` để che lỗi danh tính Gateway. Lỗi database/hạ tầng: đề xuất 503, không coi đó là token hợp lệ hoặc token đã thu hồi.

`token_type_hint` chỉ là gợi ý, không phải căn cứ tin cậy xác định loại token. Đề xuất Gateway chỉ được introspect user access token, không được tiết lộ refresh token; hint không được biến refresh token thành access token. Không nhận sid do Gateway tự gửi thay cho việc xác minh token.

## Danh tính Gateway và vận hành

**Đã chốt Service JWT**, còn **PENDING DECISION** về bên phát token, thuật toán, issuer, audience dành riêng cho introspection, subject của Gateway, TTL, quyền gọi và cách phân phối/rotation khóa. Không tự chọn HTTP Basic, API Key hoặc dùng Private Key người dùng cho Gateway.

Đề xuất tách cấu hình xác minh Service JWT khỏi cấu hình user access token và từ chối dùng token này ở sai mục đích. Theo [Q04 đã chốt](../confirmed-decisions.md#user-tokens), Gateway không cache active response ở giai đoạn đầu; không chuyển request được bảo vệ khi introspection lỗi. Các route công khai, refresh UC03 và logout UC04 cần chính sách riêng; không bắt buộc access token còn hạn cho refresh hoặc logout bằng refresh token theo quy tắc đã chốt.

Gateway cần đối chiếu audience với route đích, chặn đường client đi vòng vào backend và loại header danh tính không đáng tin. Service nghiệp vụ vẫn kiểm tra quyền trên dữ liệu. Không cam kết hủy request đã vượt qua kiểm tra trước khi transaction thu hồi commit.

## Ảnh hưởng lên 16 API gốc

Các thay đổi đã được đưa vào từng operation của [16 API hiện hành](current-contract.md): tạo phiên khi login/Google, rotation cùng sid, logout thu hồi phiên, thu hồi mọi phiên khi đổi/reset/thiết lập mật khẩu hoặc khóa tài khoản. Local register không tạo phiên; mở khóa không phục hồi phiên/token. HTTP/schema chưa chốt vẫn được ghi tại operation và Q09, không tự thêm trường JSON vì bổ sung sid trong JWT.

## Lưu giữ refresh token đã chốt

Áp dụng [retention đã chốt](../confirmed-decisions.md#refresh-retention): giữ toàn bộ lịch sử token của phiên đến khi phiên kết thúc cộng 7 ngày; không xóa riêng token cũ khi phiên còn hiệu lực. Công thức, lịch chạy và ranh giới dọn được duy trì tại sổ quyết định, chưa có tác vụ thực thi.

Sau khi dọn, UC03 từ chối token không tìm thấy; UC04 trả kết quả chung, không khẳng định vừa thu hồi được phiên server. Retention không gia hạn token/session hoặc quyết định việc xóa UserSessions; phần xóa phiên còn [Q15](../open-decisions.md#q15).

## Migration và kế hoạch kiểm thử

V6 kiểm tra RefreshTokens rỗng trước khi thay đổi schema, thêm UserSessions và SessionId NOT NULL, FK ghép bảo đảm user/phiên, cùng trigger không cho chuyển token sang phiên khác. Theo bàn giao 2026-10-22, V6 chưa áp dụng vào database dự án; đợt biên tập này không kiểm tra hoặc thay đổi database. Xem [mô hình dữ liệu](../data-model.md).

**Kế hoạch kiểm thử migration khi được yêu cầu**, trên PostgreSQL riêng: chạy V1–V6 trên database mới; từ chối V6 khi V1–V5 có token; giữ nguyên dữ liệu khi thất bại; kiểm tra FK khác user/khác phiên, SessionId bắt buộc/bất biến, thời gian và xóa token thay thế.

Khi được yêu cầu code API: kiểm thử JWT/Service JWT độc lập, ownership, từ chối thu hồi phiên hiện tại ở UC11, giữ phiên hiện tại ở UC12, thu hồi lặp lại, refresh cạnh tranh revoke, token cũ sau mở khóa, Gateway timeout và chặn đường đi vòng. Chưa có test API trong repo; không dùng các mục này làm tuyên bố đã pass API.
