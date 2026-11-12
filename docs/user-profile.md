# Hồ sơ cá nhân UC06

GET/PATCH hồ sơ triển khai ngày 2026-11-12 theo kế hoạch chủ dự án duyệt. Cùng login/refresh/logout, tổng **5 IMPLEMENTED/15 DESIGN_ONLY**; giữ nguyên 7 bảng và Flyway V1–V6. Hợp đồng HTTP tại [GET](api/current-contract.md#profile-get), [PATCH](api/current-contract.md#profile-update), [schema JSON](api/json-schema.md#profile-patch); quyết định tại [UC06](confirmed-decisions.md#user-profile-uc06).

## Thành phần và xác thực

`user.web.UserProfileController` nhận `UserPrincipal` từ `@AuthenticationPrincipal`; `user.service.UserProfileService` xử lý dữ liệu/transaction. Controller `@Hidden` giữ YAML là nguồn OpenAPI. Hai endpoint dùng filter chain Bearer hiện có; không cần role cụ thể, nhưng phải là user ACTIVE với phiên đúng owner và còn hiệu lực. Không nhận danh tính thay thế từ query/header tự khai hoặc body.

GET trả 200/PROFILE_GET_SUCCESS; PATCH trả 200/PROFILE_UPDATE_SUCCESS. Cả hai dùng envelope và UserProfile đủ chín trường: id, userName, email, fullName, phoneNumber, status, roles, hasPassword, linkedProviders. phoneNumber luôn xuất hiện và có thể null; roles/providers là mảng không null, sắp xếp theo tên. Role đọc hiện tại từ database, không dùng danh sách trong principal để dựng hồ sơ. hasPassword dựa trên PasswordHash khác null; không trả hash hoặc ProviderUserId.

## Transaction và PATCH

- GET dùng READ_COMMITTED/readOnly, tra user từ principal; user không còn tồn tại trả 401 SESSION_INACTIVE. Không cập nhật hồ sơ, phiên hoặc LastSeenAt.
- PATCH dùng READ_COMMITTED, khóa user PESSIMISTIC_WRITE trước khi đọc phiên và lấy now sau khóa. Kiểm tra lại user ACTIVE, phiên đúng owner/chưa thu hồi/chưa hết hạn; không đạt trả 401 SESSION_INACTIVE và không ghi dữ liệu. Không nạp entity user/phiên từ transaction xác thực vào transaction cập nhật; open-in-view vẫn tắt.
- Dùng hasFullName()/hasPhoneNumber() của DTO: bỏ qua giữ nguyên; tên được strip/validation; phoneNumber null để xóa. Chỉ ghi hai trường này. Giữ nguyên username/email/password/status/roles/identities và phiên/token.
- Hai PATCH cùng user xử lý tuần tự theo khóa database. PATCH trường khác nhau giữ được cả hai; cùng trường theo thứ tự thực hiện database, không cam kết thứ tự client gửi.
- Trigger V5 quản lý UpdatedAt. Cập nhật cùng giá trị vẫn 200 và không đổi timestamp; response không thêm UpdatedAt. Không tự ghi timestamp hoặc sửa migration.
- Dựng DTO trong transaction; controller chỉ nhận kết quả sau commit. Lỗi lưu/dựng DTO/commit rollback toàn bộ. Giữ ánh xạ lỗi nền tảng, không log đầu vào hoặc chi tiết exception nhạy cảm.

Bearer vẫn xác minh chữ ký/claim trước khi vào controller. Quyết định kiểm tra lại dưới khóa bổ sung bảo vệ cho PATCH đang chờ khóa, không tạo cam kết hủy request đã hoàn tất kiểm tra và đang chạy trước một lần thu hồi sau đó.

## Kiểm thử

`UserProfileServiceTests` kiểm tra mapping, presence, nguồn role/provider, now sau khóa và biên hiệu lực phiên. `UserProfileHttpTests` dùng filter chain/controller/mapper thật với service giả lập, không datasource/Flyway; kiểm tra envelope, principal, validation, 401/405/415/500/503. Các kiểm thử này không đọc .env hoặc khóa dự án.

Chạy nhóm không database từ backend/ bằng Java 25/Maven Wrapper:

```powershell
.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests,UserSecurityTests,UserSecurityHttpTests,RefreshLogoutServiceTests,UserProfileServiceTests,UserProfileHttpTests" test
```

`UserProfileIntegrationTests` chỉ bật khi system property chọn rõ UserDB_test, dùng PostgreSQL/Flyway/HTTP và khóa RSA tạm. Fixture được dọn theo ID user; không dùng database dự án. Kiểm tra cập nhật/UpdatedAt, dữ liệu ngoài hồ sơ giữ nguyên, rollback kể cả COMMIT, PATCH đồng thời và kiểm tra lại user/phiên sau khi thực sự chờ khóa. Clock và latch kiểm soát ca hết hạn trong lúc chờ; pg_stat_activity xác nhận chờ khóa database.

Database test và credentials phải được chuẩn bị trước; V6 vẫn yêu cầu RefreshTokens rỗng trước khi áp dụng, không xóa token để vượt điều kiện:

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

Kết quả thực tế và môi trường từng lần chạy duy trì tại [hướng dẫn phát triển](development.md#kiểm-thử-hiện-có). Không triển khai mật khẩu, API quản lý phiên, Service JWT, Google, SMTP hoặc cleanup trong đợt UC06.
