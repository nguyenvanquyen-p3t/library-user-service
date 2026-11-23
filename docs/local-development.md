# Thiết lập môi trường và chạy cục bộ

**Hiện trạng ngày 2026-11-23.** [Thiết lập mật khẩu lần đầu UC09](user-password-setup.md) đã triển khai. Tổng **20 IMPLEMENTED**. Các số liệu theo mốc trước là lịch sử.

Hướng dẫn chi tiết cho thành viên phát triển User Service. Xem [README dự án](../README.md) để bắt đầu nhanh; các lệnh bên dưới dùng PowerShell trên Windows.

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

<a id="cau-hinh-otp-smtp"></a>

### Cấu hình OTP và SMTP cho UC01, UC05 và UC09

`OTP_HMAC_KEY` nhận khóa ngẫu nhiên 32 byte mã hóa Base64. Dùng chung khóa giữa các instance cùng môi trường. `OTP_MAIL_FROM` là địa chỉ gửi đã cấu hình với nhà cung cấp. Trong `backend/.env`, cấu hình SMTP bằng `spring.mail.host`, `spring.mail.port`, `spring.mail.username`, `spring.mail.password` và các thuộc tính TLS/xác thực theo môi trường. Mẫu tên thuộc tính có trong [backend/.env.example](../backend/.env.example).

Spring Mail mặc định đặt timeout kết nối, đọc và ghi là 5.000 ms mỗi loại. Có thể đặt lại bằng `spring.mail.properties[mail.smtp.connectiontimeout]`, `spring.mail.properties[mail.smtp.timeout]` và `spring.mail.properties[mail.smtp.writetimeout]`. Chốt nhà cung cấp, địa chỉ gửi, TLS và timeout môi trường đích theo [Q06](open-decisions.md#q06) trước khi gửi thư thật.

Ứng dụng có thể khởi động khi chưa cấu hình OTP/SMTP. Khi cấp mã cho tài khoản đủ điều kiện, thiếu khóa HMAC hợp lệ, địa chỉ gửi hoặc cấu hình máy chủ thư trả `503 SERVICE_UNAVAILABLE` trước khi lưu OTP. Sau commit, lỗi SMTP hoặc timeout trả 503 ở UC01 và UC09. UC05 vẫn trả 200 với thông báo chung, kể cả khi email không tồn tại. Cấp khóa và thông tin xác thực qua môi trường hoặc `backend/.env` cục bộ. [Triển khai UC05](user-password-reset.md) · [Triển khai UC09](user-password-setup.md)

## Chạy ứng dụng

Muốn bật hai API Google UC08, đặt `GOOGLE_ALLOWED_CLIENT_IDS` và `GOOGLE_REGISTRATION_HMAC_KEY` trong môi trường hoặc `backend/.env`. Với client có `azp` khác `aud`, thêm client ID tương ứng vào `GOOGLE_ALLOWED_PRESENTER_CLIENT_IDS`. [Định dạng cấu hình UC08](user-google-auth.md#xác-minh-và-cấu-hình)

Từ `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

Ứng dụng dùng tên `user-service`, cổng mặc định `5001`, địa chỉ `http://localhost:5001`. Đã có `POST /api/v1/auth/login`, `/api/v1/auth/refresh` và `/api/v1/auth/logout`, [GET/PATCH hồ sơ UC06](user-profile.md), [đổi mật khẩu UC07](user-password.md) và [quản lý phiên UC10–UC12](user-sessions.md); [introspection](api/sessions.md#introspect) đã có từ ngày 2026-11-15; [GET user nội bộ](api/internal-integration.md#tra-cứu-user) đã có cùng ngày; PATCH trạng thái đã có ngày 2026-11-17. [Đăng ký Local UC01](user-registration.md) đã có ngày 2026-11-20. Xem [bản đồ API](api/README.md).

Ứng dụng cần private key PKCS#8 PEM và public key X.509 PEM hợp lệ; mặc định đọc `.secrets/user-access-private.pem` và `.secrets/user-access-public.pem`. Có thể đặt `USER_ACCESS_PRIVATE_KEY_LOCATION`/`USER_ACCESS_PUBLIC_KEY_LOCATION` bằng vị trí Spring Resource bắt đầu `file:`. Thiếu/hỏng/không khớp khóa thì khởi động thất bại, không tự sinh lại; không đưa PEM vào Git/log. Xem [cấu hình bảo mật](user-security.md#cấu-hình-khóa-và-khởi-động).

Sau khi cấu hình OTP/SMTP, có thể tạo tài khoản ACTIVE qua [đăng ký Local UC01](user-registration.md) rồi đăng nhập. Login cần hash Argon2id đúng định dạng. Bearer kiểm tra user, phiên và role từ database.

Đọc hợp đồng thiết kế tại `http://localhost:5001/swagger-ui.html`; tải OpenAPI từ `/v3/api-docs` hoặc `/v3/api-docs.yaml`. UI tắt `Try it out`. Hướng dẫn và lệnh kiểm thử tài liệu không dùng database tại [OpenAPI/Swagger UI](api/openapi.md).

Flyway tự áp dụng V1–V6; Hibernate dùng `ddl-auto: validate`. **V6 chỉ chạy khi RefreshTokens rỗng**, kể cả không còn token đã hết hạn/thu hồi. Không tự xóa dữ liệu hoặc Flyway history để vượt điều kiện này. Chi tiết schema, constraint và migration nằm trong [mô hình dữ liệu](data-model.md).

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

Lệnh trên sử dụng tài khoản và mật khẩu đã cấu hình. Tài khoản đó cần có quyền tạo bảng trong database kiểm thử. `PersistenceFoundationTests` kiểm tra entity/repository/constraint; `LoginIntegrationTests` kiểm tra HTTP UC02, `RefreshLogoutIntegrationTests` kiểm tra UC03–UC04, `UserProfileIntegrationTests` kiểm tra UC06, `ChangePasswordIntegrationTests` kiểm tra UC07, `UserSessionIntegrationTests` kiểm tra UC10–UC12, commit/rollback và cạnh tranh; các test tích hợp này chỉ bật khi system property chọn rõ `UserDB_test`. Test persistence rollback fixture; test login có commit thật và dọn đúng user fixture bằng ID. Schema Flyway và identity sequence không rollback theo fixture. Test dùng khóa ngẫu nhiên tạm, không đọc private key dự án.

Có thể chạy `.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests,UserSecurityTests,UserSecurityHttpTests,RefreshLogoutServiceTests,UserProfileServiceTests,UserProfileHttpTests,ChangePasswordServiceTests,ChangePasswordHttpTests,UserSessionServiceTests,UserSessionHttpTests,IntrospectionServiceTests,IntrospectionHttpTests,InternalUserServiceTests,InternalUserHttpTests,InternalUserStatusServiceTests,InternalUserStatusHttpTests" test` từ `backend/` để kiểm tra nền tảng/bảo mật/Swagger mà không dùng `.env` hoặc database. Xem [bảo mật người dùng](user-security.md#kiểm-thử-và-giới-hạn).

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
- **V6 báo RefreshTokens không rỗng:** dừng nâng cấp và thống nhất cách xử lý với chủ dự án; migration không tự xóa/thu hồi token cũ.
- **Cổng `5001` đang được dùng:** dừng tiến trình chiếm cổng hoặc đặt biến môi trường `SERVER_PORT` trước khi khởi động.
