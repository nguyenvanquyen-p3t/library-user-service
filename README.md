# Library User Service

Backend cho dịch vụ người dùng của hệ thống thư viện, sử dụng Spring Boot, Spring Data JPA, Spring Security, PostgreSQL và Flyway.

Hiện dự án có cấu hình khởi động, migration tạo schema, dữ liệu role ban đầu và một bài test tải Spring context. Chưa có controller, service, repository hoặc entity nghiệp vụ.

## Yêu cầu

- JDK 25, cấu hình `JAVA_HOME` trỏ tới JDK.
- PostgreSQL 15 trở lên đang chạy và tài khoản có quyền tạo bảng, function và trigger trong database đích. V4 sử dụng cú pháp `ON DELETE SET NULL` cho một cột cụ thể của khóa ngoại ghép.
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
- `V3__normalize_and_validate_user_fields.sql`: chuẩn hóa username/email hiện có thành chữ thường, tên role và provider thành chữ hoa (ví dụ `GOOGLE`), đồng thời bỏ khoảng trắng hai đầu. Các trường này không được rỗng, chứa khoảng trắng hoặc ký tự điều khiển; email trong `VerificationCodes` áp dụng cùng quy tắc. Username không được chứa `@` để phân biệt với email trong ô đăng nhập chung. Họ tên không được rỗng, có khoảng trắng hai đầu hoặc chứa ký tự điều khiển như tab/xuống dòng; vẫn cho phép dấu cách giữa các từ. `ProviderUserId`, `CodeHash` và `PasswordHash` khi khác `NULL` phải có ký tự không phải khoảng trắng, được giữ nguyên nội dung.
- `V4__enforce_refresh_token_integrity.sql`: chặn hash token rỗng hoặc chỉ chứa khoảng trắng; token thay thế phải cùng người dùng, có `Id` lớn hơn token cũ và chỉ thay thế tối đa một token cũ. Token đã được thay thế phải có thời điểm thu hồi. Xóa token thay thế chỉ bỏ liên kết, giữ nguyên người sở hữu token cũ.
- `V5__auto_update_users_updated_at.sql`: `RevokedAt` và `UsedAt`, khi có giá trị, không được trước `CreatedAt` tương ứng. Trigger cập nhật `Users.UpdatedAt` theo thời điểm bắt đầu câu lệnh khi các cột khác thực sự thay đổi. Cập nhật không đổi dữ liệu hoặc chỉ gán `UpdatedAt` sẽ giữ timestamp cũ.

Với database đã chạy V1/V2, Flyway chỉ áp dụng tiếp V3–V5. V3 sẽ thất bại nếu dữ liệu hiện có bị trùng sau chuẩn hóa hoặc không đáp ứng ràng buộc; migration không tự xóa hay gộp tài khoản.

Sau V3, backend cần chuẩn hóa username/email thành chữ thường, tên role và provider thành chữ hoa trước khi lưu và tra cứu; provider Google dùng giá trị `GOOGLE`. Database từ chối giá trị chưa đúng dạng chuẩn. Quy tắc khoảng trắng và ký tự điều khiển dùng các lớp POSIX của PostgreSQL, có phân loại Unicode phụ thuộc locale. Kiểm tra định dạng email và chính sách mật khẩu vẫn thuộc backend; migration không sửa hash hoặc `ProviderUserId` và vẫn cho phép `PasswordHash = NULL` cho tài khoản đăng nhập ngoài.

Với ô đăng nhập chung, backend phân loại dữ liệu có `@` là email, còn lại là username, rồi chuẩn hóa và tra cứu theo trường tương ứng. Cần kiểm tra định dạng email ở backend; ràng buộc database hiện tại không thay thế bước này.

Sau V4, backend cần tạo token mới bằng identity tăng dần và cập nhật đồng thời `RevokedAt`, `ReplacedByTokenId` của token cũ trong cùng transaction, kèm khóa bản ghi hoặc cập nhật có điều kiện để chống hai yêu cầu đổi cùng một token. Không reset sequence xuống thấp hơn các token hiện có hoặc dùng lại token có `Id` nhỏ hơn làm token thay thế. Điều kiện `Id` tăng dần chặn vòng trong chuỗi mà không cần trigger duyệt đệ quy.

Khi triển khai dọn token, cần giữ token đã thu hồi và các liên kết trong suốt khoảng thời gian hệ thống còn cần phát hiện việc sử dụng lại token cũ. Xóa một token sẽ xóa liên kết tới nó; chỉ dựa vào `ExpiresAt` để xóa ngay có thể làm mất lịch sử rotation cần thiết. Các migration chưa ấn định thời gian lưu giữ vì đây là chính sách của backend.

Sau V5, nếu API cần timestamp mới ngay sau khi cập nhật, ORM cần đọc lại giá trị do database sinh. Việc từ chối sử dụng token/mã đã hết hạn vẫn thuộc backend; token được phép bị thu hồi sau khi đã hết hạn.

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
