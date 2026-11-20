# Quản lý phiên UC10–UC12

**Hiện trạng ngày 2026-11-20:** [Đăng ký Local UC01](api/current-contract.md#register) đã triển khai. Tổng **14 IMPLEMENTED/6 DESIGN_ONLY**. Các số liệu theo mốc trước là lịch sử.

Triển khai ngày 2026-11-14 theo kế hoạch chủ dự án duyệt. Ngày 2026-11-15 thêm [introspection](api/sessions.md#introspect), giữ nguyên 7 bảng và Flyway V1–V6. Chính sách tại [quyết định UC10–UC12](confirmed-decisions.md#user-sessions-uc10-uc12); request/response tại [API phiên](api/sessions.md). Gateway và Service JWT vẫn chưa triển khai. [GET user nội bộ](api/internal-integration.md#tra-cứu-user) triển khai ngày 2026-11-15, tại mốc GET user nội bộ có **11 IMPLEMENTED/9 DESIGN_ONLY**.

## HTTP và xác thực

`session.web.UserSessionController` dùng `UserPrincipal` từ Bearer hiện có; không yêu cầu role cụ thể. Controller `@Hidden` giữ YAML là nguồn OpenAPI. Không nhận userId/currentSessionId hoặc role tự khai thay danh tính đã xác minh.

| Endpoint | Thành công |
| --- | --- |
| GET /api/v1/users/me/sessions | 200 SESSION_LIST_SUCCESS, data SessionPage |
| DELETE /api/v1/users/me/sessions/{sessionId} | 200 SESSION_REVOKE_SUCCESS, data null |
| POST /api/v1/users/me/sessions/revoke-others | 200 OTHER_SESSIONS_REVOKED, data RevokeOthersResult |

Cả ba không yêu cầu body. GET mặc định page 0/size 20, page int32 không âm, size 1–100; từ chối sai kiểu hoặc ngoài giới hạn bằng 400 VALIDATION_ERROR theo mã con hiện có. UUID path yêu cầu dạng 8-4-4-4-12 chữ số hex, chấp nhận chữ hoa/chữ thường; sai dùng INVALID_FORMAT. Header/cache và lỗi 401/405/500/503 dùng nền tảng Security/exception hiện có; không mở thêm path công khai, không tạo cookie hoặc HTTP session.

SessionItem đủ bảy trường theo hợp đồng, deviceName/clientType có thể null. Không trả UserAgent, hash hoặc token. LastSeenAt trả đúng giá trị đã lưu, không khẳng định đang online. Metadata thiết bị của login hiện vẫn null; đợt này không sửa login hoặc thu thập metadata.

## Transaction danh sách UC10

`UserSessionService.list` dùng REPEATABLE_READ/readOnly để kiểm tra user/phiên gọi, đếm tổng và đọc trang trong cùng snapshot. User không còn/không ACTIVE hoặc phiên thiếu/sai owner/hết hạn/thu hồi trả 401 SESSION_INACTIVE. Chỉ đọc không khóa user hoặc ghi LastSeenAt.

Một mốc now dùng cho toàn thao tác. Chỉ liệt kê phiên của caller có RevokedAt null và ExpiresAt > now. Sắp xếp CreatedAt giảm dần rồi Id tăng dần; isCurrent so với sid từ principal, không đưa phiên gọi lên đầu. Lần tải sau có thể khác do login/logout đã commit.

Repository đếm tổng trả long; truy vấn native LIMIT/OFFSET dùng offset long để tránh giới hạn int của JPA. Trang vượt tổng trả items rỗng, giữ page/size được yêu cầu và tổng thực tế. Truy vấn Page nền tảng hiện hữu tiếp tục giữ nguyên. Dựng DTO trong transaction; lỗi dựng kết quả/commit không trả thành công.

## Transaction thu hồi UC11 và UC12

Hai writer dùng READ_COMMITTED: khóa user PESSIMISTIC_WRITE → lấy now → đọc lại và kiểm tra user ACTIVE/phiên gọi đúng owner, chưa thu hồi/chưa hết hạn. Đây là cùng khóa với login/refresh/logout/PATCH/đổi mật khẩu. Không dùng entity từ bước Bearer; caller mất hiệu lực trong lúc chờ khóa trả 401 SESSION_INACTIVE trước nghiệp vụ thu hồi.

UC11 từ chối target trùng phiên gọi bằng 400 CURRENT_SESSION_REVOKE_NOT_ALLOWED. Target không tồn tại hoặc thuộc user khác đều trả 404 SESSION_NOT_FOUND. Target của mình đã hết hạn/thu hồi trả 200 chung, không sửa cả phiên lẫn token. Target còn hiệu lực ghi RevokedAt và SESSION_REVOKED, thu hồi token của phiên còn hiệu lực trong cùng transaction.

UC12 chọn các phiên khác của caller còn hiệu lực; token còn hiệu lực thuộc nhóm phiên này được bulk update trước khi bulk update phiên với OTHER_SESSIONS_REVOKED. Hai query dùng cùng now/owner/sid được giữ; thứ tự này giữ subquery chọn đúng nhóm phiên trước khi chúng mất hiệu lực. revokedCount lấy số phiên thực sự cập nhật, chuyển sang long; gọi lại khi chưa có phiên mới trả 0.

Phiên hiện tại/token của nó không bị UC11/UC12 thu hồi. Giữ nguyên phiên đã kết thúc và token của những phiên đó, token đã hết hạn/thu hồi, liên kết rotation, LastSeenAt và metadata. Không save lại entity phiên sau bulk update. Chỉ trả thành công sau commit; lỗi ghi token/phiên/dựng kết quả/commit rollback toàn bộ, không trả số đếm một phần.

Refresh hoàn tất trước thu hồi thì token mới của phiên đích bị thu hồi; refresh sau thu hồi bị từ chối. Login trước UC12 có phiên mới thuộc phạm vi thu hồi; login sau UC12 vẫn được tạo phiên mới. Bearer của phiên bị thu hồi trả SESSION_INACTIVE ở lần kiểm tra tiếp theo. Không cam kết hủy request đã vượt kiểm tra trước commit.

## Kiểm thử và giới hạn

`UserSessionServiceTests` kiểm tra mapping, offset long, thứ tự khóa/Clock/kiểm tra caller, ownership, lịch sử đã kết thúc và thứ tự bulk update. `UserSessionHttpTests` dùng filter/controller/mapper thật với repository/service giả lập, không datasource/.env; kiểm tra principal, schema/envelope, phân trang/UUID và lỗi xác thực/HTTP/hạ tầng.

`UserSessionIntegrationTests` dùng PostgreSQL/Flyway/HTTP và RSA tạm, chỉ bật khi system property chọn rõ UserDB_test. Kiểm tra phân trang/metadata, snapshot khi count và đọc trang xen kẽ thu hồi, giữ lịch sử token/phiên, rollback cả COMMIT, hai yêu cầu cùng target, cạnh tranh login/refresh và phiên gọi mất hiệu lực sau chờ khóa thật. Latch/Clock điều khiển thứ tự, pg_stat_activity xác nhận chờ khóa; fixture có commit được dọn theo ID user.

Chạy từ backend/ bằng Java 25 và Maven Wrapper; database test/credentials phải được chuẩn bị trước, không dùng database dự án. V6 yêu cầu RefreshTokens rỗng trước khi áp dụng; không xóa token để vượt điều kiện:

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

Lệnh nhóm không database và kết quả thực tế tại [development](development.md#kiểm-thử-hiện-có). Không thêm Service JWT, introspection/Gateway, cleanup hoặc cập nhật LastSeenAt; các quyết định còn mở ngoài UC10–UC12 giữ nguyên.
