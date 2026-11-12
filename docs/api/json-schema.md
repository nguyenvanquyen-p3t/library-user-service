# Quy tắc schema JSON đã xác nhận

Chủ dự án chốt phần schema JSON của Q09 ngày 2026-10-25, bổ sung required/null response, các biến thể Google và quy tắc mở rộng response cùng ngày; chốt schema ba API phiên và introspection ngày 2026-10-26. **Hợp đồng đã xác nhận; login/refresh/logout UC02–UC04 và GET/PATCH hồ sơ UC06 đã triển khai, 15 operation khác còn thiết kế. Đã có [OpenAPI/Swagger UI](openapi.md) từ ngày 2026-11-01.** Tài liệu này duy trì quy tắc HTTP dùng chung; quyết định và phạm vi xác nhận tại [sổ quyết định](../confirmed-decisions.md#json-schema-contract), [schema response](../confirmed-decisions.md#response-schema-contract), [API phiên](../confirmed-decisions.md#session-response-contract) và [introspection](../confirmed-decisions.md#introspection-contract). Tra cứu từng operation tại [bản đồ 20 API](README.md).

Chỉ các quy tắc nêu dưới đây và bộ [HTTP/lỗi](http-errors.md) đã chốt, gồm ba API phiên, danh mục mã phản hồi và [introspection](../confirmed-decisions.md#introspection-contract) bổ sung ngày 2026-10-26. Tổng phạm vi 20 API: 19 API dùng envelope, introspection dùng JSON riêng; tất cả đã có schema request/response trong phạm vi được xác nhận. Hành vi tích hợp được chốt bổ sung ngày 2026-10-31 theo mục cuối tài liệu; cấu hình JWT và tình huống/mã ngoài danh mục vẫn còn mở. Không suy ra hợp đồng hoàn chỉnh từ ví dụ.

<a id="envelope"></a>

## Envelope

**Trạng thái ngày 2026-11-10:** login UC02 và nền tảng bảo mật đã triển khai; các operation còn lại tiếp tục là thiết kế. Request login giữ nguyên identifier/password, không thêm metadata; response `TokenPair` không đổi. Xem [implementation](../user-security.md).

Các API dùng envelope luôn trả đủ bốn trường:

| Trường | Kiểu và ý nghĩa |
| --- | --- |
| `success` | boolean, không null; kết quả thao tác |
| `messageCode` | string, không null; mã để client xử lý; áp dụng danh mục lỗi/mã thành công đã chốt cho 19 API dùng envelope |
| `message` | string, không null; nội dung hiển thị |
| `data` | Schema cụ thể của từng operation; thao tác không trả dữ liệu dùng null |

Không khai báo `data` thành object tùy ý để thay cho schema từng API. Lỗi validation dùng `data.errors` theo [envelope lỗi đã chốt](http-errors.md#error-body); lỗi nghiệp vụ/xác thực không cần chỉ ra trường dùng data null.

**Ngoại lệ đã chốt:** introspection nhận `application/x-www-form-urlencoded`, trả JSON RFC 7662 không bọc envelope; không áp quy tắc request JSON cho form này.

<a id="requests"></a>

## Request: bắt buộc, bỏ qua và null

Các request JSON có body dùng object. Trường bắt buộc trong bảng phải xuất hiện và không nhận null. `phoneNumber` trong đăng ký có thể bỏ qua hoặc gửi null, cùng nghĩa là chưa cung cấp số điện thoại.

| Operation | Trường bắt buộc, không null | Trường tùy chọn |
| --- | --- | --- |
| `POST /api/v1/auth/register/request-code` | `userName`, `email` | Không |
| `POST /api/v1/auth/register` | `userName`, `email`, `password`, `fullName`, `verificationCode` | `phoneNumber`: string hoặc null |
| `POST /api/v1/auth/login` | `identifier`, `password` | Không |
| `POST /api/v1/auth/refresh` | `refreshToken` | Không |
| `POST /api/v1/auth/logout` | `refreshToken` | Không |
| `POST /api/v1/auth/forgot-password/request-code` | `email` | Không |
| `POST /api/v1/auth/reset-password` | `email`, `verificationCode`, `newPassword`, `confirmPassword` | Không |
| `PUT /api/v1/users/me/password` | `currentPassword`, `newPassword`, `confirmPassword` | Không |
| `POST /api/v1/auth/google` | `idToken` | Không |
| `POST /api/v1/auth/google/register` | `registrationToken`, `userName`, `fullName` | `phoneNumber`: string hoặc null |
| `POST /api/v1/users/me/password/set` | `verificationCode`, `newPassword`, `confirmPassword` | Không |
| `PATCH /api/v1/internal/users/{id}/status` | `status` | Không |

`PATCH /api/v1/users/me` theo [quy tắc PATCH](#profile-patch). Các operation hiện không có body giữ nguyên: xem hồ sơ, xin OTP thiết lập mật khẩu, tra cứu user nội bộ, danh sách phiên, thu hồi một phiên và thu hồi các phiên khác. Danh tính/điều kiện xác thực vẫn theo từng operation; schema không thay thế kiểm tra quyền.

Mọi request JSON từ chối trường ngoài danh sách, biểu diễn bằng `additionalProperties: false` khi tạo schema. Không âm thầm bỏ qua `roles`, `status` hoặc trường client không được phép sửa trong request hồ sơ. Response có [quy tắc mở rộng riêng](#response-evolution).

<a id="validation"></a>

## Kiểu dữ liệu và validation

| Trường/nhóm | Quy tắc đã chốt |
| --- | --- |
| `userName` | Chuẩn hóa trim/lowercase trước lưu và tra cứu; sau chuẩn hóa dài 1–50 ký tự; không whitespace, ký tự điều khiển hoặc `@`, phù hợp V3 |
| `email` | Trim/lowercase, tối đa 255 ký tự, kiểm tra định dạng email; không whitespace/ký tự điều khiển theo V3. OTP mới chứng minh quyền truy cập email |
| `identifier` | Chuỗi username/email, phân loại theo `@`, chuẩn hóa và kiểm tra theo trường tương ứng như [mô hình dữ liệu](../data-model.md#lưu-ý-cho-bước-triển-khai-backend) |
| `fullName` | Trim, dài 1–150 ký tự sau chuẩn hóa; cho phép Unicode/khoảng trắng giữa các từ, không trắng toàn bộ hoặc chứa ký tự điều khiển |
| `phoneNumber` | Khi là string: 8–15 chữ số ASCII, có thể bắt đầu bằng một dấu `+`; pattern `^\+?[0-9]{8,15}$`. Không cố định đầu số Việt Nam; chuỗi rỗng hoặc có dấu cách/dấu phân cách không hợp lệ |
| Mật khẩu mới | Khi đăng ký, đổi, reset hoặc thiết lập: 6–128 ký tự, Unicode/khoảng trắng được phép, không trim/cắt ngắn; giữ [chính sách mật khẩu](../confirmed-decisions.md#otp-password-policy) |
| `confirmPassword` | Phải khớp chính xác mật khẩu mới; quy tắc so sánh ghi trong mô tả và kiểm tra phía server, không chỉ dựa vào schema kiểu string |
| `verificationCode` | string đúng 6 chữ số ASCII, pattern `^[0-9]{6}$`; giữ được số 0 ở đầu |
| Token trong JSON | string không rỗng, giữ nguyên giá trị, không trim hoặc lowercase; kiểm tra hiệu lực/mục đích theo từng loại token |
| User ID | integer dương, `int32`; không đổi thành string trong JSON user hoặc path parameter. `sub` trong JWT/introspection giữ kiểu string theo hợp đồng token |
| Session ID | string UUID |
| Thời gian dạng chuỗi | Có múi giờ, response thống nhất UTC; không đổi `exp`/`iat` số trong JWT/introspection thành chuỗi ngày giờ |
| `status` | `ACTIVE` hoặc `BANNED` |

Mật khẩu dùng để đăng nhập/xác thực hiện tại vẫn được so sánh nguyên giá trị, không trim. Bảng này không tự thêm chính sách mật khẩu mới cho dữ liệu mật khẩu hiện tại. Kiểm tra định dạng không thay thế xác minh email, Google ID Token, OTP, session hoặc quyền service. Ánh xạ thiếu trường/sai kiểu/định dạng/giới hạn/trường lạ theo [10 mã con validation chốt ngày 2026-10-26](http-errors.md#validation-codes); giữ HTTP 400/messageCode VALIDATION_ERROR và ngoại lệ INVALID_REQUEST cho JSON hỏng/thiếu toàn bộ body.

<a id="profile-patch"></a>

## PATCH hồ sơ

Chỉ nhận `fullName` và `phoneNumber`, phải có ít nhất một trong hai trường.

| Dữ liệu gửi | Xử lý |
| --- | --- |
| Không gửi một trường | Giữ nguyên giá trị trường đó |
| `fullName` là string hợp lệ | Cập nhật sau chuẩn hóa |
| `fullName` là null, rỗng hoặc trắng | Từ chối |
| `phoneNumber` là string hợp lệ | Cập nhật |
| `phoneNumber` là null | Xóa số điện thoại |
| `phoneNumber` là chuỗi rỗng | Từ chối; dùng null để xóa |
| Object rỗng hoặc trường ngoài danh sách | Từ chối |

Ví dụ cập nhật tên và xóa số điện thoại:

```json
{
  "fullName": "Nguyễn Văn A",
  "phoneNumber": null
}
```

PATCH trả 200 khi thành công, kể cả gửi lại giá trị đang có. Body rỗng, trường lạ hoặc validation sai dùng 400 `VALIDATION_ERROR`; JSON không đọc được hoặc thiếu body bắt buộc dùng 400 `INVALID_REQUEST`. Chi tiết theo [HTTP/lỗi đã chốt](http-errors.md).

<a id="responses"></a>

## Tái sử dụng schema response

**Đã xác nhận ngày 2026-10-25:** các schema dưới đây mô tả giá trị `data` bên trong envelope. Mọi trường được liệt kê đều bắt buộc xuất hiện; chỉ trường được ghi rõ mới nhận null. Tên schema là tên dùng trong đặc tả, không tạo thêm trường JSON hoặc đổi cấu trúc hiện hành. Required và khả năng nhận null là hai thuộc tính riêng.

### UserProfile

Dùng chung cho đăng ký Local, xem/cập nhật hồ sơ và `data.user` khi hoàn tất đăng ký Google.

| Trường | Kiểu | Nhận null |
| --- | --- | --- |
| `id` | integer dương, int32 | Không |
| `userName` | string | Không |
| `email` | string | Không |
| `fullName` | string | Không |
| `phoneNumber` | string | Có |
| `status` | string, ACTIVE hoặc BANNED | Không |
| `roles` | array các string | Không |
| `hasPassword` | boolean | Không |
| `linkedProviders` | array các string | Không |

Áp dụng kiểu dữ liệu/validation đã chốt khi tương ứng. `phoneNumber` luôn xuất hiện, chưa có hoặc đã xóa thì trả null. `roles` và `linkedProviders` không có phần tử thì trả `[]`; hỗ trợ nhiều role, không thêm API quản lý role. Không tự thêm timestamp hoặc trường hồ sơ mới.

### TokenPair

| Trường | Kiểu và giá trị | Nhận null |
| --- | --- | --- |
| `accessToken` | string không rỗng | Không |
| `refreshToken` | string không rỗng | Không |
| `tokenType` | string, Bearer | Không |
| `expiresIn` | integer, 3600 giây | Không |

Dùng chung khi đăng nhập, refresh và `data.token` khi hoàn tất đăng ký Google; hai nhánh đăng nhập Google dùng các trường này trực tiếp trong `data` cùng hai cờ. Không tự thêm `sid` vào JSON cấp token; `sid` nằm trong JWT.

### Các schema dùng chung khác

| Schema | Trường bắt buộc và kiểu | Nhận null | Áp dụng |
| --- | --- | --- | --- |
| `OtpRequestResult` | `expiresIn`: integer, 600 giây | Không | Ba API xin OTP REGISTER, PASSWORD_RESET, SET_PASSWORD |
| `InternalUserSummary` | `id`: integer dương/int32; `userName`, `fullName`: string; `status`: ACTIVE hoặc BANNED; `roles`: array các string | Không trường nào | Tra cứu user nội bộ; giữ tập thông tin tối thiểu, không tự thêm email/số điện thoại |
| `UserStatusResult` | `id`: integer dương/int32; `status`: ACTIVE hoặc BANNED | Không trường nào | Cập nhật trạng thái nội bộ |
| `GoogleRegistrationResult` | `user`: UserProfile; `token`: TokenPair | Không trường nào | Hoàn tất đăng ký Google; giữ `data.user` và `data.token` |

Xin mã quên mật khẩu giữ response chung khi email không tồn tại; `OtpRequestResult` không khẳng định mã đã được cấp hoặc email đã gửi cho một tài khoản tồn tại. Bốn thao tác không trả dữ liệu của 16 API gốc (logout, reset mật khẩu, đổi mật khẩu, thiết lập mật khẩu lần đầu) dùng `data: null`.

<a id="google-responses"></a>

### Ba biến thể response Google

`POST /api/v1/auth/google` trả đúng một trong ba biến thể sau. Các cờ là boolean bắt buộc, không null; không có tổ hợp `isNewUser = true` và `accountLinked = true`.

| Nhánh | `isNewUser` | `accountLinked` | Trường bắt buộc trong data |
| --- | --- | --- | --- |
| Identity đã có | false | false | Toàn bộ trường TokenPair và hai cờ |
| Tự liên kết với Local | false | true | Toàn bộ trường TokenPair và hai cờ |
| Cần hoàn tất đăng ký | true | false | `email`, `fullName`, `suggestedUserName`, `registrationToken` và hai cờ |

Hai nhánh đăng nhập có nhóm trường token và hai cờ; nhánh cần đăng ký có nhóm trường đăng ký và hai cờ, chưa có token đăng nhập. Client phân nhánh bằng hai cờ đã được xác định, không đoán qua nội dung `message`.

Trong nhánh cần hoàn tất đăng ký:

- `email` và `registrationToken` là string không rỗng, không null; email lấy từ danh tính Google đã xác minh.
- `fullName` là string hoặc null, luôn xuất hiện. Nếu Google không cung cấp tên phù hợp quy tắc của dự án thì trả null; client để trống và yêu cầu nhập tại bước hoàn tất.
- `suggestedUserName` là string hoặc null, luôn xuất hiện. Không tạo được gợi ý hợp lệ thì trả null. Gợi ý không giữ chỗ hoặc bảo đảm username còn trống; vẫn kiểm tra khi hoàn tất đăng ký.
- `fullName` ở đây là gợi ý trước đăng ký; `UserProfile.fullName` và request hoàn tất đăng ký Google vẫn bắt buộc có tên hợp lệ, không null.

Ví dụ HTTP 200 khi chưa có gợi ý tên/username (schema và messageCode đã xác nhận; message minh họa):

```json
{
  "success": true,
  "messageCode": "GOOGLE_REGISTRATION_REQUIRED",
  "message": "Vui lòng hoàn tất thông tin tài khoản",
  "data": {
    "email": "reader@example.com",
    "fullName": null,
    "suggestedUserName": null,
    "registrationToken": "short-lived-token...",
    "isNewUser": true,
    "accountLinked": false
  }
}
```

<a id="response-evolution"></a>

### Mở rộng response

Client bỏ qua trường response mới chưa sử dụng để giữ khả năng tương thích khi hợp đồng được bổ sung. Backend chỉ trả các trường được đưa vào hợp đồng, dùng DTO response riêng để kiểm soát dữ liệu; không trả trực tiếp entity chứa hash hoặc thông tin nội bộ. Quy tắc này không tự cho phép thêm trường mới chưa được xác nhận và không thay quy tắc từ chối trường lạ của request JSON.

<a id="session-responses"></a>

### Schema response của ba API phiên

**Đã xác nhận ngày 2026-10-26:** giữ envelope chung và các operation không có request body. Chi tiết trường/kiểu, phân trang và các ví dụ duy trì tại [API phiên](sessions.md#session-list).

| Operation | Schema của data | Quy tắc |
| --- | --- | --- |
| GET danh sách phiên (UC10) | `SessionPage` | items là array SessionItem; page/size int32 và totalElements int64; mọi trường bắt buộc, không null |
| DELETE một phiên khác (UC11) | null | Luôn có data null khi thành công, kể cả phiên đích của mình đã thu hồi/hết hạn nếu phiên gọi hợp lệ |
| POST thu hồi các phiên khác (UC12) | `RevokeOthersResult` | Chỉ có revokedCount integer/int64 từ 0, bắt buộc và không null; đếm phiên vừa thu hồi thành công |

SessionItem bắt buộc đủ bảy trường; chỉ deviceName/clientType nhận null. SessionPage.items có thể rỗng, giữ page/size được yêu cầu khi trang vượt kết quả. Áp dụng quy tắc client bỏ qua trường response mới chưa sử dụng; backend chỉ trả trường thuộc hợp đồng.

<a id="introspection-schemas"></a>

### Schema introspection đã chốt ngày 2026-10-26

Introspection là API thứ 20, không dùng envelope/messageCode chung. Request form và chi tiết trường/kiểu duy trì tại [introspection](sessions.md#introspect); body lỗi/header tại [HTTP/lỗi](http-errors.md#introspection-errors).

| Schema | Quy tắc đã xác nhận |
| --- | --- |
| `IntrospectionRequest` | Form chỉ có token bắt buộc/string không rỗng và token_type_hint tùy chọn/string; không trim token, không giới hạn hint bằng enum; lặp tham số hoặc trường ngoài danh sách bị từ chối |
| `IntrospectionActiveResponse` | HTTP 200, active true; đủ chín trường bắt buộc không null theo bảng tại API phiên/introspection; aud luôn là mảng không rỗng, roles từ database |
| `IntrospectionInactiveResponse` | HTTP 200, chỉ có active false; không thêm lý do, dữ liệu user/role hoặc trường null |
| Body lỗi | Thiếu xác thực Gateway là object rỗng {}; các lỗi khác chỉ có error string không null với giá trị tương ứng bảng HTTP/lỗi; không dùng data.errors |

Nguồn roles của Q14 đã chốt từ UserRoles/Roles tại thời điểm kiểm tra. Kiểm tra danh tính/quyền Gateway trước xử lý token người dùng; lỗi hạ tầng không trở thành inactive. Mọi response có Content-Type application/json, Cache-Control no-store.

Schema của 20 API đã xác nhận; danh mục mã thành công áp dụng cho 19 API dùng envelope, introspection không có messageCode. Ngày 2026-10-31 chốt [Service JWT/quyền và header actor Admin](../confirmed-decisions.md#internal-service-auth), [hành vi SMTP](../confirmed-decisions.md#otp-email-delivery), [quản trị trạng thái](../confirmed-decisions.md#admin-status-contract), [lỗi tích hợp bổ sung](../confirmed-decisions.md#integration-error-contract) và [user access token Q14](../confirmed-decisions.md#access-token-key-pair). User JWT có issuer chung urn:library:user-service, aud ["library-api"] và header kid user-access-v1; kid không thêm vào JSON cấp token hoặc response introspection. X-Admin-Access-Token là header xác thực bổ sung của API trạng thái, không thêm trường JSON. Chỉ cấu hình Service JWT Q03/Q11a vẫn PENDING DECISION; Q06 chỉ còn lựa chọn/cấu hình môi trường SMTP, Q09 chỉ còn tình huống/mã ngoài danh mục.
