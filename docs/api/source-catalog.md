# Danh mục API từ tài liệu nguồn

**Đọc cùng [bản đồ API hiện hành](README.md).** Đây là dữ liệu nguồn lịch sử, không phải hợp đồng cuối cùng: access token hiện đã chốt 3600 giây thay cho 1800 trong ví dụ; logout và các luồng mật khẩu đã bổ sung session revocation. Khi khác nhau, áp dụng [quyết định đã xác nhận](../confirmed-decisions.md), [yêu cầu](../requirements.md) và [API phiên](sessions.md). Không cần file Excel để đọc các request/response đã trích bên dưới.

Trạng thái: **thiết kế nguồn, chưa triển khai**. Trích từ `ThietKeApi.xlsx`, `Sheet1`, hàng 2–19; 16 operation, trong đó Google có ba nhánh response. Ví dụ được giữ từ Excel, không suy ra trường bắt buộc, nullable hoặc giới hạn validation chỉ từ ví dụ. Các giá trị token là dữ liệu minh họa.

Các ô lỗi để trống không có nghĩa API không thể lỗi. Xác thực service-to-service là **PENDING DECISION**; “Service Token” trong Excel chưa xác định Service JWT, API Key hay cơ chế khác. API quản lý role **ngoài phạm vi V1**. Xem [tích hợp nội bộ](internal-integration.md) và [quyết định còn mở](../open-decisions.md). Response nguồn dùng envelope `success`, `messageCode`, `message`, `data`; chưa phải chuẩn lỗi đã hoàn thiện.

Danh mục này lưu 16 API gốc từ Excel. Phạm vi 2026-10-19 mở rộng thành 7 bảng/20 API: thêm [4 API phiên/introspection](sessions.md) ở mức thiết kế; hiện chỉ thêm migration và docs. Gateway dùng Service JWT; xác thực Admin Service còn chờ chốt. Web/Android giữ refresh token trong JSON. Các trích dẫn bên dưới được giữ nguyên từ Excel.

## POST /api/v1/auth/register/request-code

UC01 - Gửi mã xác minh đăng ký. Nguồn: Sheet1, hàng 2.

Gửi mã xác minh đến Email trước khi tạo tài khoản

HTTP thành công / lỗi theo nguồn: `200 / 400, 409, 429, 500`.

Message code thành công: `REGISTER_CODE_SENT`.

**Request** — ô H2

```json
{
  "userName": "reader01",
  "email": "reader@gmail.com"
}
```

**Response thành công** — ô I2

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

## POST /api/v1/auth/register

UC01 - Đăng ký tài khoản Độc giả. Nguồn: Sheet1, hàng 3.

Kiểm tra mã xác minh, tạo User mới và gán Role READER

HTTP thành công / lỗi theo nguồn: `201 / 400, 409, 422`.

Message code thành công: `REGISTER_SUCCESS`.

**Request** — ô H3

```json
{
  "userName": "reader01",
  "email": "reader@gmail.com",
  "password": "Abc@123456",
  "fullName": "Nguyen Van A",
  "phoneNumber": "0901234567",
  "verificationCode": "583921"
}
```

**Response thành công** — ô I3

```json
{
  "success": true,
  "messageCode": "REGISTER_SUCCESS",
  "message": "Đăng ký tài khoản thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "email": "reader@gmail.com",
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

## POST /api/v1/auth/login

UC02 - Đăng nhập. Nguồn: Sheet1, hàng 4.

Xác thực tài khoản và cấp Access Token + Refresh Token

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 409`.

Message code thành công: `LOGIN_SUCCESS`.

**Request** — ô H4

```json
{
  "identifier": "reader@gmail.com",
  "password": "Abc@123456"
}
```

**Response thành công** — ô I4

```json
{
  "success": true,
  "messageCode": "LOGIN_SUCCESS",
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8K3mP7q...",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

**Response lỗi mẫu** — ô J4

```json
{
  "success": false,
  "messageCode": "PASSWORD_NOT_CONFIGURED",
  "message": "Tài khoản chưa thiết lập mật khẩu. Vui lòng đăng nhập bằng Google hoặc thiết lập mật khẩu.",
  "data": null
}
```

## POST /api/v1/auth/refresh

UC03 - Làm mới Access Token. Nguồn: Sheet1, hàng 5.

Dùng Refresh Token hiện tại để cấp Access Token và Refresh Token mới theo cơ chế Rotation

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403`.

