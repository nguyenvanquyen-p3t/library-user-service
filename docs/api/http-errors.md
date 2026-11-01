# HTTP status và phản hồi lỗi đã xác nhận

Chủ dự án chốt HTTP/lỗi ngày 2026-10-25, bổ sung API phiên, mã phản hồi và introspection ngày 2026-10-26; chốt lỗi tích hợp, SMTP và trạng thái ngày 2026-10-31. **Thiết kế đã xác nhận, chưa triển khai API nghiệp vụ. Đã có [OpenAPI/Swagger UI](openapi.md) từ ngày 2026-11-01.** Quyết định duy trì tại [sổ quyết định](../confirmed-decisions.md#http-error-contract), [API phiên](../confirmed-decisions.md#session-response-contract), [danh mục mã phản hồi](../confirmed-decisions.md#response-code-contract), [introspection](../confirmed-decisions.md#introspection-contract) và [lỗi tích hợp bổ sung](../confirmed-decisions.md#integration-error-contract); schema chung tại [schema JSON](json-schema.md). Cấu hình JWT Q03/Q11a vẫn mở; Q06 chỉ còn lựa chọn/cấu hình môi trường SMTP.

<a id="status"></a>

## Quy tắc HTTP status

| Status | Áp dụng |
| --- | --- |
| 200 | Đọc/cập nhật/thao tác thành công; gồm các nhánh Google trước hoàn tất đăng ký |
| 201 | Đăng ký Local hoặc hoàn tất đăng ký Google thành công |
| 400 | Request sai/không đạt validation; OTP không hợp lệ |
| 401 | Thiếu/sai thông tin xác thực, token hoặc phiên không hợp lệ |
| 403 | Không đủ quyền; tài khoản bị khóa trong luồng đã xác minh danh tính |
| 404 | Không tìm thấy tài nguyên được phép truy cập |
| 409 | Trùng dữ liệu hoặc trạng thái tài khoản không phù hợp thao tác |
| 429 | Vượt giới hạn gửi OTP |
| 500 | Lỗi server ngoài dự kiến |
| 503 | Tạm thời không xử lý được vì dịch vụ phụ thuộc/hạ tầng không khả dụng |

V1 dùng 400 cho lỗi định dạng và validation, không dùng 422. Sai HTTP method dùng 405; sai media type dùng 415, không gộp vào validation. Admin/Borrow đã chọn Service JWT riêng theo Q03; bảng status không tự chốt cấu hình JWT.

<a id="error-body"></a>

## Envelope lỗi và lỗi theo trường

Giữ đủ `success`, `messageCode`, `message`, `data`. Response lỗi có `success: false`; lỗi nghiệp vụ/xác thực không cần chỉ ra trường dùng `data: null`.

Ví dụ HTTP 401:

```json
{
  "success": false,
  "messageCode": "SESSION_INACTIVE",
  "message": "Phiên không còn hiệu lực. Vui lòng đăng nhập lại",
  "data": null
}
```

Lỗi validation HTTP 400 dùng `messageCode: VALIDATION_ERROR`, `data.errors` là mảng không rỗng. Mỗi phần tử luôn có `field`, `code`, `message` kiểu string không null; `field: "$"` chỉ lỗi của toàn bộ body. Trả các lỗi validation có thể xác định trong cùng lần kiểm tra; không yêu cầu thực hiện kiểm tra nghiệp vụ/xác thực tiếp khi chưa đủ điều kiện.

```json
{
  "success": false,
  "messageCode": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "data": {
    "errors": [
      {
        "field": "confirmPassword",
        "code": "PASSWORD_MISMATCH",
        "message": "Mật khẩu xác nhận không khớp"
      }
    ]
  }
}
```

Không trả giá trị mật khẩu, OTP hoặc token đã nhập trong lỗi. Không trả SQL, stack trace, hash hoặc secret. [Bộ mã con validation](#validation-codes) đã chốt ngày 2026-10-26; không tự coi tên annotation/framework là mã công khai.

<a id="validation-codes"></a>

## Mã con validation đã chốt ngày 2026-10-26

HTTP 400, messageCode `VALIDATION_ERROR`; mã dưới đây nằm tại `data.errors[].code`, không thay messageCode cấp envelope. Áp dụng cho body/path/query khi có quy tắc validation tương ứng; `field` là tên trường, `$` chỉ lỗi toàn bộ body.

| code | Khi sử dụng |
| --- | --- |
| `REQUIRED` | Thiếu trường bắt buộc hoặc gửi null khi trường không cho phép |
| `TYPE_MISMATCH` | Sai kiểu dữ liệu |
| `NOT_BLANK` | Chuỗi rỗng hoặc toàn khoảng trắng khi quy tắc trường cấm |
| `INVALID_LENGTH` | Độ dài không đạt giới hạn |
| `INVALID_FORMAT` | Sai định dạng email, số điện thoại, OTP hoặc UUID |
| `INVALID_VALUE` | Giá trị không thuộc danh sách cho phép, ví dụ status khác ACTIVE/BANNED |
| `OUT_OF_RANGE` | Giá trị số ngoài giới hạn, ví dụ page âm hoặc size lớn hơn 100 |
| `UNKNOWN_FIELD` | Request có trường không được phép |
| `EMPTY_PATCH` | PATCH hồ sơ gửi object rỗng `{}` |
| `PASSWORD_MISMATCH` | Xác nhận mật khẩu không khớp; giữ mã đã chốt trước đó |

Chỉ áp dụng mã tương ứng với quy tắc của trường; không dùng NOT_BLANK để tự cấm khoảng trắng trong mật khẩu, không trim mật khẩu/token. JSON hỏng hoặc thiếu toàn bộ body bắt buộc vẫn dùng `INVALID_REQUEST`, không đổi thành lỗi thiếu trường. Không tự thêm thứ tự ưu tiên hoặc mã khác khi nhiều quy tắc cùng lỗi.

Ví dụ query `page=-1` của danh sách phiên (message minh họa):

```json
{
  "success": false,
  "messageCode": "VALIDATION_ERROR",
  "message": "Dữ liệu không hợp lệ",
  "data": {
    "errors": [
      {
        "field": "page",
        "code": "OUT_OF_RANGE",
        "message": "page phải lớn hơn hoặc bằng 0"
      }
    ]
  }
}
```

<a id="codes"></a>

## Danh mục messageCode đã chốt

| Tình huống | HTTP | messageCode |
| --- | --- | --- |
| JSON không đọc được hoặc thiếu body bắt buộc | 400 | `INVALID_REQUEST` |
| Thiếu trường, sai kiểu, trường lạ hoặc không đạt validation | 400 | `VALIDATION_ERROR` |
| Không cung cấp thông tin xác thực cho API được bảo vệ | 401 | `AUTHENTICATION_REQUIRED` |
| Service JWT Admin/Borrow đã cung cấp nhưng sai/hết hạn | 401 | `SERVICE_TOKEN_INVALID` |
| Token actor Admin đã cung cấp nhưng sai/hết hạn/phiên mất hiệu lực | 401 | `ADMIN_ACTOR_INVALID` |
| Không có tài khoản hoặc sai mật khẩu đăng nhập | 401 | `INVALID_CREDENTIALS` |
| Access token hết hạn/không hợp lệ | 401 | `ACCESS_TOKEN_INVALID` |
| Phiên hết hạn/đã thu hồi | 401 | `SESSION_INACTIVE` |
| Refresh token không hợp lệ, gồm token đã rotation | 401 | `REFRESH_TOKEN_INVALID` |
| Google ID Token không hợp lệ | 401 | `GOOGLE_TOKEN_INVALID` |
| registrationToken không hợp lệ/hết hạn | 401 | `REGISTRATION_TOKEN_INVALID` |
| Tài khoản bị khóa sau khi xác minh thông tin đăng nhập | 403 | `ACCOUNT_BANNED` |
| Người gọi/service không đủ quyền | 403 | `ACCESS_DENIED` |
| Username trùng | 409 | `USERNAME_ALREADY_EXISTS` |
| Email trùng | 409 | `EMAIL_ALREADY_EXISTS` |
| Chưa có mật khẩu nhưng gọi luồng yêu cầu đã có | 409 | `PASSWORD_NOT_CONFIGURED` |
| Đã có mật khẩu nhưng gọi thiết lập lần đầu | 409 | `PASSWORD_ALREADY_CONFIGURED` |
| Google identity đã được đăng ký khi hoàn tất đăng ký | 409 | `GOOGLE_IDENTITY_ALREADY_REGISTERED` |
| Mật khẩu hiện tại sai khi đổi mật khẩu | 400 | `CURRENT_PASSWORD_INCORRECT` |
| OTP sai/hết hạn/đã dùng/đã bị thay thế | 400 | `OTP_INVALID` |
| OTP đã đạt giới hạn nhập sai | 400 | `OTP_ATTEMPTS_EXCEEDED` |
| Vượt giới hạn gửi OTP | 429 | `OTP_RATE_LIMITED` |
| Không tìm thấy user qua API nội bộ | 404 | `USER_NOT_FOUND` |
| Phiên đích không tồn tại hoặc thuộc người khác | 404 | `SESSION_NOT_FOUND` |
| UC11 chọn phiên hiện tại | 400 | `CURRENT_SESSION_REVOKE_NOT_ALLOWED` |
| HTTP method không được hỗ trợ | 405 | `METHOD_NOT_ALLOWED` |
| Kiểu nội dung request không được hỗ trợ | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Phụ thuộc tạm thời không khả dụng | 503 | `SERVICE_UNAVAILABLE` |
| Lỗi ngoài dự kiến | 500 | `INTERNAL_ERROR` |

Mã dùng `UPPER_SNAKE_CASE`, không đổi ý nghĩa sau công bố. Client dựa HTTP status và messageCode, không so sánh nội dung message. Ba mã AUTHENTICATION_REQUIRED/METHOD_NOT_ALLOWED/UNSUPPORTED_MEDIA_TYPE chốt ngày 2026-10-26; SERVICE_TOKEN_INVALID/ADMIN_ACTOR_INVALID chốt ngày 2026-10-31. Các mã này dùng envelope lỗi với data null. AUTHENTICATION_REQUIRED áp dụng khi thiếu Service JWT hoặc thiếu token actor bắt buộc, không thay validation trường password thiếu trong JSON login. Actor đã xác minh nhưng không ACTIVE hoặc không có ADMIN, service thiếu quyền, hoặc yêu cầu khóa đối tượng ADMIN bị cấm dùng 403 ACCESS_DENIED. Introspection giữ body lỗi riêng, không dùng các mã envelope này. Các trường hợp ngoài danh mục vẫn thuộc Q09.

<a id="success-codes"></a>

## Mã thành công của 16 API gốc đã chốt ngày 2026-10-26

Dùng tên trong đề xuất đã được chủ dự án chốt; ba nhánh Google thuộc cùng một operation. Riêng thiết lập mật khẩu dùng PASSWORD_SET_SUCCESS theo đề xuất, thay SET_PASSWORD_SUCCESS trong ví dụ cũ; danh mục nguồn giữ tên lịch sử. Tất cả response thành công dùng success true, data theo schema đã chốt; nội dung message là minh họa.

| Operation | HTTP | messageCode |
| --- | --- | --- |
| `POST /api/v1/auth/register/request-code` | 200 | `REGISTER_CODE_SENT` |
| `POST /api/v1/auth/register` | 201 | `REGISTER_SUCCESS` |
| `POST /api/v1/auth/login` | 200 | `LOGIN_SUCCESS` |
| `POST /api/v1/auth/refresh` | 200 | `TOKEN_REFRESH_SUCCESS` |
| `POST /api/v1/auth/logout` | 200 | `LOGOUT_SUCCESS` |
| `POST /api/v1/auth/forgot-password/request-code` | 200 | `PASSWORD_RESET_CODE_SENT` |
| `POST /api/v1/auth/reset-password` | 200 | `PASSWORD_RESET_SUCCESS` |
| `GET /api/v1/users/me` | 200 | `PROFILE_GET_SUCCESS` |
| `PATCH /api/v1/users/me` | 200 | `PROFILE_UPDATE_SUCCESS` |
| `PUT /api/v1/users/me/password` | 200 | `PASSWORD_CHANGE_SUCCESS` |
| `POST /api/v1/auth/google`, identity đã có | 200 | `GOOGLE_LOGIN_SUCCESS` |
| `POST /api/v1/auth/google`, vừa tự liên kết Local | 200 | `GOOGLE_ACCOUNT_LINKED` |
| `POST /api/v1/auth/google`, cần hoàn tất đăng ký | 200 | `GOOGLE_REGISTRATION_REQUIRED` |
| `POST /api/v1/auth/google/register` | 201 | `GOOGLE_REGISTER_SUCCESS` |
| `POST /api/v1/users/me/password/request-code` | 200 | `SET_PASSWORD_CODE_SENT` |
| `POST /api/v1/users/me/password/set` | 200 | `PASSWORD_SET_SUCCESS` |
| `GET /api/v1/internal/users/{id}` | 200 | `USER_GET_SUCCESS` |
| `PATCH /api/v1/internal/users/{id}/status` | 200 | `USER_STATUS_UPDATE_SUCCESS` |

Giữ ngoại lệ nghiệp vụ: LOGOUT_SUCCESS là kết quả chung kể cả không tìm thấy token; PASSWORD_RESET_CODE_SENT giữ HTTP 200/thông báo chung khi email không tồn tại hoặc bước gửi SMTP lỗi/timeout theo [Q06 chốt ngày 2026-10-31](../confirmed-decisions.md#otp-email-delivery). Tên mã không bảo đảm thư đã vào Inbox. Gửi lại cùng trạng thái trả USER_STATUS_UPDATE_SUCCESS theo [Q10](../confirmed-decisions.md#admin-status-contract); khóa đối tượng ADMIN bị cấm.

<a id="session-success-codes"></a>

## Mã thành công API phiên đã chốt ngày 2026-10-26

| Operation | HTTP | messageCode | data |
| --- | --- | --- | --- |
| GET danh sách phiên UC10 | 200 | `SESSION_LIST_SUCCESS` | SessionPage |
| DELETE một phiên khác UC11 | 200 | `SESSION_REVOKE_SUCCESS` | null |
| POST thu hồi các phiên khác UC12 | 200 | `OTHER_SESSIONS_REVOKED` | RevokeOthersResult với revokedCount |

Áp dụng [schema và hành vi API phiên](sessions.md#session-list). Nội dung message là minh họa, client không so sánh chuỗi message. Cùng [mã thành công của 16 API gốc](#success-codes), đây là danh mục thành công đã chốt cho 19 API dùng envelope; introspection không dùng messageCode.

<a id="operations"></a>

## Ánh xạ vào 20 operation

Bảng liệt kê lỗi nghiệp vụ/token đặc thù đã ánh xạ; mã thành công tra hai bảng ở trên. Request JSON/path/query áp dụng 400 INVALID_REQUEST/VALIDATION_ERROR khi tương ứng. API dùng envelope được bảo vệ có 401 AUTHENTICATION_REQUIRED khi thiếu xác thực, ACCESS_TOKEN_INVALID/SESSION_INACTIVE khi đúng nguyên nhân; riêng API nội bộ dùng SERVICE_TOKEN_INVALID và ADMIN_ACTOR_INVALID theo bảng trên. User access token không thay Service JWT. 405/415 dùng METHOD_NOT_ALLOWED/UNSUPPORTED_MEDIA_TYPE. Tất cả operation có 500/503 khi đúng nhóm lỗi, ngoại trừ bước gửi SMTP của xin mã quên mật khẩu giữ 200; lỗi database/phụ thuộc ngoài SMTP vẫn áp dụng 500/503. Introspection giữ body riêng. Không coi mọi lỗi hạ tầng là 503 nếu nguyên nhân là lỗi server ngoài dự kiến.

| # | Operation | Thành công | Lỗi đặc thù đã chốt |
| --- | --- | --- | --- |
| 1 | `POST /api/v1/auth/register/request-code` | 200 | 409 `USERNAME_ALREADY_EXISTS`/`EMAIL_ALREADY_EXISTS`; 429 `OTP_RATE_LIMITED`; SMTP lỗi/timeout sau commit dùng 503 `SERVICE_UNAVAILABLE` |
| 2 | `POST /api/v1/auth/register` | 201 | 400 `OTP_INVALID`/`OTP_ATTEMPTS_EXCEEDED`; 409 `USERNAME_ALREADY_EXISTS`/`EMAIL_ALREADY_EXISTS` |
| 3 | `POST /api/v1/auth/login` | 200 | 401 `INVALID_CREDENTIALS`; 403 `ACCOUNT_BANNED`; 409 `PASSWORD_NOT_CONFIGURED` |
| 4 | `POST /api/v1/auth/refresh` | 200 | 401 `REFRESH_TOKEN_INVALID`/`SESSION_INACTIVE`; 403 `ACCOUNT_BANNED` khi đã xác định đúng tài khoản theo token |
| 5 | `POST /api/v1/auth/logout` | 200 | Không biến token không tìm thấy/phiên kết thúc thành 401; lỗi database/transaction vẫn trả lỗi |
| 6 | `POST /api/v1/auth/forgot-password/request-code` | 200 | 409 `PASSWORD_NOT_CONFIGURED` theo UC05; 429 `OTP_RATE_LIMITED`; email không tồn tại hoặc bước gửi SMTP lỗi/timeout vẫn 200/thông báo chung |
| 7 | `POST /api/v1/auth/reset-password` | 200 | 400 `OTP_INVALID`/`OTP_ATTEMPTS_EXCEEDED`; 409 `PASSWORD_NOT_CONFIGURED`; không mở khóa BANNED |
| 8 | `GET /api/v1/users/me` | 200 | Kiểm tra access token/phiên hợp lệ theo quy tắc chung |
| 9 | `PATCH /api/v1/users/me` | 200 | 400 `VALIDATION_ERROR`; gửi lại giá trị hiện có vẫn thành công |
| 10 | `PUT /api/v1/users/me/password` | 200 | 400 `CURRENT_PASSWORD_INCORRECT`; 409 `PASSWORD_NOT_CONFIGURED` |
| 11 | `POST /api/v1/auth/google` | 200 | 401 `GOOGLE_TOKEN_INVALID`; 403 `ACCOUNT_BANNED`; cần hoàn tất đăng ký là nhánh thành công |
| 12 | `POST /api/v1/auth/google/register` | 201 | 401 `REGISTRATION_TOKEN_INVALID`; 409 `USERNAME_ALREADY_EXISTS`/`GOOGLE_IDENTITY_ALREADY_REGISTERED`/`EMAIL_ALREADY_EXISTS` |
| 13 | `POST /api/v1/users/me/password/request-code` | 200 | 409 `PASSWORD_ALREADY_CONFIGURED`; 429 `OTP_RATE_LIMITED`; SMTP lỗi/timeout sau commit dùng 503 `SERVICE_UNAVAILABLE` |
| 14 | `POST /api/v1/users/me/password/set` | 200 | 400 `OTP_INVALID`/`OTP_ATTEMPTS_EXCEEDED`; 409 `PASSWORD_ALREADY_CONFIGURED` |
| 15 | `GET /api/v1/internal/users/{id}` | 200 | 401 `AUTHENTICATION_REQUIRED`/`SERVICE_TOKEN_INVALID`; 403 `ACCESS_DENIED`; 404 `USER_NOT_FOUND`; user BANNED vẫn được trả khi đủ quyền |
| 16 | `PATCH /api/v1/internal/users/{id}/status` | 200 | 401 `AUTHENTICATION_REQUIRED`/`SERVICE_TOKEN_INVALID`/`ADMIN_ACTOR_INVALID`; 403 `ACCESS_DENIED` gồm actor thiếu quyền hoặc khóa đối tượng ADMIN bị cấm; 404 `USER_NOT_FOUND`; cùng trạng thái vẫn 200 |
| 17 | `GET /api/v1/users/me/sessions` | 200 `SESSION_LIST_SUCCESS` | Query sai kiểu/giới hạn dùng 400; trang vượt kết quả vẫn 200/items rỗng |
| 18 | `DELETE /api/v1/users/me/sessions/{sessionId}` | 200 `SESSION_REVOKE_SUCCESS` | 400 `CURRENT_SESSION_REVOKE_NOT_ALLOWED`; 404 `SESSION_NOT_FOUND`; phiên đích của mình đã thu hồi/hết hạn vẫn thành công nếu phiên gọi hợp lệ |
| 19 | `POST /api/v1/users/me/sessions/revoke-others` | 200 `OTHER_SESSIONS_REVOKED` | Giữ phiên hiện tại; không có phiên cần thu hồi trả revokedCount 0; transaction lỗi không trả số đếm một phần |
| 20 | `POST /api/v1/internal/oauth2/introspect` | 200 active true/false | Bộ lỗi 400/401/405/415/500/503 và header riêng theo bảng introspection, không dùng messageCode/envelope chung |

Ngày 2026-10-31 chốt thứ tự kiểm tra API nội bộ: danh tính service → quyền service → actor Admin khi cần → nghiệp vụ; trả lỗi của bước đầu tiên không đạt. Q10 đã chốt retry/đối soát timeout; không tự đặt thứ tự ưu tiên cho mọi tổ hợp validation. Introspection kiểm tra danh tính/quyền Gateway trước token người dùng theo xác nhận riêng. Các tình huống/mã ngoài danh mục tiếp tục thuộc Q09.

<a id="exceptions"></a>

## Ngoại lệ và xử lý ở client

- Xin mã quên mật khẩu: email không tồn tại hoặc bước gửi SMTP lỗi/timeout trả 200/thông báo chung; không trả USER_NOT_FOUND/EMAIL_NOT_FOUND. Lỗi database vẫn trả lỗi phù hợp.
- Xin OTP REGISTER/SET_PASSWORD: SMTP lỗi/timeout sau commit trả 503 SERVICE_UNAVAILABLE; mã đã cấp vẫn tính giới hạn gửi, không phục hồi mã cũ hoặc tự cấp mã retry.
- Cập nhật trạng thái: timeout cần đọc lại để đối soát, không tự gửi lại lệnh cũ; gửi lại là lệnh mới. Cấm khóa đối tượng ADMIN, kể cả tự khóa, dùng 403 ACCESS_DENIED.
- Hoàn tất đăng ký Google: email trong registrationToken đã được tài khoản khác sử dụng trước khi hoàn tất trả 409 EMAIL_ALREADY_EXISTS, rollback đăng ký; hướng người dùng quay lại đăng nhập Google theo luồng đã xác nhận. Không tự tạo/liên kết hoặc cấp token tại request hoàn tất bị từ chối.
- Logout: token không tìm thấy/phiên đã kết thúc trả 200/kết quả chung, không khẳng định vừa thu hồi được phiên phía server. Database/transaction lỗi không báo thành công.
- UC11: phiên đích của mình đã thu hồi hoặc hết hạn trả 200 nếu phiên gọi hợp lệ; không ghi đè thời gian/lý do thu hồi cũ, không cập nhật lại phiên hết hạn.
- PATCH hồ sơ: gửi lại giá trị đang có trả 200, không phải xung đột; giữ trigger UpdatedAt hiện hành.
- 429 kèm `Retry-After` tính bằng giây, theo thời điểm thực sự được xin mã tiếp trong chính sách đã chốt. Giới hạn nhập sai OTP trả 400 `OTP_ATTEMPTS_EXCEEDED`, không dùng 429 thay cho yêu cầu xin mã mới.
- 503 không tự khiến client xóa token hoặc kết luận phiên bị thu hồi; không tự retry vô hạn.

<a id="introspection-errors"></a>

## Introspection tách khỏi envelope chung

**Đã xác nhận ngày 2026-10-26:** [request/response introspection](sessions.md#introspect) dùng form request và JSON riêng, không dùng envelope/messageCode chung hoặc data.errors. Token người dùng không còn hiệu lực trả HTTP 200 với `{"active": false}`, không tiết lộ nguyên nhân. Lỗi danh tính/quyền Gateway tách khỏi inactive; kiểm tra danh tính/quyền Gateway trước xử lý token người dùng.

| Tình huống | HTTP | JSON body | Header bổ sung |
| --- | --- | --- | --- |
| Thiếu thông tin xác thực Gateway | 401 | `{}` | `WWW-Authenticate: Bearer` |
| Service JWT sai/hết hạn | 401 | `{"error":"invalid_token"}` | `WWW-Authenticate: Bearer error="invalid_token"` |
| Service JWT hợp lệ nhưng thiếu quyền gọi introspection | 401 | `{"error":"insufficient_scope"}` | `WWW-Authenticate: Bearer error="insufficient_scope"` |
| Form thiếu/sai tham số | 400 | `{"error":"invalid_request"}` | Không thêm header riêng theo hợp đồng này |
| Sai HTTP method | 405 | `{"error":"invalid_request"}` | `Allow: POST` |
| Sai media type | 415 | `{"error":"invalid_request"}` | Không thêm header riêng theo hợp đồng này |
| Database/phụ thuộc tạm không khả dụng | 503 | `{"error":"temporarily_unavailable"}` | Không thêm header riêng theo hợp đồng này |
| Lỗi server ngoài dự kiến | 500 | `{"error":"server_error"}` | Không thêm header riêng theo hợp đồng này |

Mọi response introspection có `Content-Type: application/json`, `Cache-Control: no-store`. Body thiếu xác thực là object rỗng, không phải null hoặc body vắng mặt. Các body lỗi còn lại chỉ có trường error kiểu string không null với giá trị tương ứng trong bảng; không trả thêm thông tin danh tính/token hoặc lỗi nội bộ.

Không áp HTTP 403 ACCESS_DENIED của API thông thường để thay introspection 401 khi Gateway thiếu quyền. Không chuyển lỗi hạ tầng thành active false hoặc token hợp lệ. Bộ body lỗi trên là lựa chọn hợp đồng dự án đã chốt, không khẳng định toàn bộ đều do RFC bắt buộc. Bên phát/thuật toán/issuer/audience/subject/TTL/quyền cụ thể và phân phối/rotation khóa Service JWT vẫn thuộc Q11a.

Tham chiếu ngữ nghĩa HTTP: [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html), [429 và Retry-After](https://www.rfc-editor.org/rfc/rfc6585.html#section-4), [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662.html). Các lựa chọn cụ thể cho dự án là quyết định của chủ dự án, không phải toàn bộ đều do RFC quy định.
