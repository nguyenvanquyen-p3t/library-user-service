# Bản đồ 20 API thiết kế

Tất cả operation dưới đây **chưa triển khai**. 12 use case không tương ứng một-một với 20 endpoint; hai endpoint nội bộ phục vụ service khác và introspection phục vụ Gateway.

| # | Method và đường dẫn | Luồng | Danh tính/điều kiện đầu vào |
| --- | --- | --- | --- |
| 1 | [POST /api/v1/auth/register/request-code](current-contract.md#register-request-code) | UC01 | Công khai, username/email |
| 2 | [POST /api/v1/auth/register](current-contract.md#register) | UC01 | Công khai, OTP REGISTER và dữ liệu đăng ký |
| 3 | [POST /api/v1/auth/login](current-contract.md#login) | UC02 | Username/email và mật khẩu |
| 4 | [POST /api/v1/auth/refresh](current-contract.md#refresh) | UC03 | Refresh token, không yêu cầu access token còn hạn |
| 5 | [POST /api/v1/auth/logout](current-contract.md#logout) | UC04 | Refresh token kể cả token cũ còn xác định được phiên |
| 6 | [POST /api/v1/auth/forgot-password/request-code](current-contract.md#forgot-password-request-code) | UC05 | Công khai, email |
| 7 | [POST /api/v1/auth/reset-password](current-contract.md#reset-password) | UC05 | OTP PASSWORD_RESET và mật khẩu mới |
| 8 | [GET /api/v1/users/me](current-contract.md#profile-get) | UC06 | Access token, user ACTIVE, phiên hợp lệ |
| 9 | [PATCH /api/v1/users/me](current-contract.md#profile-update) | UC06 | Như trên; chỉ fullName/phoneNumber |
| 10 | [PUT /api/v1/users/me/password](current-contract.md#password-change) | UC07 | Phiên hợp lệ và mật khẩu hiện tại |
| 11 | [POST /api/v1/auth/google](current-contract.md#google) | UC08 | Google ID Token được server xác minh |
| 12 | [POST /api/v1/auth/google/register](current-contract.md#google-register) | UC08 | registrationToken hợp lệ và dữ liệu hoàn tất |
| 13 | [POST /api/v1/users/me/password/request-code](current-contract.md#password-request-code) | UC09 | Phiên hợp lệ, user chưa có mật khẩu |
| 14 | [POST /api/v1/users/me/password/set](current-contract.md#password-set) | UC09 | Phiên hợp lệ, OTP SET_PASSWORD, mật khẩu mới |
| 15 | [GET /api/v1/internal/users/{id}](current-contract.md#internal-user-get) | Borrow Service | Xác thực và quyền service: còn chờ hợp đồng |
| 16 | [PATCH /api/v1/internal/users/{id}/status](current-contract.md#internal-user-status) | Admin Service | Xác thực service/actor và quyền: Q03 |
| 17 | [GET /api/v1/users/me/sessions](sessions.md#session-list) | UC10 | Phiên hợp lệ, chỉ dữ liệu của mình |
| 18 | [DELETE /api/v1/users/me/sessions/{sessionId}](sessions.md#session-revoke) | UC11 | Phiên hợp lệ; phiên đích của mình và khác sid |
| 19 | [POST /api/v1/users/me/sessions/revoke-others](sessions.md#session-revoke-others) | UC12 | Phiên hợp lệ; giữ sid từ token đã xác minh |
| 20 | [POST /api/v1/internal/oauth2/introspect](sessions.md#introspect) | Gateway | Service JWT riêng, user token trong form body |

## Đọc hợp đồng đúng phiên bản

[16 API hiện hành](current-contract.md) là nơi tra cứu request/response của các API gốc theo quyết định mới nhất; [API phiên](sessions.md) duy trì 4 API mới. [Danh mục nguồn](source-catalog.md) giữ nguyên dữ liệu Excel lịch sử, không dùng làm bản hợp đồng cuối cùng. [Tích hợp nội bộ](internal-integration.md) mô tả trách nhiệm/quyền/retry; [use case](../use-cases.md) mô tả tương tác; [yêu cầu](../requirements.md) duy trì transaction.

Bản hiện hành dùng TTL và tác động lên session đã chốt thay ví dụ nguồn cũ. Mỗi operation ghi rõ điều kiện xác thực, quy tắc được xác nhận và phần còn chờ quyết định. Refresh/logout không đòi access token còn hạn; UC11 không cho chọn phiên hiện tại. Introspection dùng form URL encoded và JSON RFC 7662, không bọc envelope.

Các trường required/null, validation, response lỗi và hình dạng JSON chưa thống nhất vẫn thuộc [Q09](../open-decisions.md#q09). HTTP status/messageCode trong ví dụ nguồn và đề xuất của API phiên chưa phải tất cả đã được xác nhận. [Q13](../open-decisions.md#q13) theo dõi cách chia bước OTP UC05/UC09; không có verify-code endpoint riêng trong 20 API. Chưa tạo OpenAPI hoặc triển khai endpoint trong đợt tối ưu tài liệu.
