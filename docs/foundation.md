# Nền tảng backend dùng chung

Theo yêu cầu chủ dự án ngày 2026-11-03, đã triển khai cấu trúc package, entity/repository cho 7 bảng, DTO cho hợp đồng 20 API, envelope, validation và xử lý exception. Ngày 2026-11-10 bổ sung [bảo mật người dùng và login UC02](user-security.md), gồm JWT và controller/service login. Không sửa V1–V6; riêng login là `IMPLEMENTED`, 19 operation khác `DESIGN_ONLY`. Google, SMTP, Gateway chưa triển khai.

## Package và trách nhiệm

Tất cả package nằm dưới `com.library.userservice`:

| Package | Nội dung đã có |
| --- | --- |
| `config` | OpenAPI, quyền đọc tài liệu và cấu hình Jackson 3 của ứng dụng |
| `common.api` | `ApiResponse<T>`, `SuccessCode`, `ErrorCode`, `FieldViolation`, `ValidationErrorData` |
| `common.validation` | Chuẩn hóa, constraint dữ liệu đầu vào, xác nhận mật khẩu và PATCH |
| `common.exception` | `ApplicationException`, `ApiExceptionHandler`, `IntrospectionErrorFormatter` |
| `user.persistence` | `User`, `Role`, `UserRole`, khóa ghép `UserRoleId`, repository và `UserStatus` |
| `auth.persistence` | `UserIdentity`, `VerificationCode`, `RefreshToken`, repository và `VerificationPurpose` |
| `session.persistence` | `UserSession`, repository và `ClientType` |
| `user.dto`, `auth.dto`, `session.dto`, `internal.dto` | Request/response theo [hợp đồng hiện hành](api/README.md); không có request DTO rỗng cho operation không có body |

