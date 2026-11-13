# Đổi mật khẩu UC07

`PUT /api/v1/users/me/password` triển khai ngày 2026-11-13 theo kế hoạch chủ dự án duyệt. Tổng hiện tại **6 IMPLEMENTED/14 DESIGN_ONLY**, giữ nguyên 7 bảng và V1–V6. Hợp đồng tại [UC07](api/current-contract.md#password-change), chính sách tại [quyết định UC07](confirmed-decisions.md#user-password-uc07), validation tại [schema JSON](api/json-schema.md#validation).

## HTTP và xác thực

`user.web.ChangePasswordController` lấy `UserPrincipal` từ Bearer hiện có, không cần role cụ thể; user phải ACTIVE và phiên đúng owner/còn hiệu lực. Controller `@Hidden` giữ YAML là nguồn OpenAPI. Không nhận userId/sid thay danh tính đã xác minh.

Request JSON dùng `ChangePasswordRequest` với currentPassword/newPassword/confirmPassword bắt buộc, không null; từ chối trường ngoài danh sách. Mật khẩu mới 6–128 ký tự, giữ nguyên Unicode/khoảng trắng và phải khớp xác nhận. Không áp độ dài mật khẩu mới cho currentPassword; không cấm mật khẩu mới trùng mật khẩu hiện tại. DTO che thông tin xác thực trong toString().

| Trường hợp | HTTP / messageCode |
| --- | --- |
| Thành công sau commit | 200 PASSWORD_CHANGE_SUCCESS, data null |
| Chưa có PasswordHash | 409 PASSWORD_NOT_CONFIGURED |
| Mật khẩu hiện tại sai hoặc hash hỏng/không hỗ trợ | 400 CURRENT_PASSWORD_INCORRECT |
| User/phiên mất hiệu lực | 401 SESSION_INACTIVE |
| Thiếu/sai Bearer | 401 AUTHENTICATION_REQUIRED/ACCESS_TOKEN_INVALID |
| Validation, JSON, method/media type | Bộ lỗi nền tảng 400/405/415 |
| Hạ tầng tạm lỗi / lỗi ngoài dự kiến | 503 SERVICE_UNAVAILABLE / 500 INTERNAL_ERROR |

Không cấp token mới hoặc cookie; client xóa token và đăng nhập lại sau thành công. Giữ cache header của security hiện có. Lỗi không trả password/hash/token đã nhập hoặc chi tiết exception.

## Transaction và thu hồi

`user.service.ChangePasswordService.changePassword` dùng READ_COMMITTED:

1. Khóa user PESSIMISTIC_WRITE cùng login/refresh/logout/PATCH hồ sơ; lấy now sau khóa và đọc lại phiên từ database.
2. User không còn/không ACTIVE hoặc phiên thiếu/sai owner/đã thu hồi/now >= expiresAt trả SESSION_INACTIVE trước khi xét mật khẩu. Không dùng entity từ transaction xác thực Bearer.
3. Kiểm tra có password, xác minh password hiện tại, dùng PasswordService.encode với Argon2id hiện có để tạo hash mới và lưu user.
4. Hai truy vấn revokeActiveByUser thu hồi mọi phiên/token của user chưa thu hồi và expiresAt > now. Phiên ghi PASSWORD_CHANGED, kể cả phiên gọi. Token được xét theo hạn riêng, vẫn thu hồi nếu phiên chứa nó đã kết thúc.
5. Chỉ trả thành công sau commit. Lỗi hash/lưu/thu hồi/commit rollback toàn bộ.

Bulk update flush password trước ghi, không clear persistence context; không save lại entity phiên đã đọc sau bulk update. Trigger V5 quản lý UpdatedAt. Giữ nguyên lịch sử đã hết hạn/thu hồi, liên kết rotation, LastSeenAt, metadata, hồ sơ/roles và Google identity; không xóa dữ liệu hoặc triển khai cleanup.

Login/refresh trước UC07 có phiên/token mới thuộc phạm vi thu hồi. Login sau UC07 phải xác minh password mới, vẫn được tạo phiên mới. Hai UC07 đã qua Bearer vẫn tuần tự theo khóa: request sau thấy phiên đã bị thu hồi và trả SESSION_INACTIVE. Cơ chế không cam kết hủy request đã vượt kiểm tra trước transaction thu hồi commit.

## Kiểm thử

ChangePasswordServiceTests kiểm tra thứ tự khóa/Clock/phiên/mật khẩu và lỗi không ghi. ChangePasswordHttpTests dùng filter/controller/mapper thật với service giả lập, không datasource/.env; kiểm tra principal, envelope, validation, giữ nguyên password, lỗi HTTP và dữ liệu nhạy cảm.

ChangePasswordIntegrationTests chỉ bật khi system property chọn rõ UserDB_test. Dùng PostgreSQL/Flyway/HTTP và RSA tạm; kiểm tra hash mới, thu hồi nhiều phiên/token, giữ lịch sử/identity, rollback kể cả lỗi COMMIT, hai UC07 và cạnh tranh với login/refresh ở cả hai thứ tự. Latch/Clock điều khiển thứ tự; pg_stat_activity xác nhận chờ khóa thật. Fixture có commit được dọn theo ID user.

Chạy từ backend/ với Java 25 và Maven Wrapper. Database test/credentials phải được chuẩn bị trước; V6 yêu cầu RefreshTokens rỗng, không xóa token để vượt điều kiện:

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

Lệnh nhóm không database và kết quả thực tế của đợt UC07 tại [hướng dẫn phát triển](development.md#kiểm-thử-hiện-có). Không dùng database/khóa dự án cho kiểm thử.