Message code thành công: `TOKEN_REFRESH_SUCCESS`.

**Request** — ô H5

```json
{
  "refreshToken": "d8K3mP7q..."
}
```

**Response thành công** — ô I5

```json
{
  "success": true,
  "messageCode": "TOKEN_REFRESH_SUCCESS",
  "message": "Làm mới token thành công",
  "data": {
    "accessToken": "new-access-token...",
    "refreshToken": "new-refresh-token...",
    "tokenType": "Bearer",
    "expiresIn": 1800
  }
}
```

## POST /api/v1/auth/logout

UC04 - Đăng xuất. Nguồn: Sheet1, hàng 6.

Thu hồi Refresh Token của phiên đăng nhập hiện tại

HTTP thành công / lỗi theo nguồn: `200 / 400`.

Message code thành công: `LOGOUT_SUCCESS`.

**Request** — ô H6

```json
{
  "refreshToken": "d8K3mP7q..."
}
```

**Response thành công** — ô I6

```json
{
  "success": true,
  "messageCode": "LOGOUT_SUCCESS",
  "message": "Đăng xuất thành công",
  "data": null
}
```

## POST /api/v1/auth/forgot-password/request-code

UC05 - Gửi mã quên mật khẩu. Nguồn: Sheet1, hàng 7.

Gửi mã xác minh Email để đặt lại mật khẩu

HTTP thành công / lỗi theo nguồn: `200 / 400, 429, 500`.

Message code thành công: `PASSWORD_RESET_CODE_SENT`.

**Request** — ô H7

```json
{
  "email": "reader@gmail.com"
}
```

**Response thành công** — ô I7

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

## POST /api/v1/auth/reset-password

UC05 - Đặt lại mật khẩu. Nguồn: Sheet1, hàng 8.

Xác minh code, cập nhật mật khẩu mới và thu hồi các Refresh Token hiện tại của người dùng.

HTTP thành công / lỗi theo nguồn: `200 / 400, 409, 422`.

Message code thành công: `PASSWORD_RESET_SUCCESS`.

**Request** — ô H8