Không tạo tầng service/controller rỗng. Đã thêm `auth.service`/`auth.web` cho login và package `security`; repository có truy vấn khóa user, đọc role và insert phiên phục vụ UC02. Rotation, thu hồi và cleanup chưa triển khai. Các writer tiếp theo phải tuân thủ [transaction và cạnh tranh](requirements.md#ranh-giới-transaction), khóa user như login trước khi thao tác phiên/token.

## Ánh xạ dữ liệu

Entity dùng tên bảng/cột có dấu nháy kép và physical naming strategy chuẩn, giữ nguyên chữ hoa/chữ thường PostgreSQL. ID identity là `Integer`, ID phiên là `UUID`, thời gian là `Instant`. FK được ánh xạ bằng ID, không có collection hai chiều hoặc cascade ORM. Constraint database tiếp tục kiểm tra ownership và liên kết rotation.

`CreatedAt` do database sinh; `Users.UpdatedAt` do trigger V5 sinh và Hibernate đọc lại sau ghi. `UserSessions.LastSeenAt` lấy default database khi insert, có thể cập nhật sau đó; chưa có chính sách/tác vụ tự cập nhật. Không tự áp TTL trong entity. `RefreshToken.sessionId` không cập nhật qua ORM và setter từ chối đổi phiên đã gán; trigger database vẫn là lớp bảo vệ độc lập.

Caller truyền `now` vào truy vấn phiên còn hiệu lực, dùng `PageRequest` không có sort tùy ý; truy vấn sắp xếp `createdAt DESC, id ASC`. Caller tạo UUID cho phiên mới. UC02 dùng SessionCreationRepository insert JDBC trong transaction JPA để TTL khớp timestamp database. Với caller khác dùng `save` cho UUID đã gán, Spring Data có thể merge: dùng entity do `save` trả về, không giả định đầu vào đã được quản lý.

## DTO, JSON và validation

Request chuẩn hóa username/email bằng `strip()` và lowercase với `Locale.ROOT`, fullName bằng `strip()`, trước khi kiểm tra độ dài. Password, OTP, token và Google subject được giữ nguyên. Phân loại whitespace Unicode của Java có thể khác POSIX PostgreSQL theo locale; constraint database vẫn giữ nguyên và không bị thay bằng validation ứng dụng.

Constraint `@Input` và các constraint liên quan thực hiện [quy tắc JSON](api/json-schema.md); kiểm tra hiệu lực token/OTP, UNIQUE và quyền thuộc bước nghiệp vụ sau này. Mã lỗi constraint là mã ổn định, được chuyển sang thông báo tiếng Việt trong `data.errors`. Độ dài mật khẩu mới không áp lên password login/currentPassword.

`ProfilePatchRequest` giữ cờ presence riêng: thiếu trường để giữ nguyên, `phoneNumber: null` để xóa; cờ không xuất hiện trong JSON. Dùng `hasFullName()`/`hasPhoneNumber()` khi áp dụng PATCH. `SessionQuery()` chứa mặc định page 0/size 20; controller tương lai phải áp mặc định query khi binding và vẫn từ chối query sai kiểu/giới hạn.

`JsonConfiguration` tùy biến mapper do Spring Boot quản lý: từ chối trường lạ, số/boolean bị ép thành string, số thập phân thành integer và số thành enum. Không tạo mapper thay thế độc lập cho HTTP. DTO chứa thông tin xác thực che dữ liệu trong `toString()`; không log DTO/exception gốc có dữ liệu đầu vào.

Response giữ đủ required/null theo hợp đồng, mảng không null. Ba nhánh Google là ba record riêng; introspection active/inactive là hai kiểu riêng. `ApiResponse<T>` không tự bọc response toàn ứng dụng và không được dùng cho introspection. Không serialize entity làm response.

## Xử lý lỗi và giới hạn

`ApiExceptionHandler` chỉ xử lý request thuộc controller trong package dự án; lỗi method security chuyển lại Security để trả 403 đúng envelope. Với 405/415 xảy ra trước khi chọn controller, kiểm tra path đã đăng ký của controller dự án; không giả định mọi path `/api/v1/**` đã có endpoint. springdoc và tài nguyên tĩnh giữ cơ chế riêng; filter Security dùng `SecurityErrorWriter` theo [bảo mật người dùng](user-security.md).

Validation trả HTTP 400/`VALIDATION_ERROR`; JSON hỏng hoặc thiếu body bắt buộc dùng `INVALID_REQUEST`. Chỉ constraint UNIQUE đã biết được ánh xạ thành conflict nghiệp vụ; các vi phạm khác dùng `INTERNAL_ERROR`. Lỗi truy cập dữ liệu tạm thời dùng 503; lỗi ngoài dự kiến dùng 500. Log xử lý lỗi 5xx chỉ ghi loại exception, không ghi exception message, SQL hoặc rejected value. Không bật log chi tiết HTTP/SQL chứa thông tin xác thực.

`ApplicationException` nhận mã lỗi đã chốt; `OTP_RATE_LIMITED` yêu cầu thêm số giây `Retry-After` do nghiệp vụ tính. `IntrospectionRequest.fromForm` kiểm tra danh sách trường, tham số lặp và token thiếu/rỗng; caller phải hoàn tất xác thực/quyền Gateway trước khi gọi. `IntrospectionErrorFormatter` chuẩn bị đủ tám biến thể lỗi/header riêng; chưa gắn formatter vào endpoint hoặc authentication entry point. Cấu hình Service JWT vẫn PENDING DECISION.

## Kiểm thử

Từ `backend/`, dùng Java 25 và Maven Wrapper. Kiểm thử nền tảng/Swagger không đọc `.env`, không kết nối datasource hoặc chạy Flyway:

```powershell
.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests" test
```

Kiểm thử dữ liệu tải context thực và Flyway. **Database `UserDB_test` và credentials phải được chuẩn bị trước; V6 yêu cầu RefreshTokens hoàn toàn rỗng. Không xóa dữ liệu hoặc sửa migration để vượt điều kiện.**

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

`PersistenceFoundationTests` chỉ bật khi system property `spring.datasource.url` chỉ rõ database có tên `UserDB_test`; dữ liệu fixture nằm trong transaction rollback. Việc Flyway tạo schema và identity sequence tăng không được rollback cùng fixture. `contextLoads` hiện hữu vẫn cần chỉ định database test khi chạy toàn bộ test.

Các test kiểm tra serialization, presence/null, validation, lỗi HTTP qua controller chỉ có trong test, formatter introspection, Hibernate validate đủ 7 entity, repository, trigger, FK ownership/rotation, SessionId bất biến và phân trang phiên. Đây không phải kiểm thử API nghiệp vụ hoặc bằng chứng JWT/Gateway đã hoạt động.

Kết quả kiểm tra thực tế được ghi trong [hướng dẫn phát triển](development.md#kiểm-thử-hiện-có); không dùng kết quả SQL lịch sử thay cho test nền tảng hiện tại.
