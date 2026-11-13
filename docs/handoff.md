# Bắt đầu một phiên làm việc mới

Cập nhật hướng dẫn đọc: 2026-10-24; cập nhật trạng thái V6 theo xác nhận của chủ dự án ngày 2026-10-25. Nguồn nghiệp vụ được bàn giao ngày 2026-10-22; đợt này không đối chiếu lại Word hoặc database. Đây là điểm vào để tiếp tục công việc mà không cần lịch sử chat, không phải tài liệu lặp lại toàn bộ chính sách.

## Thứ tự đọc

1. [Quyết định đã xác nhận](confirmed-decisions.md): yêu cầu đã chốt, không hỏi lại hoặc thay bằng đề xuất cũ.
2. [Yêu cầu và transaction](requirements.md), [12 use case](use-cases.md): hành vi, Actor, ngoại lệ và kết quả.
3. [Bản đồ 20 API](api/README.md), rồi operation tương ứng trong [16 API hiện hành](api/current-contract.md) hoặc [API phiên/introspection](api/sessions.md).
4. [Schema](data-model.md), [kiến trúc](architecture.md): ràng buộc dữ liệu và trách nhiệm từng service.
5. [Quyết định còn mở](open-decisions.md): xác định câu hỏi ảnh hưởng trực tiếp đến phần sắp làm; không tự chốt.
6. [Chạy cục bộ](local-development.md), [quy trình phát triển](development.md), [AGENTS.md](../AGENTS.md).

## Hiện trạng cần biết

