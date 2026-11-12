# Nền tảng bảo mật người dùng và login UC02

Nền tảng bảo mật/login UC02 triển khai ngày 2026-11-10; refresh/logout UC03–UC04 bổ sung ngày 2026-11-11 theo kế hoạch được chủ dự án duyệt. GET/PATCH hồ sơ [UC06](user-profile.md) bổ sung ngày 2026-11-12; năm endpoint login/refresh/logout/GET hồ sơ/PATCH hồ sơ `IMPLEMENTED`, 15 operation khác `DESIGN_ONLY`. Không sửa V1–V6 hoặc dữ liệu dự án. [Quyết định UC02](confirmed-decisions.md#user-security-uc02) và [UC03–UC04](confirmed-decisions.md#refresh-logout-uc03-uc04) duy trì lựa chọn nghiệp vụ; tài liệu này duy trì cách dùng implementation.

## Thành phần và trách nhiệm

Package `security` chứa `PasswordService`, `RefreshTokenGenerator`, `UserAccessTokenService`, `UserSessionAuthenticator`, `UserPrincipal`, filter Bearer và writer lỗi. `UserSecurityConfiguration` cung cấp `PasswordEncoder`, `Clock` UTC và cấu hình khóa. Controller chỉ xử lý HTTP; `LoginService.login` là transaction được Spring quản lý.

- `PasswordEncoder` dùng Argon2id, memory 19456 KiB, iterations 2, parallelism 1, salt 16 byte, hash 32 byte; định dạng lưu có prefix `{argon2id}`. Chỉ xác minh định dạng/tham số này; không hỗ trợ hash cũ, plaintext hoặc tự cập nhật hash. Password giữ nguyên Unicode/khoảng trắng, không cắt ngắn. Không tìm thấy user vẫn so sánh dummy hash ngẫu nhiên để giảm khác biệt thời gian.
- Refresh token sinh từ 32 byte `SecureRandom`, Base64 URL không padding; database chỉ giữ SHA-256 hex. Đây là hash token ngẫu nhiên, không dùng SHA-256 để lưu mật khẩu. Rotation UC03 và logout UC04 đã triển khai; cleanup chưa triển khai.
- JWT ký RS256 bằng Nimbus JOSE, xác minh bằng Spring Security `NimbusJwtDecoder`; chỉ nhận `typ=at+jwt`, `kid` tin cậy, issuer/audience đã cấu hình, `sub` string integer dương/int32, `sid` UUID chuẩn, `iat`/`exp` số nguyên. `aud` luôn được phát dạng mảng. Không có claim `roles`, email hoặc hồ sơ.
- `Clock` UTC quyết định `iat`/`exp`; access token có hạn 3600 giây. Xác minh cho dung sai 30 giây: từ chối tại `now >= exp + 30 giây` hoặc `iat > now + 30 giây`; nếu có `nbf` thì cũng kiểm tra dung sai. Hạn phiên không có dung sai.
- `UserSessionAuthenticator` nhận JWT đã xác minh, kiểm tra user tồn tại/ACTIVE và phiên đúng owner, chưa thu hồi, `now < expiresAt`; đọc role hiện tại từ database. Principal bất biến có `userId`, `sessionId`, roles; authorities là `ROLE_<Name>`. Không cập nhật `LastSeenAt` trong request.

Argon2id dùng Bouncy Castle; tham số khởi đầu theo [OWASP Password Storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Spring dependencies theo BOM hiện có, không thêm OAuth2 authorization server hoặc Service JWT.

## Cấu hình khóa và khởi động

Chạy Maven/JAR từ `backend/` như [hướng dẫn cục bộ](local-development.md). Database phải được chuẩn bị trước; Flyway vẫn tự chạy và V6 vẫn yêu cầu RefreshTokens hoàn toàn rỗng. Không xóa dữ liệu hoặc sửa migration để vượt điều kiện.

| Thuộc tính `app.security.user-access` | Giá trị mặc định |
| --- | --- |
| `issuer` | `urn:library:user-service` |
| `audience` | `library-api` |
| `active-key-id` | `user-access-v1` |
| `private-key-location` | `file:./.secrets/user-access-private.pem` |
| `public-keys.user-access-v1` | `file:./.secrets/user-access-public.pem` |

Có thể đổi vị trí file bằng `USER_ACCESS_PRIVATE_KEY_LOCATION` và `USER_ACCESS_PUBLIC_KEY_LOCATION` trong môi trường hoặc `.env`; giá trị là vị trí Spring Resource, ví dụ đường dẫn bắt đầu bằng `file:`. Không ghi nội dung PEM vào biến cấu hình, Git hoặc log.

Nạp private key PKCS#8 PEM và public key X.509 SubjectPublicKeyInfo PEM; xác minh cặp active khớp bằng ký/kiểm tra dữ liệu thử. Thiếu, hỏng, không khớp hoặc RSA public key dưới 2048 bit làm khởi động thất bại với thông báo chung. Cặp dự án hiện hữu là RSA 3072 bit; code không sinh/thay khóa.

Danh sách `public-keys` hỗ trợ giữ public key cũ trong lúc thay khóa có kiểm soát. Cấp public key mới cho bên xác minh trước, đổi private key và `active-key-id`, giữ key cũ đến khi token cũ hết hạn cộng dung sai. Không tải khóa từ header JWT, không có JWKS hoặc rotation tự động. Private key bị lộ phải ngừng tin key đó ngay theo Q14.

## HTTP và transaction

Login nhận đúng `identifier` và `password`, trả envelope với `LOGIN_SUCCESS` và `TokenPair` hiện hành, `Cache-Control: no-store`. Không seed user hoặc cấp mật khẩu mặc định; cần có tài khoản ACTIVE mang hash Argon2id đúng định dạng trước khi đăng nhập. Các fixture trong test không phải tài khoản ứng dụng.

1. Chuẩn hóa identifier như DTO hiện có; có `@` tra email, còn lại username. Khóa bản ghi user bằng `PESSIMISTIC_WRITE`.
2. Không tìm thấy/sai password/hash không được hỗ trợ trả 401 `INVALID_CREDENTIALS`; hash null trả 409 `PASSWORD_NOT_CONFIGURED`; password đúng rồi user BANNED trả 403 `ACCOUNT_BANNED`.
3. Tạo UUID phiên mới bằng insert dùng thời gian database: `ExpiresAt = CreatedAt + 2592000 giây`, giữ default CreatedAt/LastSeenAt và metadata null. Tạo refresh token có hạn riêng 604800 giây từ lúc cấp, lưu hash, cùng transaction.
4. Ký JWT chứa user/phiên tương ứng; lỗi ký/lưu/commit rollback. Controller chỉ nhận kết quả ngoài transactional proxy sau commit. Login mới không thu hồi các phiên cũ.

Các writer password/status/refresh/revoke sau này phải khóa cùng user trước khi đọc/ghi dữ liệu phiên/token để tuần tự với login. Tầng xác thực request chỉ kiểm tra tại thời điểm đọc; không cam kết hủy request đã qua kiểm tra trước commit thu hồi.

Runtime dùng ba filter chain: GET tài liệu công khai; ba path login/refresh/logout mọi method công khai để MVC trả 405/415 và bỏ qua Bearer cũ; các request còn lại yêu cầu user Bearer. Header writer của chain công khai đặt Cache-Control no-store cả khi validation/nghiệp vụ lỗi. HTTP Basic, form login, logout mặc định, CSRF và HTTP session đã tắt; default user tự sinh của Spring Boot cũng bị loại. Không nhận token qua cookie/query, không tin header tự khai user/role. Error dispatch giữ lỗi HTTP gốc. Method security được bật cho endpoint có kiểm tra quyền sau này.

## Refresh và logout UC03–UC04

Đã triển khai ngày 2026-11-11. Hai controller nhận `RefreshTokenRequest` qua JSON, giữ nguyên token, dùng validation hiện có và không yêu cầu access token. Controller `@Hidden` giữ YAML là hợp đồng OpenAPI; không nhận userId/sid từ client. Mọi response của hai endpoint có `Cache-Control: no-store`, không tạo cookie hoặc HTTP session.

`RefreshService.refresh` và `LogoutService.logout` dùng transaction `READ_COMMITTED`: tra hash lấy owner bằng projection, khóa user `PESSIMISTIC_WRITE`, rồi đọc lại entity token/phiên. Không nạp entity token trước khi chờ khóa để tránh dữ liệu cũ trong persistence context; lấy `now` sau khóa. Login đã khóa cùng bản ghi user. Writer password/status/revoke sau này phải giữ cùng quy ước. Kết quả chỉ đến controller sau commit; lỗi lưu/ký/commit rollback.

- **UC03:** kiểm tra token trước user rồi phiên. Token không tồn tại/hết hạn/đã thu hồi → 401 `REFRESH_TOKEN_INVALID`; token còn hợp lệ nhưng user BANNED → 403 `ACCOUNT_BANNED`; user không còn hoặc phiên thiếu/sai owner/hết hạn/thu hồi → 401 `SESSION_INACTIVE`. Hai mốc hết hạn dùng `now >= expiresAt`, không áp dung sai JWT vào refresh token/phiên.
- Tạo refresh token mới cùng user/sid, hạn 604800 giây từ lúc cấp, lưu hash. Insert lấy ID mới rồi ghi đồng thời RevokedAt/ReplacedByTokenId của token cũ; ký access token RS256 hạn 3600 giây trong cùng transaction. Không tạo/gia hạn phiên, không đổi LastSeenAt hoặc metadata. Reuse token cũ không thu hồi phiên/token thay thế.
- **UC04:** không kiểm tra user ACTIVE hoặc hạn/trạng thái refresh token; token cũ còn bản ghi được dùng để xác định đúng phiên. Phiên còn hiệu lực được ghi RevokedAt và `LOGOUT`, đồng thời thu hồi mọi refresh token của phiên chưa thu hồi/chưa hết hạn. Không đổi lịch sử token đã thu hồi/hết hạn, liên kết rotation hoặc các phiên khác.
- Phiên đã hết hạn/thu hồi không bị cập nhật lại. Token không tìm thấy hoặc không còn xác định được phiên trả 200 `LOGOUT_SUCCESS`/data null; đây là kết quả chung, không chứng minh vừa thu hồi được phiên server. Database/commit lỗi vẫn trả lỗi. Bearer thuộc phiên đã thu hồi trả `SESSION_INACTIVE` ở lần kiểm tra tiếp theo.

Nếu refresh đã commit nhưng mất response, token cũ không được cấp lại cặp token hoặc có grace period. Client cần đăng nhập lại khi không còn token hợp lệ. Không tự retry refresh vô hạn; 503 không có nghĩa phiên chắc chắn bị thu hồi. Retention giữ toàn bộ lịch sử token như quyết định hiện có; chưa có cleanup.

| Tình huống của nền tảng Bearer | HTTP / mã |
| --- | --- |
| Không có Authorization | 401 `AUTHENTICATION_REQUIRED` |
| Authorization lặp/sai định dạng, JWT sai | 401 `ACCESS_TOKEN_INVALID` |
| User không tồn tại/BANNED; phiên thiếu, khác owner, hết hạn/thu hồi | 401 `SESSION_INACTIVE` |
| Không đủ quyền | 403 `ACCESS_DENIED` |
| Database/phụ thuộc tạm không khả dụng, không mở được transaction | 503 `SERVICE_UNAVAILABLE` |
| Lỗi ngoài dự kiến | 500 `INTERNAL_ERROR` |

Lỗi filter dùng envelope hiện có, data null, `Cache-Control: no-store`; 401 có `WWW-Authenticate: Bearer`. Không biến lỗi database thành lỗi token. Các request không có controller nghiệp vụ vẫn chưa phải API hoạt động dù đã qua filter. Ba operation nội bộ sẽ có chính sách service tin tưởng khi được triển khai; chưa mở chúng trong đợt UC02. Actor ADMIN và introspection vẫn là bước sau.

Không log DTO/exception chứa thông tin xác thực. Đã tắt chi tiết request và log Hibernate JDBC error/bind/extract có thể chứa hash trong lỗi constraint; handler chỉ ghi loại exception. Không bật lại các log này hoặc log SQL/HTTP body có dữ liệu thật.

## Kiểm thử và giới hạn

Các test dùng khóa RSA tạm tự sinh; không đọc private key dự án. Chạy nhóm không dùng database từ `backend/`:

```powershell
.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests,UserSecurityTests,UserSecurityHttpTests,RefreshLogoutServiceTests,UserProfileServiceTests,UserProfileHttpTests" test
```

Toàn bộ test cần PostgreSQL riêng và credentials đã cấu hình, **chỉ chọn `UserDB_test`; không dùng database dự án hoặc xóa token để vượt V6**:

```powershell
.\mvnw.cmd "-Dspring.datasource.url=jdbc:postgresql://localhost:5432/UserDB_test" test
```

`LoginIntegrationTests` chỉ bật khi system property chỉ rõ UserDB_test; có HTTP/commit thật, dọn đúng các user fixture bằng ID sau mỗi test. `PersistenceFoundationTests` tiếp tục rollback fixture. Schema Flyway và identity sequence không rollback theo fixture. Các ca gồm TTL/ownership, hash, lỗi login, rollback lỗi lưu/ký/COMMIT, hai login đồng thời, login chờ transaction cập nhật trạng thái và hợp đồng OpenAPI trong context thật.

`RefreshLogoutServiceTests` dùng Clock kiểm soát để kiểm tra biên thời gian và thứ tự lỗi. `RefreshLogoutIntegrationTests` chỉ bật trên UserDB_test, kiểm tra HTTP/commit thật, lịch sử rotation, logout bằng token cũ, rollback lỗi lưu/ký/COMMIT, hai refresh và refresh/logout ở cả hai thứ tự có chờ khóa thực. Fixture được dọn theo ID user; khóa test tạm như UC02.

Chưa có API quản lý phiên UC10–UC12, introspection, đăng ký, mật khẩu hoặc quản trị trạng thái. GET/PATCH hồ sơ UC06 đã triển khai; xem [transaction UC06](user-profile.md#transaction-và-patch). Chưa có Gateway, xác thực actor Admin, CORS, rate limit login, Google/SMTP hoặc cleanup. Không coi 5 API đã chạy là hoàn tất bảo mật/tích hợp toàn hệ thống. Kết quả lần chạy được ghi ở [hướng dẫn phát triển](development.md#kiểm-thử-hiện-có).
