# Thiết kế hiện hành của 16 API gốc

Cập nhật tổng hợp: 2026-10-24; đồng bộ Q13/schema JSON/HTTP/lỗi ngày 2026-10-25, danh mục mã phản hồi và email trùng khi hoàn tất đăng ký Google ngày 2026-10-26. **Login UC02 đã triển khai ngày 2026-11-10; refresh/logout UC03–UC04 ngày 2026-11-11, GET/PATCH hồ sơ UC06 ngày 2026-11-12; đổi mật khẩu UC07 ngày 2026-11-13. Trong 16 API gốc có 6 IMPLEMENTED/10 DESIGN_ONLY; tổng 20 operation có 6/14.** “Hiện hành” nghĩa là ví dụ nguồn đã được đối chiếu với quyết định mới nhất; chỉ operation ghi rõ IMPLEMENTED đã có endpoint. [Bản đồ 20 API](README.md) liên kết thêm [4 API phiên/introspection](sessions.md).

## Cách đọc trạng thái

**Quyết định ngày 2026-11-10:** [hoãn xác thực service đến sau khi hoàn tất chức năng](../confirmed-decisions.md#deferred-service-auth). Giai đoạn code chức năng mặc định tin tưởng service; Q03 chưa chốt cấu hình không chặn hai API nội bộ. Các yêu cầu/lỗi Service JWT trong tài liệu là thiết kế đích cho bước sau. Giữ xác thực user/phiên, ownership và actor ADMIN.

Bổ sung ngày 2026-10-31: [Q03](../confirmed-decisions.md#internal-service-auth) chốt Service JWT/quyền và actor Admin; [Q06](../confirmed-decisions.md#otp-email-delivery) chốt hành vi SMTP; [Q10](../confirmed-decisions.md#admin-status-contract) chốt trạng thái/retry/audit; [Q09](../confirmed-decisions.md#integration-error-contract) bổ sung lỗi tích hợp. [User access token Q14](../confirmed-decisions.md#access-token-key-pair) đã chốt issuer chung urn:library:user-service, aud ["library-api"], kid user-access-v1 và quy trình cấp/phân phối/thay khóa. Nhãn Q03 còn mở chỉ cấu hình Service JWT, Q06 chỉ lựa chọn/cấu hình môi trường SMTP; Q11a vẫn hoãn, không áp cấu hình user token cho Service JWT.

- **Đã xác nhận:** quy tắc do chủ dự án chốt, dẫn tới sổ quyết định hoặc luồng đã bàn giao; chưa đồng nghĩa có code.
- **Theo nguồn/chưa xác nhận:** các tình huống/mã/chi tiết ngoài [bộ HTTP/lỗi đã chốt](http-errors.md) còn thuộc Q09. Các mã thành công của 16 API bên dưới đã chốt ngày 2026-10-26; required/null request/response, validation và biến thể Google theo [schema JSON đã chốt](json-schema.md). Không suy ra phần còn thiếu từ ví dụ.
- **Đề xuất:** phương án còn cần thống nhất; đề xuất của API phiên được giữ ở tài liệu riêng, không chuyển thành quyết định tại đây.
- **PENDING DECISION:** câu hỏi có ID trong [sổ quyết định còn mở](../open-decisions.md); cần chốt phần liên quan trước triển khai/tích hợp.

Request có body của 16 API bên dưới dùng JSON; API không có body được ghi riêng. [Schema JSON](json-schema.md) quy định envelope, required/null request/response, validation, PATCH và quy tắc tái sử dụng/mở rộng response; [HTTP/lỗi](http-errors.md) duy trì status, danh mục mã đã chốt, data.errors, Retry-After và ngoại lệ. Danh sách status từng operation bên dưới không lặp 405 METHOD_NOT_ALLOWED/415 UNSUPPORTED_MEDIA_TYPE dùng chung; API được bảo vệ dùng 401 AUTHENTICATION_REQUIRED khi chưa cung cấp xác thực, không thay validation của trường password thiếu trong JSON login. Các danh sách không tự bổ sung mã hoặc hành vi còn mở. Tình huống/mã/chi tiết lỗi ngoài danh mục vẫn thuộc [Q09](../open-decisions.md#q09). Introspection dùng form URL encoded và response RFC 7662 không bọc envelope, xem API phiên.

**Schema data thành công đã xác nhận ngày 2026-10-25:** mọi trường của schema đều bắt buộc xuất hiện; chỉ các trường được nêu tại [schema response](json-schema.md#responses) nhận null. [Mã thành công](http-errors.md#success-codes) đã xác nhận ngày 2026-10-26; nội dung message là minh họa.

| Operation/nhóm | Schema của data |
| --- | --- |
| Đăng ký Local; GET/PATCH hồ sơ | `UserProfile` |
| Login; refresh | `TokenPair` |
| Ba API xin OTP REGISTER/PASSWORD_RESET/SET_PASSWORD | `OtpRequestResult` |
| Logout; reset/đổi/thiết lập mật khẩu | null |
| `POST /api/v1/auth/google` | [Ba biến thể Google](json-schema.md#google-responses), theo cặp isNewUser/accountLinked |
| Hoàn tất đăng ký Google | `GoogleRegistrationResult`, giữ user/token lồng trong data |
| Tra cứu user nội bộ | `InternalUserSummary` |
| Cập nhật trạng thái nội bộ | `UserStatusResult` |

Mọi dữ liệu là minh họa. Ví dụ cấp access token dùng expiresIn 3600 theo [TTL đã chốt](../confirmed-decisions.md#user-tokens); OTP 600 giây. sid nằm trong JWT và được kiểm tra cùng phiên; không tự thêm trường JSON ngoài hợp đồng nguồn. Refresh token tiếp tục qua JSON cho Web/Android. Các token mẫu không sử dụng được.

Request người dùng được bảo vệ lấy danh tính từ JWT đã xác minh, user ACTIVE và phiên đúng ownership/còn hiệu lực. JWT còn hạn không đủ khi phiên bị thu hồi/hết hạn. Refresh/logout và hai API nội bộ có điều kiện xác thực riêng tại từng operation. Chính sách duy trì tại [sổ quyết định](../confirmed-decisions.md); thay đổi dữ liệu tuân thủ [transaction và cạnh tranh](../requirements.md#ranh-giới-transaction).

Không chỉnh dữ liệu trong [danh mục nguồn](source-catalog.md) để giống bản hiện hành. Khi có quyết định mới, cập nhật bản này theo [quy ước bảo trì](../documentation.md#quy-tắc-bảo-trì).

<a id="register-request-code"></a>

## POST /api/v1/auth/register/request-code

**Luồng:** UC01. **Nguồn:** [Sheet1, hàng 2](source-catalog.md#post-apiv1authregisterrequest-code).

**Xác thực/đầu vào:** Công khai; không yêu cầu access token.

**Đã xác nhận — hành vi:** Kiểm tra username/email chưa được sử dụng, cấp OTP REGISTER trước khi có user. Chưa lưu password/dữ liệu đăng ký tạm. OTP gắn email/purpose, không gắn username; xem [OTP REGISTER](../confirmed-decisions.md#register-otp-binding).

**Bổ sung đã xác nhận ngày 2026-10-31:** commit hash OTP mới và vô hiệu mã cũ trước khi gửi SMTP đồng bộ. SMTP lỗi/timeout trả 503 SERVICE_UNAVAILABLE; lần cấp mã vẫn tính giới hạn, không phục hồi mã cũ hoặc tự cấp mã retry. Chi tiết tại [Q06](../confirmed-decisions.md#otp-email-delivery).

**PENDING DECISION:** [Q06](../open-decisions.md#q06), [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 409, 429, 500, 503`.

**Request minh họa** (đối chiếu ô H2):

```json
{
  "userName": "reader01",
  "email": "reader@example.com"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I2):

```json
{
  "success": true,
  "messageCode": "REGISTER_CODE_SENT",
  "message": "Mã xác minh đã được gửi đến email",
  "data": {
    "expiresIn": 600
  }
}
```

<a id="register"></a>

## POST /api/v1/auth/register

**Luồng:** UC01. **Nguồn:** [Sheet1, hàng 3](source-catalog.md#post-apiv1authregister).

**Xác thực/đầu vào:** Công khai; OTP REGISTER đúng email/purpose.

**Đã xác nhận — hành vi:** Xác minh OTP trước khi tạo user ACTIVE, hash password, gán READER và dùng mã một lần cùng transaction. Không cấp token hoặc tạo phiên; đăng nhập riêng. Xem [vòng đời tài khoản](../confirmed-decisions.md#account-lifecycle) và [OTP/mật khẩu](../confirmed-decisions.md#otp-password-policy).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `201 / 400, 409, 500, 503`.

**Request minh họa** (đối chiếu ô H3):

```json
{
  "userName": "reader01",
  "email": "reader@example.com",
  "password": "Abc@123456",
  "fullName": "Nguyen Van A",
  "phoneNumber": "0901234567",
  "verificationCode": "583921"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I3):

```json
{
  "success": true,
  "messageCode": "REGISTER_SUCCESS",
  "message": "Đăng ký tài khoản thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "email": "reader@example.com",
    "fullName": "Nguyen Van A",
    "phoneNumber": "0901234567",
    "status": "ACTIVE",
    "roles": [
      "READER"
    ],
    "hasPassword": true,
    "linkedProviders": []
  }
}
```

<a id="login"></a>

## POST /api/v1/auth/login

**Đã triển khai — IMPLEMENTED ngày 2026-11-10.** [Nền tảng bảo mật/transaction](../user-security.md): Argon2id, khóa user đến commit, tạo phiên/refresh token và ký JWT trong transaction; lỗi commit không trả thành công. Không nhận metadata thiết bị. Response thành công có `Cache-Control: no-store`; chưa có endpoint Google/UC09 để thực hiện hướng dẫn của nhánh chưa có password.

**Luồng:** UC02. **Nguồn:** [Sheet1, hàng 4](source-catalog.md#post-apiv1authlogin).

**Xác thực/đầu vào:** Username/email và mật khẩu; không yêu cầu access token.

**Đã xác nhận — hành vi:** User ACTIVE có password đúng mới được đăng nhập. Tạo session/refresh token, JWT RS256 chứa sub/sid; chỉ trả token sau commit. Chưa có password thì Google và UC09. Xem [token](../confirmed-decisions.md#user-tokens) và [TTL](../confirmed-decisions.md#session-lifetime).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 403, 409, 500, 503`.

**Request minh họa** (đối chiếu ô H4):

```json
{
  "identifier": "reader@example.com",
  "password": "Abc@123456"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I4):

```json
{
  "success": true,
  "messageCode": "LOGIN_SUCCESS",
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8K3mP7q...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

**Response lỗi minh họa — HTTP 409 và messageCode đã xác nhận** (đối chiếu ô J4, message minh họa):

```json
{
  "success": false,
  "messageCode": "PASSWORD_NOT_CONFIGURED",
  "message": "Tài khoản chưa thiết lập mật khẩu. Vui lòng đăng nhập bằng Google hoặc thiết lập mật khẩu.",
  "data": null
}
```

<a id="refresh"></a>

## POST /api/v1/auth/refresh

**Đã triển khai — IMPLEMENTED ngày 2026-11-11.** [Implementation](../user-security.md#refresh-và-logout-uc03uc04): transaction READ_COMMITTED, khóa user rồi đọc lại token/phiên; ưu tiên lỗi token → user → phiên. Không cập nhật LastSeenAt; mọi response có Cache-Control no-store.

**Luồng:** UC03. **Nguồn:** [Sheet1, hàng 5](source-catalog.md#post-apiv1authrefresh).

**Xác thực/đầu vào:** Refresh token trong JSON; không yêu cầu access token còn hạn.

**Đã xác nhận — hành vi:** Kiểm tra token, user ACTIVE và session còn hiệu lực. Rotation một lần, giữ sid, không gia hạn session. Reuse chỉ bị từ chối, không thu hồi session/token thay thế vì sự kiện này. Session hết hạn thì từ chối dù token còn hạn; xem [reuse](../confirmed-decisions.md#refresh-reuse) và [TTL](../confirmed-decisions.md#session-lifetime).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 403, 500, 503`.

**Request minh họa** (đối chiếu ô H5):

```json
{
  "refreshToken": "d8K3mP7q..."
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I5):

```json
{
  "success": true,
  "messageCode": "TOKEN_REFRESH_SUCCESS",
  "message": "Làm mới token thành công",
  "data": {
    "accessToken": "new-access-token...",
    "refreshToken": "new-refresh-token...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  }
}
```

<a id="logout"></a>

## POST /api/v1/auth/logout

**Đã triển khai — IMPLEMENTED ngày 2026-11-11.** [Implementation](../user-security.md#refresh-và-logout-uc03uc04): không yêu cầu access token hoặc user ACTIVE; transaction READ_COMMITTED, khóa user, thu hồi phiên với LOGOUT và token còn hiệu lực. Mọi response có Cache-Control no-store.

**Luồng:** UC04. **Nguồn:** [Sheet1, hàng 6](source-catalog.md#post-apiv1authlogout).

**Xác thực/đầu vào:** Refresh token trong JSON, kể cả token cũ còn xác định được phiên; không yêu cầu access token còn hạn.

**Đã xác nhận — hành vi:** Thu hồi đúng session/token liên quan; session đã hết hạn/thu hồi trả thành công, giữ thời điểm/lý do cũ. Token không tìm thấy chỉ bảo đảm đăng xuất client; database lỗi không báo thu hồi server thành công. Client xóa token. Xem [UC04](../confirmed-decisions.md#logout); LOGOUT_SUCCESS bên dưới không chứng minh server vừa thu hồi được session.

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 500, 503`.

**Request minh họa** (đối chiếu ô H6):

```json
{
  "refreshToken": "d8K3mP7q..."
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I6):

```json
{
  "success": true,
  "messageCode": "LOGOUT_SUCCESS",
  "message": "Đăng xuất thành công",
  "data": null
}
```

<a id="forgot-password-request-code"></a>

## POST /api/v1/auth/forgot-password/request-code

**Luồng:** UC05. **Nguồn:** [Sheet1, hàng 7](source-catalog.md#post-apiv1authforgot-passwordrequest-code).

**Xác thực/đầu vào:** Công khai; email tài khoản cần reset.

**Đã xác nhận — hành vi:** Cấp OTP PASSWORD_RESET, email không tồn tại nhận thông báo chung. Chưa có password thì Google và UC09. Xem [OTP/mật khẩu](../confirmed-decisions.md#otp-password-policy) và [UC05](../use-cases.md#uc05-quên-và-đặt-lại-mật-khẩu).

**Bổ sung đã xác nhận ngày 2026-10-31:** gửi SMTP đồng bộ sau commit OTP. Bước gửi SMTP lỗi/timeout vẫn trả 200 PASSWORD_RESET_CODE_SENT với kết quả chung, ghi lỗi vận hành nội bộ; không che lỗi database thành thành công. Lần cấp mã đã commit vẫn tính giới hạn gửi, không tự cấp lại. Chi tiết tại [Q06](../confirmed-decisions.md#otp-email-delivery).

**PENDING DECISION:** [Q06](../open-decisions.md#q06), [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 409, 429, 500, 503`.

**Request minh họa** (đối chiếu ô H7):

```json
{
  "email": "reader@example.com"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I7):

```json
{
  "success": true,
  "messageCode": "PASSWORD_RESET_CODE_SENT",
  "message": "Nếu email tồn tại trong hệ thống, mã xác minh sẽ được gửi",
  "data": {
    "expiresIn": 600
  }
}
```

<a id="reset-password"></a>

## POST /api/v1/auth/reset-password

**Luồng:** UC05. **Nguồn:** [Sheet1, hàng 8](source-catalog.md#post-apiv1authreset-password).

**Xác thực/đầu vào:** Công khai; OTP PASSWORD_RESET đúng email/purpose và mật khẩu mới.

**Đã xác nhận — hành vi:** Theo [Q13 chốt ngày 2026-10-25](../confirmed-decisions.md#password-otp-flow), gửi email, OTP, mật khẩu mới và xác nhận trong cùng request hoàn tất; server xác minh OTP và kiểm tra mật khẩu trước khi lưu, không có API xác minh riêng hoặc ticket. Cập nhật PasswordHash, dùng mã một lần và thu hồi mọi phiên/refresh token cùng transaction; không mở khóa BANNED. Chưa có password thì Google/UC09. Xem [UC05](../use-cases.md#uc05-quên-và-đặt-lại-mật-khẩu).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 409, 500, 503`.

**Request minh họa** (đối chiếu ô H8):

```json
{
  "email": "reader@example.com",
  "verificationCode": "628351",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I8):

```json
{
  "success": true,
  "messageCode": "PASSWORD_RESET_SUCCESS",
  "message": "Đặt lại mật khẩu thành công",
  "data": null
}
```

**Response lỗi minh họa — HTTP 409 và messageCode đã xác nhận** (đối chiếu ô J8, message minh họa):

```json
{
  "success": false,
  "messageCode": "PASSWORD_NOT_CONFIGURED",
  "message": "Tài khoản chưa thiết lập mật khẩu. Vui lòng đăng nhập bằng Google và sử dụng chức năng thiết lập mật khẩu.",
  "data": null
}
```

<a id="profile-get"></a>

## GET /api/v1/users/me

**Đã triển khai — IMPLEMENTED ngày 2026-11-12.** [Implementation UC06](../user-profile.md): READ_COMMITTED/readOnly, user từ principal, role/provider hiện tại từ database; user không còn trả 401 SESSION_INACTIVE. Không thay đổi dữ liệu.

**Luồng:** UC06. **Nguồn:** [Sheet1, hàng 9](source-catalog.md#get-apiv1usersme).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE, phiên đúng user từ sub/sid và còn hiệu lực.

**Đã xác nhận — hành vi:** Chỉ trả hồ sơ người gọi, không nhận userId tùy ý, không trả hash/secret. Chỉ xem không thay đổi hồ sơ. Xem [UC06](../use-cases.md#uc06-xem-và-cập-nhật-hồ-sơ).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 401, 500, 503`.

**Request không có body.**

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I9):

```json
{
  "success": true,
  "messageCode": "PROFILE_GET_SUCCESS",
  "message": "Lấy thông tin cá nhân thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "email": "reader@example.com",
    "fullName": "Nguyen Van A",
    "phoneNumber": "0901234567",
    "status": "ACTIVE",
    "roles": [
      "READER"
    ],
    "hasPassword": true,
    "linkedProviders": [
      "GOOGLE"
    ]
  }
}
```

<a id="profile-update"></a>

## PATCH /api/v1/users/me

**Đã triển khai — IMPLEMENTED ngày 2026-11-12.** [Implementation UC06](../user-profile.md): READ_COMMITTED, khóa user rồi kiểm tra lại user ACTIVE/phiên đúng owner và còn hiệu lực, lấy now sau khóa; không đạt trả 401 SESSION_INACTIVE. Chỉ lưu trường hiện diện; trigger giữ UpdatedAt; lỗi lưu/dựng DTO/commit rollback. Thành công sau commit.

**Luồng:** UC06. **Nguồn:** [Sheet1, hàng 10](source-catalog.md#patch-apiv1usersme).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE và phiên hợp lệ.

**Đã xác nhận — hành vi:** Chỉ sửa fullName/phoneNumber của mình, không sửa roles/status/password/username/email. Theo [PATCH đã chốt](json-schema.md#profile-patch), bỏ qua trường để giữ nguyên; fullName không null/trắng, phoneNumber null để xóa, chuỗi rỗng bị từ chối; không nhận body rỗng hoặc trường ngoài danh sách. Trigger cập nhật Users.UpdatedAt khi dữ liệu thực sự đổi; xem [schema](../data-model.md#token-và-thời-gian).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 500, 503`.

**Request minh họa** (đối chiếu ô H10):

```json
{
  "fullName": "Nguyen Van B",
  "phoneNumber": "0987654321"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I10):

```json
{
  "success": true,
  "messageCode": "PROFILE_UPDATE_SUCCESS",
  "message": "Cập nhật thông tin cá nhân thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "email": "reader@example.com",
    "fullName": "Nguyen Van B",
    "phoneNumber": "0987654321",
    "status": "ACTIVE",
    "roles": [
      "READER"
    ],
    "hasPassword": true,
    "linkedProviders": [
      "GOOGLE"
    ]
  }
}
```

<a id="password-change"></a>

## PUT /api/v1/users/me/password

**Đã triển khai — IMPLEMENTED ngày 2026-11-13.** [UC07](../user-password.md) dùng READ_COMMITTED, khóa user rồi kiểm tra lại user/phiên; mất hiệu lực trả 401 SESSION_INACTIVE. Đổi hash Argon2id và thu hồi mọi phiên/token còn hiệu lực, kể cả phiên gọi, cùng transaction. Phiên ghi PASSWORD_CHANGED; giữ nguyên bản ghi đã hết hạn/thu hồi và identity Google. Thành công sau commit; sai mật khẩu/hash hỏng dùng 400 CURRENT_PASSWORD_INCORRECT.

**Luồng:** UC07. **Nguồn:** [Sheet1, hàng 11](source-catalog.md#put-apiv1usersmepassword).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE, phiên hợp lệ và mật khẩu hiện tại đúng.

**Đã xác nhận — hành vi:** Đổi PasswordHash và thu hồi mọi phiên/refresh token, kể cả phiên gọi, cùng transaction. Giữ identity Google; client xóa token và đăng nhập lại. Chưa có password thì UC09. Xem [UC07](../use-cases.md#uc07-đổi-mật-khẩu).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 409, 500, 503`.

**Request minh họa** (đối chiếu ô H11):

```json
{
  "currentPassword": "Abc@123456",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I11):

```json
{
  "success": true,
  "messageCode": "PASSWORD_CHANGE_SUCCESS",
  "message": "Đổi mật khẩu thành công. Vui lòng đăng nhập lại",
  "data": null
}
```

**Response lỗi minh họa — HTTP 409 và messageCode đã xác nhận** (đối chiếu ô J11, message minh họa):

```json
{
  "success": false,
  "messageCode": "PASSWORD_NOT_CONFIGURED",
  "message": "Tài khoản chưa có mật khẩu. Vui lòng sử dụng chức năng thiết lập mật khẩu.",
  "data": null
}
```

<a id="google"></a>

## POST /api/v1/auth/google

**Luồng:** UC08. **Nguồn:** [Sheet1, hàng 12](source-catalog.md#post-apiv1authgoogle).

**Xác thực/đầu vào:** Công khai; Google ID Token được server xác minh trước khi dùng sub/email/trạng thái email đã xác minh.

**Đã xác nhận — hành vi:** Ba nhánh: identity đã có thì đăng nhập; chưa có identity nhưng email Google đã xác minh trùng Local thì tự liên kết không hỏi mật khẩu Local; người mới nhận registrationToken 10 phút, chưa có phiên. Chỉ user ACTIVE được cấp session/token. Tự liên kết không tạo user hoặc đổi role/password. Xem [Google](../confirmed-decisions.md#google-linking) và [registrationToken](../confirmed-decisions.md#google-registration-ttl).

**PENDING DECISION:** [Q07](../open-decisions.md#q07), [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 403, 500, 503`.

**Request minh họa** (đối chiếu ô H12):

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

### Nhánh A — Identity đã liên kết

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I12):

```json
{
  "success": true,
  "messageCode": "GOOGLE_LOGIN_SUCCESS",
  "message": "Đăng nhập bằng Google thành công",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8K3mP7q...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "isNewUser": false,
    "accountLinked": false
  }
}
```

### Nhánh B — Tự liên kết với Local

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I13):

```json
{
  "success": true,
  "messageCode": "GOOGLE_ACCOUNT_LINKED",
  "message": "Tài khoản Google đã được liên kết và đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8K3mP7q...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "isNewUser": false,
    "accountLinked": true
  }
}
```

### Nhánh C — Cần hoàn tất đăng ký

**Đã xác nhận schema:** email/registrationToken không null; fullName/suggestedUserName luôn xuất hiện và có thể null khi thiếu gợi ý phù hợp. Gợi ý username không bảo đảm còn trống. Request hoàn tất vẫn yêu cầu fullName hợp lệ; xem [biến thể Google](json-schema.md#google-responses).

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I14):

```json
{
  "success": true,
  "messageCode": "GOOGLE_REGISTRATION_REQUIRED",
  "message": "Vui lòng hoàn tất thông tin tài khoản",
  "data": {
    "email": "reader@example.com",
    "fullName": "Nguyen Van A",
    "suggestedUserName": "nguyenvana",
    "registrationToken": "short-lived-token...",
    "isNewUser": true,
    "accountLinked": false
  }
}
```

<a id="google-register"></a>

## POST /api/v1/auth/google/register

**Luồng:** UC08. **Nguồn:** [Sheet1, hàng 15](source-catalog.md#post-apiv1authgoogleregister).

**Xác thực/đầu vào:** Công khai; registrationToken hợp lệ, còn hạn, đúng mục đích hoàn tất đăng ký Google.

**Đã xác nhận — hành vi:** Tạo user ACTIVE với PasswordHash NULL, identity GOOGLE, READER, session/refresh token cùng transaction. Identity đã có thì từ chối, không cấp session/token; UNIQUE xung đột rollback toàn bộ. Đã commit mất response thì đăng nhập Google. Không gia hạn token khi sửa dữ liệu; không bảo đảm dùng một lần tuyệt đối. Xem [chống lặp](../confirmed-decisions.md#google-replay).

**Bổ sung đã xác nhận ngày 2026-10-26:** email trong registrationToken đã được tài khoản khác sử dụng trước khi hoàn tất trả HTTP 409 EMAIL_ALREADY_EXISTS, rollback đăng ký; hướng người dùng quay lại đăng nhập Google. Không tự liên kết/cấp token tại request hoàn tất bị từ chối.

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `201 / 400, 401, 409, 500, 503`.

**Request minh họa** (đối chiếu ô H15):

```json
{
  "registrationToken": "short-lived-token...",
  "userName": "reader01",
  "fullName": "Nguyen Van A",
  "phoneNumber": "0901234567"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I15):

```json
{
  "success": true,
  "messageCode": "GOOGLE_REGISTER_SUCCESS",
  "message": "Đăng ký bằng Google thành công",
  "data": {
    "user": {
      "id": 10,
      "userName": "reader01",
      "email": "reader@example.com",
      "fullName": "Nguyen Van A",
      "phoneNumber": "0901234567",
      "status": "ACTIVE",
      "roles": [
        "READER"
      ],
      "hasPassword": false,
      "linkedProviders": [
        "GOOGLE"
      ]
    },
    "token": {
      "accessToken": "eyJhbGciOi...",
      "refreshToken": "d8K3mP7q...",
      "tokenType": "Bearer",
      "expiresIn": 3600
    }
  }
}
```

<a id="password-request-code"></a>

## POST /api/v1/users/me/password/request-code

**Luồng:** UC09. **Nguồn:** [Sheet1, hàng 16](source-catalog.md#post-apiv1usersmepasswordrequest-code).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE, phiên hợp lệ, chưa có password. Email lấy từ tài khoản đã xác minh.

**Đã xác nhận — hành vi:** Gửi OTP SET_PASSWORD cho email của chính mình; không nhận email tài khoản khác. Đã có password thì UC07. Xem [UC09](../use-cases.md#uc09-thiết-lập-mật-khẩu-lần-đầu).

**Bổ sung đã xác nhận ngày 2026-10-31:** gửi SMTP đồng bộ sau commit OTP. SMTP lỗi/timeout trả 503 SERVICE_UNAVAILABLE; lần cấp mã đã commit vẫn tính giới hạn gửi, không phục hồi mã cũ hoặc tự cấp mã retry. Chi tiết tại [Q06](../confirmed-decisions.md#otp-email-delivery).

**PENDING DECISION:** [Q06](../open-decisions.md#q06), [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 401, 409, 429, 500, 503`.

**Request không có body.**

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I16):

```json
{
  "success": true,
  "messageCode": "SET_PASSWORD_CODE_SENT",
  "message": "Mã xác minh đã được gửi đến email",
  "data": {
    "expiresIn": 600
  }
}
```

**Response lỗi minh họa — HTTP 409 và messageCode đã xác nhận** (đối chiếu ô J16, message minh họa):

```json
{
  "success": false,
  "messageCode": "PASSWORD_ALREADY_CONFIGURED",
  "message": "Tài khoản đã có mật khẩu. Vui lòng sử dụng chức năng đổi mật khẩu.",
  "data": null
}
```

<a id="password-set"></a>

## POST /api/v1/users/me/password/set

**Luồng:** UC09. **Nguồn:** [Sheet1, hàng 17](source-catalog.md#post-apiv1usersmepasswordset).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE, phiên hợp lệ, chưa có password và OTP SET_PASSWORD đúng email/purpose.

**Đã xác nhận — hành vi:** Theo [Q13 chốt ngày 2026-10-25](../confirmed-decisions.md#password-otp-flow), gửi OTP, mật khẩu mới và xác nhận trong cùng request hoàn tất có xác thực; email lấy từ tài khoản đang xác thực. Server xác minh OTP và kiểm tra mật khẩu trước khi lưu, không có API xác minh riêng hoặc ticket. Đặt password, dùng mã một lần và thu hồi mọi phiên/refresh token, kể cả phiên gọi, cùng transaction; giữ identity Google. Client xóa token, đăng nhập lại bằng Google hoặc Local. Xem [UC09](../use-cases.md#uc09-thiết-lập-mật-khẩu-lần-đầu).

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 409, 500, 503`.

**Request minh họa** (đối chiếu ô H17):

```json
{
  "verificationCode": "583921",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I17):

```json
{
  "success": true,
  "messageCode": "PASSWORD_SET_SUCCESS",
  "message": "Thiết lập mật khẩu thành công. Vui lòng đăng nhập lại",
  "data": null
}
```

**Response lỗi minh họa — HTTP 409 và messageCode đã xác nhận** (đối chiếu ô J17, message minh họa):

```json
{
  "success": false,
  "messageCode": "PASSWORD_ALREADY_CONFIGURED",
  "message": "Tài khoản đã có mật khẩu. Vui lòng sử dụng chức năng đổi mật khẩu.",
  "data": null
}
```

<a id="internal-user-get"></a>

## GET /api/v1/internal/users/{id}

**Luồng:** Borrow Service. **Nguồn:** [Sheet1, hàng 18](source-catalog.md#get-apiv1internalusersid).

**Xác thực/đầu vào:** Giai đoạn chức năng mặc định tin tưởng service, không yêu cầu Service JWT hoặc kiểm tra quyền users:read. Borrow/Admin vẫn là bên gọi theo thiết kế; chưa có lớp xác thực service để chứng minh bên gọi. Sau khi hoàn tất chức năng, bổ sung Service JWT/quyền users:read và giới hạn caller theo [Q03](../confirmed-decisions.md#internal-service-auth).

**Đã xác nhận — hành vi:** Trả thông tin cơ bản, kể cả user BANNED để service gọi kiểm tra nghiệp vụ. HTTP 200 không bảo đảm đủ điều kiện mượn. id là User ID kiểu Integer theo nguồn; xem [tích hợp Borrow](internal-integration.md#tra-cứu-user).

**PENDING DECISION:** [Q03](../open-decisions.md#q03), [Q09](../open-decisions.md#q09). Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi đã chốt](http-errors.md); Q09 còn mở cho tình huống/mã/chi tiết lỗi ngoài danh mục; schema data và mã thành công đã chốt.

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 403, 404, 500, 503`.

**Request không có body.**

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I18):

```json
{
  "success": true,
  "messageCode": "USER_GET_SUCCESS",
  "message": "Lấy thông tin người dùng thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "fullName": "Nguyen Van A",
    "status": "ACTIVE",
    "roles": [
      "READER"
    ]
  }
}
```

<a id="internal-user-status"></a>

## PATCH /api/v1/internal/users/{id}/status

**Luồng:** Admin Service. **Nguồn:** [Sheet1, hàng 19](source-catalog.md#patch-apiv1internalusersidstatus).

**Xác thực/đầu vào:** Giai đoạn chức năng mặc định tin tưởng service, chưa yêu cầu Service JWT hoặc kiểm tra quyền users:status:write. Vẫn bắt buộc access token quản trị viên trong X-Admin-Access-Token: User Service xác minh token/phiên, user ACTIVE và role ADMIN hiện tại từ database. Admin Service vẫn điều phối; xác thực danh tính/quyền Admin Service sẽ bổ sung sau khi hoàn tất chức năng theo [quyết định ngày 2026-11-10](../confirmed-decisions.md#deferred-service-auth).

**Đã xác nhận — hành vi:** Chỉ ACTIVE/BANNED. Khóa ghi status và thu hồi mọi phiên/refresh token còn hiệu lực liên quan cùng transaction; mở khóa không phục hồi phiên/token. Introspection từ chối phiên thu hồi ở lần kiểm tra sau commit. Không thêm API kích hoạt/role. id là User ID kiểu Integer theo nguồn; xem [vòng đời tài khoản](../confirmed-decisions.md#account-lifecycle).

**Bổ sung đã xác nhận ngày 2026-10-31:** cùng trạng thái vẫn 200 USER_STATUS_UPDATE_SUCCESS, không ghi đè thời điểm/lý do thu hồi cũ. Lệnh cùng user khóa cùng bản ghi và xử lý tuần tự theo database. Cấm khóa đối tượng có ADMIN, kể cả tự khóa, trả 403 ACCESS_DENIED. Timeout cần Admin đọc lại để đối soát, không tự gửi lại lệnh cũ; audit theo [Q10](../confirmed-decisions.md#admin-status-contract).

**PENDING DECISION:** [Q03](../open-decisions.md#q03) chỉ cấu hình Service JWT, [Q09](../open-decisions.md#q09) chỉ tình huống/mã ngoài danh mục. User JWT cho actor theo Q14 đã chốt; schema và lỗi tích hợp theo [HTTP/lỗi](http-errors.md).

**Đã xác nhận — HTTP đã ánh xạ:** `200 / 400, 401, 403, 404, 500, 503`.

**Request minh họa** (đối chiếu ô H19):

```json
{
  "status": "BANNED"
}
```

**Response thành công minh họa — schema và messageCode đã xác nhận; message minh họa** (đối chiếu ô I19):

```json
{
  "success": true,
  "messageCode": "USER_STATUS_UPDATE_SUCCESS",
  "message": "Cập nhật trạng thái tài khoản thành công",
  "data": {
    "id": 3,
    "status": "BANNED"
  }
}
```