```json
{
  "email": "reader@gmail.com",
  "verificationCode": "628351",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công** — ô I8

```json
{
  "success": true,
  "messageCode": "PASSWORD_RESET_SUCCESS",
  "message": "Đặt lại mật khẩu thành công",
  "data": null
}
```

**Response lỗi mẫu** — ô J8

```json
{
  "success": false,
  "messageCode": "PASSWORD_NOT_CONFIGURED",
  "message": "Tài khoản chưa thiết lập mật khẩu. Vui lòng đăng nhập bằng Google và sử dụng chức năng thiết lập mật khẩu.",
  "data": null
}
```

## GET /api/v1/users/me

UC06 - Xem thông tin cá nhân. Nguồn: Sheet1, hàng 9.

Lấy thông tin tài khoản hiện tại từ Access Token

HTTP thành công / lỗi theo nguồn: `200 / 401, 403, 404`.

Message code thành công: `PROFILE_GET_SUCCESS`.

**Request** — ô H9

Không có

**Response thành công** — ô I9

```json
{
  "success": true,
  "messageCode": "PROFILE_GET_SUCCESS",
  "message": "Lấy thông tin cá nhân thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "email": "reader@gmail.com",
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

Ghi chú nguồn: Yêu cầu Bearer Access Token

## PATCH /api/v1/users/me

UC06 - Cập nhật thông tin cá nhân. Nguồn: Sheet1, hàng 10.

Cập nhật các thông tin cá nhân được phép thay đổi

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 422`.

Message code thành công: `PROFILE_UPDATE_SUCCESS`.

**Request** — ô H10

```json
{
  "fullName": "Nguyen Van B",
  "phoneNumber": "0987654321"
}
```

**Response thành công** — ô I10

```json
{
  "success": true,
  "messageCode": "PROFILE_UPDATE_SUCCESS",
  "message": "Cập nhật thông tin cá nhân thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "email": "reader@gmail.com",
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

Ghi chú nguồn: Yêu cầu Bearer Access Token

## PUT /api/v1/users/me/password

UC07 - Đổi mật khẩu. Nguồn: Sheet1, hàng 11.

Xác minh mật khẩu hiện tại, cập nhật mật khẩu mới cho tài khoản đã có Password, sau đó thu hồi các Refresh Token hiện còn hiệu lực và yêu cầu người dùng đăng nhập lại.

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 409, 422`.

Message code thành công: `PASSWORD_CHANGE_SUCCESS`.

**Request** — ô H11

```json
{
  "currentPassword": "Abc@123456",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công** — ô I11

```json
{
  "success": true,
  "messageCode": "PASSWORD_CHANGE_SUCCESS",
  "message": "Đổi mật khẩu thành công. Vui lòng đăng nhập lại",
  "data": null
}
```

**Response lỗi mẫu** — ô J11

```json
{
  "success": false,
  "messageCode": "PASSWORD_NOT_CONFIGURED",
  "message": "Tài khoản chưa có mật khẩu. Vui lòng sử dụng chức năng thiết lập mật khẩu.",
  "data": null
}
```

Ghi chú nguồn: Yêu cầu Bearer Access Token

## POST /api/v1/auth/google

UC08 - Đăng nhập bằng Google. Nguồn: Sheet1, hàng 12.

Xác thực Google Account; đăng nhập nếu đã liên kết, liên kết với Local Account cùng Email đã được Google xác minh nếu có, hoặc yêu cầu hoàn tất đăng ký nếu là người dùng mới.

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 409, 500`.

Message code thành công: `GOOGLE_LOGIN_SUCCESS`.

**Request** — ô H12

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

**Response thành công** — ô I12

```json
{
  "success": true,
  "messageCode": "GOOGLE_LOGIN_SUCCESS",
  "message": "Đăng nhập bằng Google thành công",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8K3mP7q...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "isNewUser": false,
    "accountLinked": false
  }
}
```

Ghi chú nguồn: Trường hợp A — Google đã được liên kết

### Nhánh Google bổ sung — hàng 13

Message code thành công: `GOOGLE_ACCOUNT_LINKED`.

**Response thành công** — ô I13

```json
{
  "success": true,
  "messageCode": "GOOGLE_ACCOUNT_LINKED",
  "message": "Tài khoản Google đã được liên kết và đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "d8K3mP7q...",
    "tokenType": "Bearer",
    "expiresIn": 1800,
    "isNewUser": false,
    "accountLinked": true
  }
}
```

Ghi chú nguồn: Trường hợp B — chưa có UserIdentity nhưng Email đã có Local Account

### Nhánh Google bổ sung — hàng 14

Message code thành công: `GOOGLE_REGISTRATION_REQUIRED`.

**Response thành công** — ô I14

```json
{
  "success": true,
  "messageCode": "GOOGLE_REGISTRATION_REQUIRED",
  "message": "Vui lòng hoàn tất thông tin tài khoản",
  "data": {
    "email": "reader@gmail.com",
    "fullName": "Nguyen Van A",
    "suggestedUserName": "nguyenvana",
    "registrationToken": "short-lived-token...",
    "isNewUser": true,
    "accountLinked": false
  }
}
```

Ghi chú nguồn: Trường hợp C — Google hoàn toàn mới

## POST /api/v1/auth/google/register

UC08 - Hoàn tất đăng ký bằng Google. Nguồn: Sheet1, hàng 15.

Hoàn tất đăng ký Google, tạo tài khoản Google-only, liên kết Google, gán READER và cấp Token.

HTTP thành công / lỗi theo nguồn: `201 / 400, 401, 409, 422`.

Message code thành công: `GOOGLE_REGISTER_SUCCESS`.

**Request** — ô H15

```json
{
  "registrationToken": "short-lived-token...",
  "userName": "reader01",
  "fullName": "Nguyen Van A",
  "phoneNumber": "0901234567"
}
```

**Response thành công** — ô I15

```json
{
  "success": true,
  "messageCode": "GOOGLE_REGISTER_SUCCESS",
  "message": "Đăng ký bằng Google thành công",
  "data": {
    "user": {
      "id": 10,
      "userName": "reader01",
      "email": "reader@gmail.com",
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
      "expiresIn": 1800
    }
  }
}
```

## POST /api/v1/users/me/password/request-code

UC09 - Gửi mã xác minh thiết lập mật khẩu. Nguồn: Sheet1, hàng 16.

Gửi mã xác minh Email để thiết lập mật khẩu lần đầu cho tài khoản chưa có Password.

HTTP thành công / lỗi theo nguồn: `200 / 401, 403, 409, 429, 500`.

Message code thành công: `SET_PASSWORD_CODE_SENT`.

**Request** — ô H16

Không có

**Response thành công** — ô I16

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

**Response lỗi mẫu** — ô J16

```json
{
  "success": false,
  "messageCode": "PASSWORD_ALREADY_CONFIGURED",
  "message": "Tài khoản đã có mật khẩu. Vui lòng sử dụng chức năng đổi mật khẩu.",
  "data": null
}
```

Ghi chú nguồn: Yêu cầu Bearer Access Token

## POST /api/v1/users/me/password/set

UC09 - Thiết lập mật khẩu. Nguồn: Sheet1, hàng 17.

Xác minh mã SET_PASSWORD, thiết lập mật khẩu lần đầu cho tài khoản chưa có Password, cập nhật Users.PasswordHash, thu hồi các Refresh Token hiện còn hiệu lực và yêu cầu người dùng đăng nhập lại.

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 409, 422`.

Message code thành công: `SET_PASSWORD_SUCCESS`.

**Request** — ô H17

```json
{
  "verificationCode": "583921",
  "newPassword": "New@123456",
  "confirmPassword": "New@123456"
}
```

**Response thành công** — ô I17

```json
{
  "success": true,
  "messageCode": "SET_PASSWORD_SUCCESS",
  "message": "Thiết lập mật khẩu thành công. Vui lòng đăng nhập lại",
  "data": null
}
```

**Response lỗi mẫu** — ô J17

```json
{
  "success": false,
  "messageCode": "PASSWORD_ALREADY_CONFIGURED",
  "message": "Tài khoản đã có mật khẩu. Vui lòng sử dụng chức năng đổi mật khẩu.",
  "data": null
}
```

Ghi chú nguồn: Yêu cầu Bearer Access Token

## GET /api/v1/internal/users/{id}

Lấy thông tin User theo ID. Nguồn: Sheet1, hàng 18.

Trả về thông tin cơ bản của User theo ID để Borrow Service xác minh tài khoản người mượn, bao gồm trạng thái và vai trò của tài khoản.

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 404`.

Message code thành công: `USER_GET_SUCCESS`.

**Request** — ô H18

Không có Request Body.
Path Variable: id (Integer - User ID)

**Response thành công** — ô I18

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

Ghi chú nguồn: API nội bộ dành cho Borrow Service và các service được cấp quyền. Yêu cầu xác thực Service Token. Không cho phép client Web/Android gọi trực tiếp. Trả về User có Status = BANNED để service gọi tự kiểm tra điều kiện nghiệp vụ.

## PATCH /api/v1/internal/users/{id}/status

Cập nhật trạng thái tài khoản. Nguồn: Sheet1, hàng 19.

Cho phép Admin Service yêu cầu User Service thay đổi trạng thái tài khoản thành ACTIVE hoặc BANNED. Khi khóa tài khoản, hệ thống thu hồi các Refresh Token hiện còn hiệu lực của User.

HTTP thành công / lỗi theo nguồn: `200 / 400, 401, 403, 404, 422`.

Message code thành công: `USER_STATUS_UPDATE_SUCCESS`.

**Request** — ô H19

```json
{
  "status": "BANNED"
}
```

**Response thành công** — ô I19

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

Ghi chú nguồn: API nội bộ chỉ dành cho Admin Service được cấp quyền. Yêu cầu xác thực Service Token và kiểm tra quyền thao tác Admin. Chỉ chấp nhận Status = ACTIVE hoặc BANNED. Khi chuyển sang BANNED, thu hồi toàn bộ Refresh Token còn hiệu lực. Khi chuyển sang ACTIVE, không khôi phục các token đã thu hồi.

