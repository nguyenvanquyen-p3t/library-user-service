# Library User Service

Backend cho dịch vụ người dùng của hệ thống thư viện, sử dụng Spring Boot, Spring Data JPA, Spring Security, PostgreSQL và Flyway.

Hiện dự án có cấu hình khởi động, migration tạo schema, dữ liệu role ban đầu và một bài test tải Spring context. Chưa có controller, service, repository hoặc entity nghiệp vụ.

## Yêu cầu

- JDK 25, cấu hình `JAVA_HOME` trỏ tới JDK.
- PostgreSQL đang chạy và tài khoản có quyền tạo bảng trong database đích.
- Kết nối mạng trong lần build đầu để Maven Wrapper tải Maven và các dependency. Không cần cài Maven riêng.

Các lệnh bên dưới dùng PowerShell trên Windows. **Chạy từ thư mục `backend/`** vì ứng dụng đọc `.env` theo thư mục làm việc hiện tại.

```powershell
cd backend
java -version
.\mvnw.cmd -v
```

## Chuẩn bị database

Kết nối PostgreSQL bằng pgAdmin hoặc `psql`, rồi tạo database rỗng:

```sql
CREATE DATABASE "UserDB";
```

Tên `"UserDB"` được đặt trong dấu nháy kép để giữ đúng chữ hoa, khớp URL mẫu. Nếu dùng tài khoản khác tài khoản tạo database, cần cấp quyền thích hợp trước khi khởi động.

Flyway tạo bảng trong database đã tồn tại; migration hiện tại không tạo database.

## Cấu hình môi trường

Tạo `.env` từ file mẫu nếu chưa có:

```powershell
if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
}
```

Sửa `backend/.env` theo môi trường của bạn:

```properties
DB_USERNAME=postgres
DB_URL=jdbc:postgresql://localhost:5432/UserDB
DB_PASSWORD=your_password
```

| Biến | Ý nghĩa | Giá trị mặc định trong ứng dụng |
| --- | --- | --- |
| `DB_URL` | JDBC URL tới database PostgreSQL | `jdbc:postgresql://localhost:5432/UserDB` |
| `DB_USERNAME` | Tài khoản kết nối database | Bắt buộc cung cấp |
| `DB_PASSWORD` | Mật khẩu kết nối database | Bắt buộc cung cấp |

File `.env` được đọc theo định dạng Java properties; dùng cú pháp `KEY=value`, không thêm `export` hoặc dấu nháy bao quanh giá trị. Với giá trị chứa dấu `\`, cần escape thành `\\` theo định dạng properties.

Cũng có thể cung cấp các biến trên bằng biến môi trường của hệ điều hành; chúng được ưu tiên hơn giá trị trong `.env`.

`.gitignore` bỏ qua `.env` và các file `.env.*`, đồng thời cho phép chia sẻ `.env.example`. Chỉ điền giá trị mẫu vào `.env.example`. Quy tắc ignore không loại bỏ file đã được Git theo dõi; trong trường hợp đó cần bỏ theo dõi bằng `git rm --cached backend/.env` từ thư mục gốc dự án.

## Chạy ứng dụng

Từ `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Ứng dụng dùng tên `user-service`, cổng mặc định `5001`, địa chỉ `http://localhost:5001`. Hiện chưa có API nghiệp vụ để gọi thử.

Khi khởi động với database rỗng, Flyway áp dụng các migration trong `src/main/resources/db/migration/`:

- `V1__create_user_tables.sql`: tạo `Users`, `Roles`, `UserRoles`, `UserIdentities`, `RefreshTokens`, `VerificationCodes` cùng các ràng buộc và index.
- `V2__seed_roles.sql`: thêm các role `READER`, `LIBRARIAN`, `ADMIN`.

Hibernate được cấu hình `ddl-auto: validate`, không tự tạo hoặc cập nhật schema. Khi migration đã được áp dụng ở môi trường dùng chung, hãy thêm migration phiên bản tiếp theo để thay đổi schema, tránh sửa migration cũ.

## Chạy test

Bài test `contextLoads` hiện tải toàn bộ Spring context, bao gồm kết nối database và Flyway. Dự án chưa có profile test hoặc database kiểm thử tự động.

Tạo database riêng cho test để không dùng database phát triển:

```sql
CREATE DATABASE "UserDB_test";
```

Từ `backend/`, chỉ định database kiểm thử cho lần chạy Maven:

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

Lệnh trên sử dụng tài khoản và mật khẩu đã cấu hình. Tài khoản đó cần có quyền tạo bảng trong database kiểm thử. Test hiện tại chỉ kiểm tra context khởi động thành công, chưa kiểm tra riêng từng ràng buộc hoặc dữ liệu seed.

## Đóng gói

Sau khi test thành công, đóng gói ứng dụng từ `backend/` mà không chạy lại test:

```powershell
.\mvnw.cmd package -DskipTests
java -jar .\target\user-service-0.0.1-SNAPSHOT.jar
```

Giữ thư mục làm việc là `backend/` khi chạy JAR để ứng dụng tiếp tục đọc đúng `.env`.

## Xử lý lỗi thường gặp

- **Thiếu `DB_USERNAME` hoặc `DB_PASSWORD`:** kiểm tra `.env` nằm trong `backend/`, thư mục chạy là `backend/`, hoặc cung cấp biến môi trường tương ứng. Import `.env` là tùy chọn nên việc thiếu file không tự báo lỗi tại bước đọc cấu hình.
- **Không kết nối được PostgreSQL:** kiểm tra PostgreSQL đang chạy, host/cổng trong `DB_URL`, database đã được tạo và thông tin đăng nhập đúng.
- **Flyway không khởi tạo được schema:** dùng database rỗng cho lần chạy đầu và kiểm tra quyền tạo bảng. Không xóa dữ liệu hoặc lịch sử Flyway để bỏ qua lỗi trên database đang sử dụng.
- **Cổng `5001` đang được dùng:** dừng tiến trình chiếm cổng hoặc đặt biến môi trường `SERVER_PORT` trước khi khởi động.
