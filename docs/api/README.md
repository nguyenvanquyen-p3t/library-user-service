# Bản đồ 20 API thiết kế

**Login UC02 đã triển khai ngày 2026-11-10; refresh/logout UC03–UC04 ngày 2026-11-11. GET/PATCH hồ sơ UC06 đã triển khai ngày 2026-11-12. Tổng 5 IMPLEMENTED/15 DESIGN_ONLY.** Xem [bảo mật người dùng](../user-security.md). 12 use case không tương ứng một-một với 20 endpoint; hai endpoint nội bộ phục vụ service khác và introspection phục vụ Gateway.

Ngày 2026-11-01 đã thêm [OpenAPI và Swagger UI](openapi.md) để đọc đủ 20 operation này. [Đặc tả YAML](../../backend/src/main/resources/openapi/user-service.yaml) ghi trạng thái từng operation: login/refresh/logout và GET/PATCH hồ sơ UC06 `IMPLEMENTED`, 15 operation khác `DESIGN_ONLY`; UI tắt `Try it out`.

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
| 15 | [GET /api/v1/internal/users/{id}](current-contract.md#internal-user-get) | Borrow/Admin Service | Giai đoạn chức năng tin tưởng service; Service JWT/users:read bổ sung sau |
| 16 | [PATCH /api/v1/internal/users/{id}/status](current-contract.md#internal-user-status) | Admin Service | Giai đoạn chức năng tin tưởng service; vẫn yêu cầu X-Admin-Access-Token và actor ADMIN hợp lệ; Service JWT/users:status:write bổ sung sau |
| 17 | [GET /api/v1/users/me/sessions](sessions.md#session-list) | UC10 | Phiên hợp lệ, chỉ dữ liệu của mình |
| 18 | [DELETE /api/v1/users/me/sessions/{sessionId}](sessions.md#session-revoke) | UC11 | Phiên hợp lệ; phiên đích của mình và khác sid |
| 19 | [POST /api/v1/users/me/sessions/revoke-others](sessions.md#session-revoke-others) | UC12 | Phiên hợp lệ; giữ sid từ token đã xác minh |
| 20 | [POST /api/v1/internal/oauth2/introspect](sessions.md#introspect) | Gateway | Giai đoạn chức năng tin tưởng service; user token vẫn trong form body; Service JWT/tokens:introspect bổ sung sau |

## Đọc hợp đồng đúng phiên bản

Theo [quyết định ngày 2026-11-10](../confirmed-decisions.md#deferred-service-auth), giai đoạn code chức năng mặc định tin tưởng service; chỉ bổ sung xác thực/quyền service sau khi hoàn tất tất cả chức năng. Các yêu cầu Service JWT trong thiết kế/OpenAPI là hợp đồng đích cho bước sau, không chặn triển khai ba operation nội bộ. Xác thực user/phiên/actor vẫn áp dụng.

[16 API hiện hành](current-contract.md) là nơi tra cứu request/response của các API gốc theo quyết định mới nhất; [API phiên](sessions.md) duy trì 4 API mới. [Danh mục nguồn](source-catalog.md) giữ nguyên dữ liệu Excel lịch sử, không dùng làm bản hợp đồng cuối cùng. [Tích hợp nội bộ](internal-integration.md) mô tả trách nhiệm/quyền/retry; [use case](../use-cases.md) mô tả tương tác; [yêu cầu](../requirements.md) duy trì transaction.

Bản hiện hành dùng TTL và tác động lên session đã chốt thay ví dụ nguồn cũ. Mỗi operation ghi rõ điều kiện xác thực, quy tắc được xác nhận và phần còn chờ quyết định. Refresh/logout không đòi access token còn hạn; UC11 không cho chọn phiên hiện tại. Introspection dùng form URL encoded và JSON RFC 7662, không bọc envelope.

[User access token Q14 đã chốt ngày 2026-10-31](../confirmed-decisions.md#access-token-key-pair): issuer chung urn:library:user-service, aud ["library-api"], kid user-access-v1; cặp khóa cố định trong backend/.secrets/, phân phối public key qua file/cấu hình và thay khóa có kiểm soát. Cấu hình Service JWT Q03/Q11a vẫn mở; không áp các giá trị Q14 sang Service JWT.

[Schema JSON](json-schema.md) và [HTTP/lỗi](http-errors.md) chốt ngày 2026-10-25 duy trì envelope, required/null request/response của 16 API gốc, biến thể Google, mở rộng response, validation, PATCH, status/messageCode, data.errors và Retry-After. Ngày 2026-10-26 chốt [ba API phiên](../confirmed-decisions.md#session-response-contract), [danh mục mã phản hồi](../confirmed-decisions.md#response-code-contract) và [introspection](../confirmed-decisions.md#introspection-contract). Cả 20 API đã có schema request/response xác nhận: 19 API dùng envelope/mã thành công, introspection dùng JSON riêng với roles từ database và bộ lỗi/header đã chốt. Ngày 2026-10-31 chốt [Service JWT/quyền và actor Admin](../confirmed-decisions.md#internal-service-auth), [hành vi SMTP](../confirmed-decisions.md#otp-email-delivery), [quản trị trạng thái](../confirmed-decisions.md#admin-status-contract) và [lỗi tích hợp](../confirmed-decisions.md#integration-error-contract). Cấu hình JWT, lựa chọn/cấu hình môi trường SMTP và tình huống/mã ngoài danh mục vẫn thuộc [quyết định còn mở](../open-decisions.md). [Q13 đã chốt ngày 2026-10-25](../confirmed-decisions.md#password-otp-flow): UC05/UC09 gửi OTP cùng mật khẩu mới/xác nhận trong request hoàn tất; không có verify-code endpoint riêng trong 20 API. Đã có [OpenAPI/Swagger UI](openapi.md) cho hợp đồng; login/refresh/logout UC02–UC04 và hồ sơ UC06 đã triển khai, 15 operation khác còn thiết kế.
