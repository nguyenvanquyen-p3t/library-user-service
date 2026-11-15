# Thiết kế API quản lý phiên và introspection

Nguồn bàn giao: 2026-10-22; biên tập ngày 2026-10-24, cập nhật hợp đồng ba API phiên và introspection theo xác nhận ngày 2026-10-26; không đối chiếu lại Word/database. **Ba endpoint quản lý phiên UC10–UC12 đã triển khai ngày 2026-11-14; introspection đã triển khai ngày 2026-11-15; nền tảng user JWT/login UC02 đã có từ ngày 2026-11-10. [OpenAPI/Swagger UI](openapi.md) có từ ngày 2026-11-01.** Chủ dự án đã yêu cầu 3 API quản lý phiên và introspection RFC 7662 cho Gateway. Tổng thiết kế: 7 bảng, 20 API. Web và Android tiếp tục nhận/gửi refresh token qua JSON; không chuyển sang cookie.

## Những điểm đã chốt

**Implementation ngày 2026-11-10:** [login UC02/nền tảng user JWT](../user-security.md) đã tạo session và refresh token đúng schema hiện có, kiểm tra user/phiên/role cho Bearer. Tại mốc UC02 bốn endpoint trong tài liệu này chưa triển khai; ngày 2026-11-11 đã thêm rotation UC03 và thu hồi phiên bằng logout UC04; ngày 2026-11-14 đã thêm [quản lý phiên UC10–UC12](../user-sessions.md); ngày 2026-11-15 đã thêm introspection để Gateway gọi, Gateway chưa triển khai.

