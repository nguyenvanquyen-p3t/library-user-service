# Library User Service

Microservice tài khoản và danh tính của hệ thống quản lý thư viện, phục vụ Web và ứng dụng Android. Service được thiết kế để quản lý đăng ký, đăng nhập Local/Google, hồ sơ, mật khẩu, phiên đăng nhập và cung cấp API tích hợp cho các service khác.

Trong thiết kế, Admin Service điều phối khóa/mở khóa qua API nội bộ của User Service; API Gateway kiểm tra access token và phiên qua introspection. Quản lý role qua API ngoài phạm vi.

Tiếp tục dự án hoặc bắt đầu chat mới: đọc [bàn giao phát triển](docs/handoff.md) và [mục lục docs](docs/README.md). Luồng UC01–UC12 đã có trong Markdown; không cần Word hoặc lịch sử chat để tra cứu yêu cầu.

## Trạng thái dự án

Dự án đã có nền tảng backend/bảo mật và triển khai login/refresh/logout UC02–UC04 và GET/PATCH hồ sơ UC06; các chức năng còn lại tiếp tục theo thiết kế.

Ngày 2026-11-10, chủ dự án chốt [triển khai chức năng trước, xác thực service sau](docs/confirmed-decisions.md#deferred-service-auth): khi code chức năng, các service mặc định tin tưởng nhau, chưa yêu cầu Service JWT hoặc kiểm tra quyền service. Sau khi hoàn tất tất cả chức năng mới bổ sung cơ chế này. Xác thực user/phiên, ownership và actor ADMIN vẫn giữ nguyên. Sau đó cùng ngày đã yêu cầu và triển khai [nền tảng bảo mật người dùng và login UC02](docs/user-security.md).

- **Đã có:** cấu hình Spring Boot, Maven Wrapper, migration Flyway V1–V6 cho 7 bảng, [nền tảng entity/repository, DTO, validation và lỗi](docs/foundation.md), test nền tảng/context và [OpenAPI/Swagger UI](docs/api/openapi.md) cho 20 operation thiết kế.
- **Đã triển khai:** `POST /api/v1/auth/login`, Argon2id, JWT RS256, phiên/refresh token và xác thực Bearer kiểm tra database; xem [bảo mật người dùng](docs/user-security.md).
- **Đã triển khai thêm ngày 2026-11-11:** `POST /api/v1/auth/refresh` và `POST /api/v1/auth/logout`; rotation/thu hồi phiên trong transaction có khóa user. Tổng 3 operation IMPLEMENTED.
- **Đã triển khai ngày 2026-11-12:** [GET/PATCH hồ sơ UC06](docs/user-profile.md), PATCH kiểm tra lại user/phiên sau khóa user. Tổng hiện tại 5 IMPLEMENTED/15 DESIGN_ONLY.
- **Chưa triển khai:** 15 operation còn lại, gồm đăng ký, mật khẩu, Google/SMTP, API quản lý phiên, API nội bộ và introspection/Gateway. Không có API quản lý role.

V6 đã áp dụng vào database dự án theo xác nhận của chủ dự án ngày 2026-10-25. Chi tiết trạng thái tại [mô hình dữ liệu](docs/data-model.md#migration-v6-quản-lý-phiên).

Thiết kế giữ refresh token trong JSON cho cả Web/Android và dùng Service JWT để Gateway gọi introspection. Chi tiết nằm trong [quyết định đã xác nhận](docs/confirmed-decisions.md).

## Công nghệ và yêu cầu

| Thành phần | Phiên bản hoặc vai trò |
| --- | --- |
| Java | JDK 25, cấu hình `JAVA_HOME` |
| Spring Boot | 4.1.1; Web MVC, Data JPA, Security, Validation |
| PostgreSQL | 15 trở lên, database phải được tạo trước |
| Flyway | Quản lý schema; Hibernate chỉ validate |
| Maven Wrapper | Có sẵn trong `backend/`; cần mạng để tải dependency lần đầu |

## Chạy nhanh

Các lệnh dùng **PowerShell trên Windows**, bắt đầu từ thư mục gốc repository.

**1. Tạo database** bằng pgAdmin hoặc `psql` với tài khoản có quyền phù hợp:

```sql
CREATE DATABASE "UserDB";
```

Flyway tạo schema trong database đã tồn tại. Khi nâng cấp database cũ, **V6 chỉ chạy nếu RefreshTokens hoàn toàn rỗng**; không tự xóa hoặc thu hồi token để vượt điều kiện này.

**2. Chuẩn bị cấu hình:**

```powershell
cd backend
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
}
```

Sửa `backend/.env` theo PostgreSQL của bạn:

```properties
DB_USERNAME=postgres
DB_URL=jdbc:postgresql://localhost:5432/UserDB
DB_PASSWORD=your_password
```

File dùng định dạng Java properties: `KEY=value`, không bao giá trị bằng dấu nháy. Không commit `.env` hoặc thông tin xác thực thực tế.

**3. Chạy ứng dụng** từ `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Ứng dụng lắng nghe tại `http://localhost:5001`. Giữ thư mục chạy là `backend/` vì `.env` được đọc theo thư mục làm việc. Khởi động thành công chưa có nghĩa API đã được triển khai.

Ứng dụng cần cặp khóa PEM đã cấu hình trong `.secrets/`; thiếu/sai khóa sẽ dừng khởi động, không tự sinh lại. Xem [cấu hình khóa](docs/user-security.md#cấu-hình-khóa-và-khởi-động). Login cần tài khoản ACTIVE có hash Argon2id đúng định dạng; không có user/mật khẩu mặc định hoặc endpoint đăng ký trong đợt này.

Mở Swagger UI tại `http://localhost:5001/swagger-ui.html`; đặc tả JSON tại `/v3/api-docs`, YAML tại `/v3/api-docs.yaml`. UI vẫn tắt `Try it out`; 5 operation đã triển khai, 15 operation còn thiết kế. Xem [cách dùng và kiểm tra OpenAPI](docs/api/openapi.md).

Xem [hướng dẫn môi trường cục bộ](docs/local-development.md) để cấu hình chi tiết, đóng gói JAR và xử lý lỗi.

## Kiểm thử

Test `contextLoads` dùng PostgreSQL thật và chạy Flyway. Tạo database riêng bằng pgAdmin hoặc `psql`:

```sql
CREATE DATABASE "UserDB_test";
```

Sau đó chạy từ `backend/`, dùng credentials đã cấu hình:

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

Có thể kiểm tra riêng nền tảng, bảo mật và Swagger mà không dùng database bằng `.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests,UserSecurityTests,UserSecurityHttpTests,RefreshLogoutServiceTests,UserProfileServiceTests,UserProfileHttpTests" test` từ `backend/`. Test persistence/login/refresh/logout tích hợp chỉ bật khi system property chọn rõ `UserDB_test`; test login có commit và dọn đúng fixture. Chưa có database kiểm thử tự động. Xem [hướng dẫn phát triển](docs/development.md).

## Tài liệu và đóng góp

| Nội dung | Tài liệu |
| --- | --- |
| Mục lục toàn bộ docs | [docs/README.md](docs/README.md) |
| Phạm vi và tích hợp giữa các service | [Kiến trúc](docs/architecture.md) |
| Bản đồ 20 API thiết kế | [Tra cứu API](docs/api/README.md) |
| Thiết kế hiện hành của 16 API gốc | [API hiện hành](docs/api/current-contract.md) |
| Ví dụ Excel lịch sử | [Danh mục nguồn](docs/api/source-catalog.md) |
| Thiết kế 4 API phiên và introspection | [Quản lý phiên](docs/api/sessions.md) |
| Schema và ràng buộc migration | [Mô hình dữ liệu](docs/data-model.md) |
| Quy trình phát triển và kiểm thử | [Hướng dẫn đóng góp](docs/development.md) |
| Các vấn đề cần thống nhất | [Quyết định còn mở](docs/open-decisions.md) |

Thành viên nhóm đọc phạm vi và các quyết định trước khi thay đổi nghiệp vụ. Khi đề xuất thay đổi, mô tả mục đích, cập nhật docs liên quan và ghi rõ kiểm tra đã chạy. Câu hỏi nghiệp vụ cần được chủ dự án xác nhận. Hướng dẫn riêng cho coding agent nằm trong [AGENTS.md](AGENTS.md).
