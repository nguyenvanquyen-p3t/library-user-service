# Thiết kế hiện hành của 16 API gốc

Cập nhật tổng hợp: 2026-10-24. **Thiết kế, chưa triển khai API.** “Hiện hành” nghĩa là ví dụ nguồn đã được đối chiếu với quyết định mới nhất, không phải hợp đồng hoàn thiện hoặc hành vi đang chạy. [Bản đồ 20 API](README.md) liên kết thêm [4 API phiên/introspection](sessions.md).

## Cách đọc trạng thái

- **Đã xác nhận:** quy tắc do chủ dự án chốt, dẫn tới sổ quyết định hoặc luồng đã bàn giao; chưa đồng nghĩa có code.
- **Theo nguồn/chưa xác nhận:** tên trường, HTTP status, messageCode và hình dạng JSON từ Excel chưa được xác nhận đầy đủ tại Q09. Ví dụ không tự xác định required/null hoặc validation.
- **Đề xuất:** phương án còn cần thống nhất; đề xuất của API phiên được giữ ở tài liệu riêng, không chuyển thành quyết định tại đây.
- **PENDING DECISION:** câu hỏi có ID trong [sổ quyết định còn mở](../open-decisions.md); cần chốt phần liên quan trước triển khai/tích hợp.

Request có body của 16 API bên dưới dùng JSON theo thiết kế nguồn; API không có body được ghi riêng. Introspection dùng form URL encoded và response RFC 7662 không bọc envelope, xem API phiên. Response bên dưới giữ envelope nguồn, chưa chốt toàn bộ schema/lỗi tại [Q09](../open-decisions.md#q09).

Mọi dữ liệu là minh họa. Ví dụ cấp access token dùng expiresIn 3600 theo [TTL đã chốt](../confirmed-decisions.md#user-tokens); OTP 600 giây. sid nằm trong JWT và được kiểm tra cùng phiên; không tự thêm trường JSON ngoài hợp đồng nguồn. Refresh token tiếp tục qua JSON cho Web/Android. Các token mẫu không sử dụng được.

Request người dùng được bảo vệ lấy danh tính từ JWT đã xác minh, user ACTIVE và phiên đúng ownership/còn hiệu lực. JWT còn hạn không đủ khi phiên bị thu hồi/hết hạn. Refresh/logout và hai API nội bộ có điều kiện xác thực riêng tại từng operation. Chính sách duy trì tại [sổ quyết định](../confirmed-decisions.md); thay đổi dữ liệu tuân thủ [transaction và cạnh tranh](../requirements.md#ranh-giới-transaction).

Không chỉnh dữ liệu trong [danh mục nguồn](source-catalog.md) để giống bản hiện hành. Khi có quyết định mới, cập nhật bản này theo [quy ước bảo trì](../documentation.md#quy-tắc-bảo-trì).

<a id="register-request-code"></a>

## POST /api/v1/auth/register/request-code

**Luồng:** UC01. **Nguồn:** [Sheet1, hàng 2](source-catalog.md#post-apiv1authregisterrequest-code).

**Xác thực/đầu vào:** Công khai; không yêu cầu access token.

**Đã xác nhận — hành vi:** Kiểm tra username/email chưa được sử dụng, cấp OTP REGISTER trước khi có user. Chưa lưu password/dữ liệu đăng ký tạm. OTP gắn email/purpose, không gắn username; xem [OTP REGISTER](../confirmed-decisions.md#register-otp-binding).

**PENDING DECISION:** [Q06](../open-decisions.md#q06), [Q09](../open-decisions.md#q09). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 409, 429, 500`.

**Request minh họa** (đối chiếu ô H2):

```json
{
  "userName": "reader01",
  "email": "reader@example.com"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I2):

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

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `201 / 400, 409, 422`.

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

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I3):

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

**Luồng:** UC02. **Nguồn:** [Sheet1, hàng 4](source-catalog.md#post-apiv1authlogin).

**Xác thực/đầu vào:** Username/email và mật khẩu; không yêu cầu access token.

**Đã xác nhận — hành vi:** User ACTIVE có password đúng mới được đăng nhập. Tạo session/refresh token, JWT RS256 chứa sub/sid; chỉ trả token sau commit. Chưa có password thì Google và UC09. Xem [token](../confirmed-decisions.md#user-tokens) và [TTL](../confirmed-decisions.md#session-lifetime).

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 409`.

**Request minh họa** (đối chiếu ô H4):

```json
{
  "identifier": "reader@example.com",
  "password": "Abc@123456"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I4):

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

**Response lỗi minh họa — theo nguồn/chưa xác nhận** (đối chiếu ô J4):

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

**Luồng:** UC03. **Nguồn:** [Sheet1, hàng 5](source-catalog.md#post-apiv1authrefresh).

**Xác thực/đầu vào:** Refresh token trong JSON; không yêu cầu access token còn hạn.

**Đã xác nhận — hành vi:** Kiểm tra token, user ACTIVE và session còn hiệu lực. Rotation một lần, giữ sid, không gia hạn session. Reuse chỉ bị từ chối, không thu hồi session/token thay thế vì sự kiện này. Session hết hạn thì từ chối dù token còn hạn; xem [reuse](../confirmed-decisions.md#refresh-reuse) và [TTL](../confirmed-decisions.md#session-lifetime).

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403`.

**Request minh họa** (đối chiếu ô H5):

```json
{
  "refreshToken": "d8K3mP7q..."
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I5):

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

**Luồng:** UC04. **Nguồn:** [Sheet1, hàng 6](source-catalog.md#post-apiv1authlogout).

**Xác thực/đầu vào:** Refresh token trong JSON, kể cả token cũ còn xác định được phiên; không yêu cầu access token còn hạn.

**Đã xác nhận — hành vi:** Thu hồi đúng session/token liên quan; session đã hết hạn/thu hồi trả thành công, giữ thời điểm/lý do cũ. Token không tìm thấy chỉ bảo đảm đăng xuất client; database lỗi không báo thu hồi server thành công. Client xóa token. Xem [UC04](../confirmed-decisions.md#logout); LOGOUT_SUCCESS bên dưới không chứng minh server vừa thu hồi được session.

**PENDING DECISION:** [Q09](../open-decisions.md#q09). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400`.

**Request minh họa** (đối chiếu ô H6):

```json
{
  "refreshToken": "d8K3mP7q..."
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I6):

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

**PENDING DECISION:** [Q06](../open-decisions.md#q06), [Q09](../open-decisions.md#q09), [Q13](../open-decisions.md#q13). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 429, 500`.

**Request minh họa** (đối chiếu ô H7):

```json
{
  "email": "reader@example.com"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I7):

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

**Đã xác nhận — hành vi:** Cập nhật PasswordHash, dùng mã một lần và thu hồi mọi phiên/refresh token cùng transaction; không mở khóa BANNED. Chưa có password thì Google/UC09. Xem [UC05](../use-cases.md#uc05-quên-và-đặt-lại-mật-khẩu).

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q13](../open-decisions.md#q13). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 409, 422`.

**Request minh họa** (đối chiếu ô H8):

```json
{
  "email": "reader@example.com",
  "verificationCode": "628351",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I8):

```json
{
  "success": true,
  "messageCode": "PASSWORD_RESET_SUCCESS",
  "message": "Đặt lại mật khẩu thành công",
  "data": null
}
```

**Response lỗi minh họa — theo nguồn/chưa xác nhận** (đối chiếu ô J8):

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

**Luồng:** UC06. **Nguồn:** [Sheet1, hàng 9](source-catalog.md#get-apiv1usersme).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE, phiên đúng user từ sub/sid và còn hiệu lực.

**Đã xác nhận — hành vi:** Chỉ trả hồ sơ người gọi, không nhận userId tùy ý, không trả hash/secret. Chỉ xem không thay đổi hồ sơ. Xem [UC06](../use-cases.md#uc06-xem-và-cập-nhật-hồ-sơ).

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 401, 403, 404`.

**Request không có body.**

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I9):

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

**Luồng:** UC06. **Nguồn:** [Sheet1, hàng 10](source-catalog.md#patch-apiv1usersme).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE và phiên hợp lệ.

**Đã xác nhận — hành vi:** Chỉ sửa fullName/phoneNumber của mình, không sửa roles/status/password/username/email. Trigger cập nhật Users.UpdatedAt khi dữ liệu thực sự đổi; xem [schema](../data-model.md#token-và-thời-gian). Missing/null và trường ngoài danh sách còn Q09.

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 422`.

**Request minh họa** (đối chiếu ô H10):

```json
{
  "fullName": "Nguyen Van B",
  "phoneNumber": "0987654321"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I10):

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

**Luồng:** UC07. **Nguồn:** [Sheet1, hàng 11](source-catalog.md#put-apiv1usersmepassword).

**Xác thực/đầu vào:** `Authorization: Bearer <access-token>`; user ACTIVE, phiên hợp lệ và mật khẩu hiện tại đúng.

**Đã xác nhận — hành vi:** Đổi PasswordHash và thu hồi mọi phiên/refresh token, kể cả phiên gọi, cùng transaction. Giữ identity Google; client xóa token và đăng nhập lại. Chưa có password thì UC09. Xem [UC07](../use-cases.md#uc07-đổi-mật-khẩu).

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 409, 422`.

**Request minh họa** (đối chiếu ô H11):

```json
{
  "currentPassword": "Abc@123456",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I11):

```json
{
  "success": true,
  "messageCode": "PASSWORD_CHANGE_SUCCESS",
  "message": "Đổi mật khẩu thành công. Vui lòng đăng nhập lại",
  "data": null
}
```

**Response lỗi minh họa — theo nguồn/chưa xác nhận** (đối chiếu ô J11):

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

**PENDING DECISION:** [Q07](../open-decisions.md#q07), [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 409, 500`.

**Request minh họa** (đối chiếu ô H12):

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

### Nhánh A — Identity đã liên kết

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I12):

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

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I13):

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

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I14):

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

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `201 / 400, 401, 409, 422`.

**Request minh họa** (đối chiếu ô H15):

```json
{
  "registrationToken": "short-lived-token...",
  "userName": "reader01",
  "fullName": "Nguyen Van A",
  "phoneNumber": "0901234567"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I15):

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

**PENDING DECISION:** [Q06](../open-decisions.md#q06), [Q09](../open-decisions.md#q09), [Q13](../open-decisions.md#q13), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 401, 403, 409, 429, 500`.

**Request không có body.**

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I16):

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

**Response lỗi minh họa — theo nguồn/chưa xác nhận** (đối chiếu ô J16):

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

**Đã xác nhận — hành vi:** Đặt password, dùng mã một lần và thu hồi mọi phiên/refresh token, kể cả phiên gọi, cùng transaction; giữ identity Google. Client xóa token, đăng nhập lại bằng Google hoặc Local. Xem [UC09](../use-cases.md#uc09-thiết-lập-mật-khẩu-lần-đầu).

**PENDING DECISION:** [Q09](../open-decisions.md#q09), [Q13](../open-decisions.md#q13), [Q14](../open-decisions.md#q14). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 409, 422`.

**Request minh họa** (đối chiếu ô H17):

```json
{
  "verificationCode": "583921",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I17):

```json
{
  "success": true,
  "messageCode": "SET_PASSWORD_SUCCESS",
  "message": "Thiết lập mật khẩu thành công. Vui lòng đăng nhập lại",
  "data": null
}
```

**Response lỗi minh họa — theo nguồn/chưa xác nhận** (đối chiếu ô J17):

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

**Xác thực/đầu vào:** Service được xác thực/cấp quyền; client Web/Android không được gọi trực tiếp. Cơ chế cụ thể còn Q03.

**Đã xác nhận — hành vi:** Trả thông tin cơ bản, kể cả user BANNED để service gọi kiểm tra nghiệp vụ. HTTP 200 không bảo đảm đủ điều kiện mượn. id là User ID kiểu Integer theo nguồn; xem [tích hợp Borrow](internal-integration.md#tra-cứu-user).

**PENDING DECISION:** [Q03](../open-decisions.md#q03), [Q09](../open-decisions.md#q09). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 404`.

**Request không có body.**

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I18):

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

**Xác thực/đầu vào:** Chỉ Admin Service được xác thực/cấp quyền; cần kiểm tra quyền thao tác Admin. Cơ chế/actor còn Q03.

**Đã xác nhận — hành vi:** Chỉ ACTIVE/BANNED. Khóa ghi status và thu hồi mọi phiên/refresh token còn hiệu lực liên quan cùng transaction; mở khóa không phục hồi phiên/token. Introspection từ chối phiên thu hồi ở lần kiểm tra sau commit. Không thêm API kích hoạt/role. id là User ID kiểu Integer theo nguồn; xem [vòng đời tài khoản](../confirmed-decisions.md#account-lifecycle).

**PENDING DECISION:** [Q03](../open-decisions.md#q03), [Q09](../open-decisions.md#q09), [Q10](../open-decisions.md#q10). Quy tắc schema/required/null/lỗi của Q09 áp dụng cho operation này; không tự bổ sung validation từ ví dụ.

**Theo nguồn/chưa xác nhận — HTTP thành công/lỗi:** `200 / 400, 401, 403, 404, 422`.

**Request minh họa** (đối chiếu ô H19):

```json
{
  "status": "BANNED"
}
```

**Response thành công minh họa — theo nguồn, áp dụng quyết định hiện hành** (đối chiếu ô I19):

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