**Triển khai ngày 2026-11-13:** [đổi mật khẩu UC07](user-password.md) đã có; tổng 6 IMPLEMENTED/14 DESIGN_ONLY. READ_COMMITTED, khóa cùng user và kiểm tra lại user/phiên sau khóa; đổi hash và thu hồi mọi phiên/token còn hiệu lực cùng transaction. Giữ nguyên lịch sử đã kết thúc. Các mốc/số liệu theo ngày bên dưới là lịch sử. Kết quả kiểm tra đợt này tại [development](development.md#kiểm-thử-hiện-có).

- **Triển khai ngày 2026-11-12:** [GET/PATCH hồ sơ UC06](user-profile.md) đã có, tổng 5 IMPLEMENTED/15 DESIGN_ONLY. PATCH dùng READ_COMMITTED, khóa user rồi kiểm tra lại user/phiên; mất hiệu lực trong lúc chờ trả 401 SESSION_INACTIVE. Đã chạy 55 test không database và 84 test toàn bộ trên PostgreSQL tạm; chi tiết tại [kiểm thử](development.md#kiểm-thử-hiện-có). Các số liệu triển khai và kết quả theo ngày bên dưới là lịch sử, không phải hiện trạng mới nhất.

- **Triển khai ngày 2026-11-11:** [refresh/logout UC03–UC04](user-security.md#refresh-và-logout-uc03uc04) đã có transaction READ_COMMITTED, khóa user trước khi đọc lại token/phiên, rotation một lần và logout bằng token cũ. Tổng 3 operation IMPLEMENTED/17 DESIGN_ONLY. Ba path login/refresh/logout công khai, bỏ qua Bearer cũ; các path khác giữ bảo vệ. Những đoạn ngày 2026-11-10 bên dưới là lịch sử UC02.

- **Triển khai mới ngày 2026-11-10:** [bảo mật người dùng và login UC02](user-security.md) đã có Argon2id, JWT RS256/PEM, Bearer kiểm tra user/phiên/role từ database và transaction login có khóa user. Login `IMPLEMENTED`, 19 operation còn `DESIGN_ONLY`. Thông tin “chưa có JWT/endpoint” ở bàn giao các ngày trước bên dưới là lịch sử. Basic/form login/default user đã tắt; ứng dụng cần cặp PEM hợp lệ khi khởi động. Test dùng khóa tạm và database riêng.

- **Quyết định mới ngày 2026-11-10:** [hoàn tất chức năng trước, xác thực service sau](confirmed-decisions.md#deferred-service-auth). Khi được yêu cầu code, Admin/Borrow/Gateway mặc định tin tưởng nhau; tạm chưa yêu cầu Service JWT hoặc kiểm tra danh tính/quyền service. Không cần chốt Q03/Q11a để code hai API nội bộ/introspection. Giữ user JWT, phiên, ownership và actor ADMIN trong X-Admin-Access-Token. Các mô tả Service JWT bên dưới là thiết kế đích sau khi hoàn tất chức năng. Bản thân quyết định hoãn xác thực service chỉ đổi docs; nền tảng user security/login UC02 đã được triển khai sau đó cùng ngày, chưa mở ba API nội bộ.

- Ngày 2026-11-03, chủ dự án yêu cầu triển khai [nền tảng backend](foundation.md): package theo chức năng, entity/repository cho 7 bảng, DTO đủ hợp đồng 20 API, envelope, validation và exception handler. Đã có kiểm thử nền tảng và persistence từ mốc 2026-11-03; ngày 2026-11-10 đã bổ sung JWT/login UC02 và test bảo mật/login. 19 operation khác chưa triển khai; không sửa V1–V6. Đọc tài liệu nền tảng trước khi nối DTO/repository vào nghiệp vụ.

- Ngày 2026-10-31 chốt [Service JWT/quyền Admin/Borrow và actor Admin](confirmed-decisions.md#internal-service-auth), [SMTP sau commit và lỗi gửi/retry](confirmed-decisions.md#otp-email-delivery), [retry/cạnh tranh/audit trạng thái và cấm khóa ADMIN](confirmed-decisions.md#admin-status-contract), [hai mã lỗi tích hợp](confirmed-decisions.md#integration-error-contract). Ban đầu hoãn cấu hình JWT; sau đó đã chốt riêng Q14. Service JWT Q03/Q11a vẫn mở, không tự áp RS256/5 phút hoặc cấu hình khóa user token cho Service JWT.
- [User access token Q14 đã chốt](confirmed-decisions.md#access-token-key-pair): iss urn:library:user-service dùng chung, không chia môi trường; aud ["library-api"]; kid user-access-v1; một cặp khóa cố định, private key ký/public key xác minh. Đã tạo RSA 3072 bit bằng JDK 25 tại backend/.secrets/user-access-private.pem và user-access-public.pem, loại khỏi Git và kiểm tra ký/xác minh từ PEM. Cấp/phân phối public key qua file/cấu hình, thay khóa có kiểm soát, chưa thêm JWKS. Ứng dụng hiện đã đọc PEM và dùng cặp khóa cấu hình cho JWT/login UC02; test dùng khóa tạm, không đọc/thay khóa dự án.

- Repo có Spring Boot 4.1.1, Java 25, Maven wrapper, PostgreSQL, Flyway V1–V6, nền tảng bảo mật và login UC02. Chưa có 19 operation còn lại, Google, email hay Gateway.
- V6 bổ sung UserSessions, đưa schema lên 7 bảng và đã áp dụng vào database dự án theo xác nhận của chủ dự án ngày 2026-10-25. Xem [trạng thái migration](data-model.md#migration-v6-quản-lý-phiên); khi làm việc với môi trường khác, kiểm tra trạng thái migration của môi trường đó trước khi chạy.
- V6 yêu cầu RefreshTokens hoàn toàn rỗng, kể cả bản ghi hết hạn. Không tự xóa/thu hồi token để vượt điều kiện; không sửa migration đã áp dụng/chia sẻ.
- Ngày 2026-11-01, chủ dự án yêu cầu xây dựng OpenAPI và Swagger UI: đã thêm [hợp đồng 20 operation và hạ tầng tài liệu](api/openapi.md), dependency springdoc 3.2.0, quyền truy cập các đường dẫn tài liệu và kiểm thử tài liệu tách database. Phạm vi 2026-11-01 chỉ cho phép tài liệu; yêu cầu 2026-11-10 sau đó đã cho phép và triển khai nền tảng user JWT/login UC02. Chạy riêng `.\mvnw.cmd -Dtest=ApiDocumentationTests test` từ `backend/` không dùng datasource/Flyway; toàn bộ `test` vẫn cần database riêng.
- Lệnh backend chạy từ `backend/` vì `.env` được tìm theo thư mục làm việc; cổng cấu hình là 5001. Context test sử dụng datasource và Flyway thật, không chạy tùy tiện trên database dự án.

Đối chiếu implementation tại [pom.xml](../backend/pom.xml), [application.yml](../backend/src/main/resources/application.yml), [migrations](../backend/src/main/resources/db/migration) và [context test](../backend/src/test/java/com/library/userservice/UserServiceApplicationTests.java). Nếu cấu trúc code đã thay đổi, cập nhật mục hiện trạng thay vì coi thiết kế là code đang chạy.

## Các ranh giới dễ hiểu nhầm

| Dễ hiểu nhầm | Nơi đọc quy tắc đầy đủ |
| --- | --- |
| Đăng ký Local khác Google; mặc định READER và không có API quản lý role | [Vòng đời tài khoản](confirmed-decisions.md#account-lifecycle), [role](confirmed-decisions.md#roles) |
| Token có TTL riêng nhưng không vượt được hiệu lực phiên | [Token](confirmed-decisions.md#user-tokens), [thời hạn phiên](confirmed-decisions.md#session-lifetime) |
| Reuse refresh khác logout bằng token cũ | [UC03](confirmed-decisions.md#refresh-reuse), [UC04](confirmed-decisions.md#logout) |
| UC11 không chọn phiên hiện tại; UC12 giữ phiên hiện tại | [Thu hồi phiên](confirmed-decisions.md#session-revocation), [API phiên](api/sessions.md) |
| Google tự liên kết và chống đăng ký lặp có giới hạn đã được chấp nhận | [Liên kết Google](confirmed-decisions.md#google-linking), [registrationToken](confirmed-decisions.md#google-registration-ttl), [chống lặp](confirmed-decisions.md#google-replay) |
| Không tự thêm chính sách OTP, mật khẩu hoặc thời gian dọn dữ liệu | [OTP/mật khẩu](confirmed-decisions.md#otp-password-policy), [retention](confirmed-decisions.md#refresh-retention) |

## Khi bắt đầu phát triển

Xác định UC/API được yêu cầu, đọc quyết định và transaction liên quan, rồi kiểm tra code hiện hữu. Chỉ hỏi những mục PENDING DECISION ảnh hưởng đến công việc đó; không mở rộng 7 bảng/20 API để tự giải quyết khoảng trống. Dùng bản API hiện hành; chỉ đọc danh mục nguồn khi cần truy vết Excel.

Khi được yêu cầu triển khai, cập nhật trạng thái từng phần từ thiết kế sang đã triển khai kèm kiểm tra thực sự đã chạy. Không đánh dấu toàn bộ API hoàn tất vì build hoặc context test thành công. Tài liệu hiện còn quyết định chưa chốt nên chưa thể suy ra một hợp đồng sản xuất hoàn chỉnh chỉ từ các ví dụ JSON.
