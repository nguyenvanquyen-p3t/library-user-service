# Bản đồ 20 API thiết kế

Tất cả operation dưới đây **chưa triển khai**. 12 use case không tương ứng một-một với 20 endpoint; hai endpoint nội bộ phục vụ service khác và introspection phục vụ Gateway.

| # | Method và đường dẫn | Luồng | Danh tính/điều kiện đầu vào |
| --- | --- | --- | --- |
| 1 | POST /api/v1/auth/register/request-code | UC01 | Công khai, username/email |
| 2 | POST /api/v1/auth/register | UC01 | Công khai, OTP REGISTER và dữ liệu đăng ký |
| 3 | POST /api/v1/auth/login | UC02 | Username/email và mật khẩu |
| 4 | POST /api/v1/auth/refresh | UC03 | Refresh token, không yêu cầu access token còn hạn |
| 5 | POST /api/v1/auth/logout | UC04 | Refresh token kể cả token cũ còn xác định được phiên |
| 6 | POST /api/v1/auth/forgot-password/request-code | UC05 | Công khai, email |
| 7 | POST /api/v1/auth/reset-password | UC05 | OTP PASSWORD_RESET và mật khẩu mới |
| 8 | GET /api/v1/users/me | UC06 | Access token, user ACTIVE, phiên hợp lệ |
| 9 | PATCH /api/v1/users/me | UC06 | Như trên; chỉ fullName/phoneNumber |
| 10 | PUT /api/v1/users/me/password | UC07 | Phiên hợp lệ và mật khẩu hiện tại |
| 11 | POST /api/v1/auth/google | UC08 | Google ID Token được server xác minh |
| 12 | POST /api/v1/auth/google/register | UC08 | registrationToken hợp lệ và dữ liệu hoàn tất |
| 13 | POST /api/v1/users/me/password/request-code | UC09 | Phiên hợp lệ, user chưa có mật khẩu |
| 14 | POST /api/v1/users/me/password/set | UC09 | Phiên hợp lệ, OTP SET_PASSWORD, mật khẩu mới |
| 15 | GET /api/v1/internal/users/{id} | Borrow Service | Xác thực và quyền service: còn chờ hợp đồng |
| 16 | PATCH /api/v1/internal/users/{id}/status | Admin Service | Xác thực service/actor và quyền: Q03 |
| 17 | GET /api/v1/users/me/sessions | UC10 | Phiên hợp lệ, chỉ dữ liệu của mình |
| 18 | DELETE /api/v1/users/me/sessions/{sessionId} | UC11 | Phiên hợp lệ; phiên đích của mình và khác sid |
| 19 | POST /api/v1/users/me/sessions/revoke-others | UC12 | Phiên hợp lệ; giữ sid từ token đã xác minh |
| 20 | POST /api/v1/internal/oauth2/introspect | Gateway | Service JWT riêng, user token trong form body |

## Đọc hợp đồng đúng phiên bản

[Danh mục nguồn](source-catalog.md) giữ request/response của 16 API Excel để không mất dữ liệu thiết kế. [API phiên](sessions.md) mô tả 4 API mới; [tích hợp nội bộ](internal-integration.md) mô tả Admin/Borrow. [Use case](../use-cases.md) là chuỗi tương tác, [requirements](../requirements.md) và [quyết định](../confirmed-decisions.md) bổ sung quy tắc hiện hành.

Các khác biệt đã được quyết định thay thế ví dụ nguồn:

- `expiresIn: 1800` trong ví dụ access token cũ phải thành `3600` khi triển khai; refresh 7 ngày, session 30 ngày cố định.
- Login/Google login/hoàn tất Google tạo session và token có sid. Local register không tự đăng nhập. Refresh rotation cùng sid.
- Logout dùng refresh token theo UC04, không phụ thuộc access token còn hiệu lực. Không sao chép yêu cầu Authorization cũ thành điều kiện chặn logout này.
- Đổi/reset/thiết lập mật khẩu và khóa user thu hồi cả session cùng refresh token liên quan. Introspection từ chối access token của session mất hiệu lực.
- UC11 không cho thu hồi phiên hiện tại. Response, messageCode và phân trang của API phiên còn là đề xuất, không phải mọi giá trị đều đã chốt.
- Introspection dùng form URL encoded và JSON RFC 7662 không bọc envelope. Không áp dụng envelope nguồn cho operation này.

Các trường required/null, validation, response lỗi và cấu trúc response chưa thống nhất phải giải quyết trong Q09 trước khi coi đây là OpenAPI hoàn chỉnh. Q13 theo dõi cách chia màn hình OTP UC05/UC09; hiện không có endpoint verify-code riêng trong 20 API. Không tự thêm endpoint để làm khớp màn hình.