- Thêm UserSessions, liên kết RefreshTokens.SessionId. Không có bảng Devices riêng: thông tin thiết bị là metadata của phiên, không phải bằng chứng danh tính.
- Theo [quyết định ngày 2026-11-10](../confirmed-decisions.md#deferred-service-auth), giai đoạn chức năng mặc định tin tưởng Gateway, chưa yêu cầu Service JWT hoặc kiểm tra quyền service. Sau khi hoàn tất tất cả chức năng mới bổ sung **Service JWT**, tách khỏi access token người dùng được kiểm tra.
- Access token người dùng ký RS256; thiết kế mở rộng thêm `sid` chỉ phiên, `sub` chỉ user. Rotation giữ cùng sid.
- Ngày 2026-10-31 chốt [user access token Q14](../confirmed-decisions.md#access-token-key-pair): một cặp khóa cố định, iss urn:library:user-service dùng chung, aud ["library-api"], kid user-access-v1, cấp/phân phối public key qua file/cấu hình và thay khóa có kiểm soát. Private key ký/public key xác minh chữ ký, không giải mã JWT hoặc tự sinh khóa mỗi lần cấp token/khởi động lại.
- Migration V6 chỉ chạy khi **RefreshTokens hoàn toàn rỗng**, kể cả không còn bản ghi token hết hạn/đã thu hồi. Không tự xóa, thu hồi hoặc gán phiên giả cho token cũ.
- API quản lý role vẫn ngoài phạm vi. Không thêm Redis hoặc JWT blacklist.

Hành vi được ghi là **Đã xác nhận** tuân theo sổ quyết định. [Schema JSON](json-schema.md) và [HTTP/lỗi](http-errors.md) áp dụng kiểu ID/thời gian, status và các mã đã nêu; ba API phiên dùng envelope chung, introspection có định dạng riêng. Chủ dự án chốt ngày 2026-10-26 [ba API phiên](../confirmed-decisions.md#session-response-contract) và [introspection](../confirmed-decisions.md#introspection-contract). User access token Q14 chốt ngày 2026-10-31; cấu hình Service JWT còn Q03/Q11a, các tình huống/mã/chi tiết ngoài danh mục vẫn thuộc [Q09](../open-decisions.md#q09). Ba API phiên và introspection đã triển khai; Service JWT/Gateway còn ở bước sau.

Chính sách [token](../confirmed-decisions.md#user-tokens) và [thời hạn session](../confirmed-decisions.md#session-lifetime) được duy trì tại sổ quyết định. Session hết hạn thì access token bị introspection từ chối và refresh thất bại dù token còn hạn riêng; refresh không gia hạn session. LastSeenAt chỉ phục vụ hiển thị hoạt động gần nhất. Chi tiết Service JWT còn [Q11a](../open-decisions.md#q11a).

<a id="session-list"></a>

## Danh sách phiên của chính mình

**IMPLEMENTED ngày 2026-11-14:** [UC10](../user-sessions.md) dùng REPEATABLE_READ/readOnly, kiểm tra caller, đếm và lấy trang trong cùng snapshot/now. Giữ LastSeenAt và metadata đã lưu.

`GET /api/v1/users/me/sessions?page=0&size=20`

Không có request body. Dùng `Authorization: Bearer <access-token>`. Lấy user và phiên hiện tại từ JWT đã được xác minh, không nhận userId từ query/header tùy ý. UC10 chỉ liệt kê phiên của chính mình chưa thu hồi và chưa hết hạn, gồm phiên hiện tại khi phiên đó nằm trong trang kết quả.

**Đã xác nhận ngày 2026-10-26 — phân trang và sắp xếp:**

| Query | Kiểu | Mặc định | Giới hạn |
| --- | --- | --- | --- |
| `page` | integer, int32 | 0 | Từ 0 |
| `size` | integer, int32 | 20 | 1–100 |

Sắp xếp cố định `createdAt` giảm dần, rồi `id` tăng dần nếu cùng thời điểm. Sai kiểu/ngoài giới hạn trả HTTP 400 theo validation đã chốt. Trang vượt số lượng kết quả vẫn HTTP 200 với items rỗng, giữ page/size được yêu cầu; không tự đưa phiên hiện tại lên đầu. Danh sách có thể thay đổi giữa các lần tải do login/logout.

**Schema SessionItem đã xác nhận:** mọi trường dưới đây bắt buộc xuất hiện; chỉ hai trường metadata nhận null.

| Trường | Kiểu | Nhận null | Ý nghĩa |
| --- | --- | --- | --- |
| `id` | string UUID | Không | ID phiên |
| `deviceName` | string, tối đa 150 ký tự | Có | Tên thiết bị để hiển thị |
| `clientType` | string, WEB hoặc ANDROID | Có | Loại client |
| `createdAt` | string date-time UTC | Không | Thời điểm tạo phiên |
| `lastSeenAt` | string date-time UTC | Không | Hoạt động gần nhất đã ghi nhận |
| `expiresAt` | string date-time UTC | Không | Hạn tuyệt đối của phiên |
| `isCurrent` | boolean | Không | Trùng sid của phiên gọi |

**Schema SessionPage của data:** luôn có đủ bốn trường, không null.

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `items` | array SessionItem | Có thể rỗng; không trả null |
| `page` | integer, int32, từ 0 | Trang được yêu cầu, đánh số từ 0 |
| `size` | integer, int32, 1–100 | Kích thước trang được yêu cầu |
| `totalElements` | integer, int64, từ 0 | Tổng số phiên phù hợp trên toàn danh sách, không phải số phần tử trang |

**Đã xác nhận HTTP 200, schema và messageCode SESSION_LIST_SUCCESS; message minh họa**, thời điểm minh họa với session có thời hạn 30 ngày:

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

`isCurrent` do server so sánh với sid đã xác minh; phiên hiện tại có thể nằm ở trang khác. Khi thiếu metadata trả deviceName/clientType null; client có thể hiển thị “Thiết bị không xác định”. Không trả token/hash hoặc UserAgent thô. lastSeenAt không có nghĩa thiết bị đang online, không quyết định hết hạn phiên; ngày 2026-11-14 đã chốt giữ giá trị đã lưu trong UC10–UC12; chính sách cập nhật chung vẫn mở.

Ví dụ data khi yêu cầu trang vượt số lượng kết quả (HTTP 200, vẫn giữ tổng số phiên phù hợp):

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "totalElements": 1
}
```

<a id="session-revoke"></a>

## Đăng xuất một phiên khác (UC11)

**IMPLEMENTED ngày 2026-11-14:** khóa user/kiểm tra lại caller, phiên đích còn hiệu lực ghi SESSION_REVOKED và thu hồi token còn hiệu lực cùng transaction. Phiên đích đã kết thúc không sửa phiên/token. [Chi tiết](../user-sessions.md).

`DELETE /api/v1/users/me/sessions/{sessionId}`

Không có request body. Người gọi phải có phiên hiện tại hợp lệ và chỉ được thu hồi phiên của chính mình. UC11 chỉ cho chọn phiên khác: UI ẩn hoặc vô hiệu hóa thao tác thu hồi tại phiên isCurrent; server vẫn từ chối sessionId trùng sid đã xác minh, không thay đổi phiên/token. Đăng xuất phiên hiện tại dùng UC04. Đây là yêu cầu đã chốt, thay đề xuất cũ cho chọn phiên hiện tại. Cập nhật RevokedAt/RevocationReason của phiên còn hiệu lực và thu hồi refresh token liên quan trong cùng transaction. **Đã xác nhận ngày 2026-10-26:** thành công HTTP 200, messageCode SESSION_REVOKE_SUCCESS, data luôn null; message minh họa.

```json
{
  "success": true,
  "messageCode": "SESSION_REVOKE_SUCCESS",
  "message": "Đã xử lý yêu cầu đăng xuất phiên",
  "data": null
}
```

Đã xác nhận: HTTP 404 `SESSION_NOT_FOUND` cho phiên không tồn tại hoặc thuộc user khác. Phiên đích của mình đã thu hồi hoặc đã hết hạn vẫn trả HTTP 200/kết quả chung nếu phiên gọi hợp lệ; không ghi đè thời điểm/lý do thu hồi cũ, không cập nhật lại phiên đã hết hạn. Quy tắc phiên hết hạn được chốt bổ sung ngày 2026-10-26 để xử lý danh sách vừa tải đã thay đổi. Phiên gọi được giữ nguyên. Chọn phiên hiện tại trả HTTP 400 `CURRENT_SESSION_REVOKE_NOT_ALLOWED` theo [bộ HTTP/lỗi](http-errors.md).

<a id="session-revoke-others"></a>

## Đăng xuất các phiên khác

**IMPLEMENTED ngày 2026-11-14:** khóa user/kiểm tra lại caller; thu hồi token còn hiệu lực thuộc các phiên khác còn hiệu lực trước khi ghi OTHER_SESSIONS_REVOKED lên phiên; giữ lịch sử đã kết thúc. [Chi tiết](../user-sessions.md).

`POST /api/v1/users/me/sessions/revoke-others`

Không có request body; không nhận currentSessionId từ client. Giữ phiên có sid lấy từ token đã xác minh, thu hồi các phiên còn hiệu lực khác và refresh token liên quan trong một transaction. **Đã xác nhận ngày 2026-10-26:** HTTP 200, messageCode OTHER_SESSIONS_REVOKED; data theo schema RevokeOthersResult chỉ gồm revokedCount bắt buộc, không null, integer/int64 từ 0 trở lên; message minh họa.

```json
{
  "success": true,
  "messageCode": "OTHER_SESSIONS_REVOKED",
  "message": "Đã xử lý yêu cầu đăng xuất các phiên khác",
  "data": { "revokedCount": 2 }
}
```

revokedCount chỉ đếm số phiên còn hiệu lực được chính thao tác này thu hồi thành công, không đếm phiên hiện tại, phiên đã thu hồi/hết hạn hoặc số refresh token. Không có phiên cần thu hồi thì HTTP 200 với revokedCount 0; gọi lại khi chưa có phiên mới cũng trả 0. Transaction thất bại phải trả lỗi, không trả thành công hoặc số đếm một phần. Ví dụ đang dùng A, còn B/C hợp lệ: lần đầu thu hồi B/C trả 2, gọi lại khi chưa có phiên mới trả 0.

Thao tác này không cấm đăng nhập mới sau transaction. Các writer phải tuần tự hóa với thu hồi theo [yêu cầu nhất quán](../requirements.md#tính-nhất-quán-khi-có-request-đồng-thời); UC11/UC12 đã dùng READ_COMMITTED, khóa user rồi lấy now/kiểm tra lại caller theo [quyết định ngày 2026-11-14](../confirmed-decisions.md#user-sessions-uc10-uc12).

## Lỗi API người dùng

Đã xác nhận theo [HTTP/lỗi](http-errors.md): 400 validation sai; 401 access token/phiên không hợp lệ (`ACCESS_TOKEN_INVALID`/`SESSION_INACTIVE`); 404 `SESSION_NOT_FOUND` ở UC11; 503 `SERVICE_UNAVAILABLE` khi hạ tầng tạm không khả dụng, 500 `INTERNAL_ERROR` khi lỗi ngoài dự kiến. Lỗi validation dùng data.errors, lỗi xác thực/nghiệp vụ không cần chỉ ra trường dùng data null. Ví dụ HTTP 401:

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

**IMPLEMENTED ngày 2026-11-15:** `internal.web.IntrospectionController` và `internal.service.IntrospectionService` đã triển khai. Tổng hiện tại **10 IMPLEMENTED/10 DESIGN_ONLY**. Chỉ endpoint User Service đã có; Gateway và xác thực Service JWT chưa triển khai. Các mốc triển khai trước ngày này trong tài liệu là lịch sử.

**Implementation và transaction đọc**

- Security dùng chain riêng chỉ cho đúng path introspection, không chạy filter user Bearer hoặc xác thực service. Authorization không được dùng thay token trong form, kể cả header sai/hết hạn. Path nội bộ khác tiếp tục được bảo vệ như trước.
- Controller `@Hidden` giữ YAML là nguồn OpenAPI. Dùng `FormHttpMessageConverter` với input stream body gốc; không dựng form từ servlet parameter map vì cách đó trộn query vào body. Query/cookie không thay thế token; trường lạ hoặc tham số lặp trong body bị từ chối. Form rỗng/sai encoding trả 400; JSON hoặc media type khác trả 415.
- Tái sử dụng `UserAccessTokenService.verify` và `UserSessionAuthenticator.authenticate`. JWT giữ chính sách `typ=at+jwt`, kid tin cậy, RS256, claim và dung sai 30 giây hiện có; JWT có khoảng trắng bao quanh không được tự trim để chấp nhận. Hạn phiên không có dung sai.
- Authenticator mở transaction readOnly hiện hữu, dùng READ_COMMITTED mặc định của PostgreSQL. Service điều phối không mở transaction bao ngoài; chỉ bắt ACCESS_TOKEN_INVALID/SESSION_INACTIVE sau khi lời gọi transactional kết thúc để trả inactive. Không khóa user, không ghi user/phiên/token/LastSeenAt và không cache kết quả. Không cam kết snapshot chung cho mọi truy vấn hoặc hủy request đã vượt kiểm tra trước khi thu hồi commit.
- Active response dùng claim từ JWT đã xác minh và roles hiện tại từ database, kể cả mảng rỗng. Lỗi mở/đọc transaction hoặc phụ thuộc tạm thời trả 503; lỗi ngoài dự kiến trả 500, không trở thành inactive.
- Mọi response ứng dụng dùng application/json và Cache-Control no-store. Method khác POST trả 405/Allow POST, gồm OPTIONS; HEAD giữ status/header và không có body theo ngữ nghĩa HTTP. Lỗi riêng dùng IntrospectionErrorFormatter, không qua envelope chung. Ba lỗi 401 Service JWT bên dưới chỉ thuộc hợp đồng đích cho bước bảo mật service sau.

**Kiểm thử implementation**

`IntrospectionServiceTests` kiểm tra mapping/nguồn roles, giữ token/hint và chỉ chuyển đúng hai mã xác thực thành inactive. `IntrospectionHttpTests` dùng HTTP/filter/controller/mapper thật với repository giả lập, không database/.env; kiểm tra form body độc lập query/cookie, schema/header, bỏ qua Authorization, method/media type và lỗi hạ tầng. `IntrospectionIntegrationTests` chỉ bật khi system property chọn rõ UserDB_test, dùng RSA tạm và fixture theo ID; kiểm tra transaction đọc thật, JWT/phiên tại biên, roles hiện tại, không thay đổi dữ liệu và introspection sau rotation/logout/đổi mật khẩu/UC11/UC12 commit. Kết quả thực tế tại [development](../development.md#kiểm-thử-hiện-có).

**Giai đoạn chức năng theo quyết định ngày 2026-11-10:** Gateway không cần gửi Service JWT; User Service mặc định tin tưởng service gọi, bỏ qua bước danh tính/quyền Gateway và xử lý user token trong form. User token vẫn được xác minh đầy đủ theo điều kiện active bên dưới. Q11a chưa chốt không chặn triển khai introspection.

```http
POST /api/v1/internal/oauth2/introspect
Content-Type: application/x-www-form-urlencoded
Accept: application/json

token=<user-access-token>&token_type_hint=access_token
```

**Thiết kế đích sau khi hoàn tất chức năng:** bổ sung Service JWT trong Authorization, giữ access token người dùng trong form body. Hai token có mục đích khác nhau:

```http
POST /api/v1/internal/oauth2/introspect
Authorization: Bearer <gateway-service-jwt>
Content-Type: application/x-www-form-urlencoded
Accept: application/json

token=<user-access-token>&token_type_hint=access_token
```

Request là form URL encoded, **không phải JSON**. Response JSON không bọc envelope success/messageCode/message/data. Theo [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662.html), `active` là boolean bắt buộc; token không còn hiệu lực trả HTTP 200 với `active: false`. Yêu cầu xác thực/cấp quyền Gateway độc lập thuộc hợp đồng đích sau khi hoàn tất chức năng; kết nối được thiết kế bảo vệ bằng TLS.

**Đã xác nhận ngày 2026-10-26 — schema IntrospectionRequest:**

| Trường form | Bắt buộc | Kiểu và xử lý |
| --- | --- | --- |
| `token` | Có | string không rỗng, giữ nguyên giá trị, không trim |
| `token_type_hint` | Không | string, chỉ là gợi ý, không giới hạn bằng enum access_token |

Chỉ nhận hai trường trên. Thiếu/rỗng token, lặp tham số hoặc có trường ngoài danh sách trả HTTP 400 với body lỗi riêng. Sau khi bổ sung Service JWT mới kiểm tra danh tính/quyền Gateway trước khi xử lý token người dùng; giai đoạn chức năng bỏ qua bước service này. Không nhận userId/sid/roles từ form thay cho việc xác minh token.

Server bỏ qua hint khi cần xác định token; hint không thay đổi loại token thực tế. Hint refresh_token với user access token hợp lệ vẫn kiểm tra access token. Gateway chỉ được kiểm tra user access token; refresh token, registrationToken hoặc Service JWT bị gửi vào trường token trả HTTP 200/active false. Không từ chối user access token hợp lệ chỉ vì hint khác access_token.

<a id="introspection-active"></a>

**Schema IntrospectionActiveResponse đã xác nhận:** mọi trường sau bắt buộc xuất hiện và không null. RFC chỉ bắt buộc active; các trường bổ sung bắt buộc bên dưới là lựa chọn hợp đồng của dự án.

| Trường | Kiểu và ý nghĩa |
| --- | --- |
| `active` | boolean, true trong biến thể này |
| `sub` | string biểu diễn User ID, ví dụ "3" |
| `sid` | string UUID của phiên |
| `token_type` | string, Bearer |
| `iss` | string issuer của user token đã xác minh, urn:library:user-service theo Q14 |
| `aud` | array string không rỗng; luôn trả mảng dù JWT chứa một audience |
| `exp` | integer/int64, Unix timestamp theo giây, lấy từ user JWT |
| `iat` | integer/int64, Unix timestamp theo giây, lấy từ user JWT |
| `roles` | array string lấy từ database, không có role thì [] |

Ví dụ HTTP 200, schema đã xác nhận; iss/aud theo Q14 chốt ngày 2026-10-31, giá trị user/session/thời gian minh họa. User JWT đã có cấu hình runtime; response introspection dưới đây là ví dụ theo implementation ngày 2026-11-15:

```json
{
  "active": true,
  "sub": "3",
  "token_type": "Bearer",
  "iss": "urn:library:user-service",
  "aud": ["library-api"],
  "exp": 1792403000,
  "iat": 1792399400,
  "sid": "2e8b95c4-1326-49d2-86f3-9a409eb70067",
  "roles": ["READER"]
}
```

`sid` và `roles` là extension của dự án. Điều kiện active giữ nguyên: JWT đúng chữ ký/issuer/audience/thời hạn, user ACTIVE, phiên thuộc đúng user, chưa hết hạn và chưa bị thu hồi. exp là hạn JWT, không cắt theo hạn phiên; phiên hết hạn thì active false dù JWT còn hạn. Gateway không cache active response theo quyết định đã chốt.

**Nguồn roles đã chốt trong Q14:** lấy role hiện tại qua UserRoles/Roles tại thời điểm introspection; không lấy role của Gateway và không chỉ dựa vào role trong JWT từ lúc đăng nhập. Quyết định này chỉ chọn nguồn dữ liệu cho introspection, không thêm API quản lý role hoặc chốt chính sách thay đổi role ngoài V1.

<a id="introspection-inactive"></a>

**Schema IntrospectionInactiveResponse:** token sai/hết hạn/sai mục đích, phiên bị thu hồi/hết hạn, user BANNED hoặc token Gateway không được phép biết thông tin trả HTTP 200 chỉ có active false. Không trả thêm user, role hoặc lý do inactive; không trả các trường còn lại dưới dạng null.

```json
{ "active": false }
```

**Lỗi/header đã xác nhận ngày 2026-10-26:** dùng [bảng lỗi introspection](http-errors.md#introspection-errors), không dùng envelope/messageCode chung. Thiếu/sai xác thực hoặc thiếu quyền Gateway trả 401 riêng; form sai trả 400, method sai 405, media type sai 415, phụ thuộc tạm không khả dụng 503, lỗi ngoài dự kiến 500. Không chuyển lỗi danh tính Gateway hoặc lỗi hạ tầng thành active false.

Mọi response introspection dùng Content-Type application/json và Cache-Control no-store. Response 401 có WWW-Authenticate Bearer tương ứng; response 405 có Allow POST. Chi tiết body và header từng lỗi duy trì tại tài liệu HTTP/lỗi. Quyền Gateway tokens:introspect đã chốt ngày 2026-10-31; thuật toán/danh tính, cách biểu diễn quyền và khóa của Service JWT vẫn chờ Q11a.

## Danh tính Gateway và vận hành

Các yêu cầu danh tính/quyền Gateway, lỗi 401 xác thực service và kiểm thử Service JWT dưới đây được áp dụng ở bước bổ sung xác thực service **sau khi hoàn tất tất cả chức năng**. Giai đoạn chức năng mặc định tin tưởng Gateway theo [quyết định ngày 2026-11-10](../confirmed-decisions.md#deferred-service-auth); kiểm tra user token/phiên, lỗi form/hạ tầng và Cache-Control no-store vẫn áp dụng. Nền tảng user security/login UC02 đã thay cấu hình runtime; endpoint introspection đã triển khai ngày 2026-11-15; xác thực service vẫn hoãn.

**Đã chốt Service JWT** và quyền Gateway tokens:introspect theo [Q03 ngày 2026-10-31](../confirmed-decisions.md#internal-service-auth). Vẫn **PENDING DECISION** về bên phát token, thuật toán, issuer, audience dành riêng cho introspection, subject của Gateway, TTL, tập claim/cách biểu diễn quyền và phân phối/rotation khóa. Chủ dự án giữ cấu hình Service JWT Q11a để nghiên cứu thêm; Q14 đã chốt riêng cho user token, không mặc định áp RS256/5 phút, issuer/audience/kid hoặc phương án public key/JWKS sang Service JWT. Không tự chọn HTTP Basic, API Key hoặc dùng Private Key người dùng cho Gateway.

Đề xuất tách cấu hình xác minh Service JWT khỏi cấu hình user access token và từ chối dùng token này ở sai mục đích. Theo [Q04 đã chốt](../confirmed-decisions.md#user-tokens), Gateway không cache active response ở giai đoạn đầu; không chuyển request được bảo vệ khi introspection lỗi. Các route công khai, refresh UC03 và logout UC04 cần chính sách riêng; không bắt buộc access token còn hạn cho refresh hoặc logout bằng refresh token theo quy tắc đã chốt.

Gateway cần đối chiếu audience với route đích, chặn đường client đi vòng vào backend và loại header danh tính không đáng tin. Service nghiệp vụ vẫn kiểm tra quyền trên dữ liệu. Không cam kết hủy request đã vượt qua kiểm tra trước khi transaction thu hồi commit.

## Ảnh hưởng lên 16 API gốc

Các thay đổi đã được đưa vào từng operation của [16 API hiện hành](current-contract.md): tạo phiên khi login/Google, rotation cùng sid, logout thu hồi phiên, thu hồi mọi phiên khi đổi/reset/thiết lập mật khẩu hoặc khóa tài khoản. Local register không tạo phiên; mở khóa không phục hồi phiên/token. HTTP/schema chưa chốt vẫn được ghi tại operation và Q09, không tự thêm trường JSON vì bổ sung sid trong JWT.

## Lưu giữ refresh token đã chốt

Áp dụng [retention đã chốt](../confirmed-decisions.md#refresh-retention): giữ toàn bộ lịch sử token của phiên đến khi phiên kết thúc cộng 7 ngày; không xóa riêng token cũ khi phiên còn hiệu lực. Công thức, lịch chạy và ranh giới dọn được duy trì tại sổ quyết định, chưa có tác vụ thực thi.

Sau khi dọn, UC03 từ chối token không tìm thấy; UC04 trả kết quả chung, không khẳng định vừa thu hồi được phiên server. Retention không gia hạn token/session hoặc quyết định việc xóa UserSessions; phần xóa phiên còn [Q15](../open-decisions.md#q15).

## Migration và kế hoạch kiểm thử

V6 kiểm tra RefreshTokens rỗng trước khi thay đổi schema, thêm UserSessions và SessionId NOT NULL, FK ghép bảo đảm user/phiên, cùng trigger không cho chuyển token sang phiên khác. V6 đã áp dụng vào database dự án theo xác nhận của chủ dự án ngày 2026-10-25; đợt cập nhật tài liệu này không kiểm tra hoặc thay đổi database. Xem [trạng thái migration và mô hình dữ liệu](../data-model.md#migration-v6-quản-lý-phiên).

**Kế hoạch kiểm thử migration khi được yêu cầu**, trên PostgreSQL riêng: chạy V1–V6 trên database mới; từ chối V6 khi V1–V5 có token; giữ nguyên dữ liệu khi thất bại; kiểm tra FK khác user/khác phiên, SessionId bắt buộc/bất biến, thời gian và xóa token thay thế.

Khi được yêu cầu code API: kiểm thử JWT/Service JWT độc lập, ownership, từ chối thu hồi phiên hiện tại ở UC11, giữ phiên hiện tại ở UC12, thu hồi lặp lại, refresh cạnh tranh revoke, token cũ sau mở khóa, Gateway timeout và chặn đường đi vòng. Đã có test endpoint phiên UC10–UC12; đã có test endpoint introspection ngày 2026-11-15; test login UC02 và nền tảng user security đã có. Kết quả thực tế UC10–UC12 tại [development](../development.md#kiểm-thử-hiện-có); các ca Gateway/Service JWT chưa triển khai vẫn là kế hoạch, không phải bằng chứng đã pass.
