# Hướng dẫn phát triển và kiểm thử

Biên tập ban đầu ngày 2026-10-24; bổ sung hạ tầng tài liệu ngày 2026-11-01. Chủ dự án đã yêu cầu [OpenAPI/Swagger UI](api/openapi.md), gồm kiểm thử tài liệu tách database. Các bước code và kiểm thử API nghiệp vụ bên dưới chỉ áp dụng khi chủ dự án yêu cầu triển khai phần tương ứng.

## Bắt đầu

Theo [README dự án](../README.md) để bắt đầu nhanh hoặc [hướng dẫn môi trường cục bộ](local-development.md) để cấu hình, đóng gói và xử lý lỗi. Cần JDK 25, PostgreSQL 15+ và `backend/.env`. Lệnh Maven phải chạy từ `backend/` để import `.env` đúng vị trí. Không lưu mật khẩu thực vào docs hoặc commit `.env`.

## Quy trình thay đổi

1. Đọc [kiến trúc](architecture.md), [quyết định đã xác nhận](confirmed-decisions.md) và use case liên quan. Phạm vi 7 bảng/20 API; không có API role, blacklist hoặc Redis. Đã có migration, docs, OpenAPI/Swagger, [nền tảng backend](foundation.md) và [bảo mật/login UC02](user-security.md); 19 operation khác chờ yêu cầu triển khai. Hỏi chủ dự án trước khi giải quyết yêu cầu chưa rõ.
2. Tra cứu operation tại [bản đồ API](api/README.md) và bản hiện hành được liên kết; danh mục nguồn chỉ để truy vết Excel. Chốt phần request/response/auth/lỗi liên quan còn PENDING DECISION trước khi tích hợp; riêng cấu hình Service JWT Q03/Q11a chốt ở bước sau khi hoàn tất chức năng theo quyết định ngày 2026-11-10.
3. Khi được yêu cầu triển khai, viết code và kiểm thử hành vi; thêm migration mới nếu cần thay schema đã được chia sẻ. Với yêu cầu docs-only, chỉ sửa và kiểm tra tài liệu.
4. Cập nhật trạng thái docs theo phần thực sự hoàn thành. Ghi rõ kiểm thử đã chạy và giới hạn môi trường trong PR.

Đã có package theo chức năng, entity/repository, DTO và [nền tảng bảo mật/login UC02](user-security.md) ngày 2026-11-10. Chỉ controller/service login đã triển khai. Các phần tiếp theo giữ transaction tách khỏi xử lý HTTP, không trả entity chứa hash và khóa cùng bản ghi user để tuần tự với login. Quy tắc nền tảng được duy trì tại [foundation.md](foundation.md).

## Kiểm thử hiện có

Đã bổ sung `UserSecurityTests` (Argon2id/JWT/khóa và biên thời gian), `UserSecurityHttpTests` (filter chain thật với repository giả lập, không database) và `LoginIntegrationTests` (HTTP/login trên PostgreSQL, rollback lỗi lưu/ký/COMMIT, cạnh tranh khóa user, OpenAPI trong context thật). Nhóm không database dùng lệnh `.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests,UserSecurityTests,UserSecurityHttpTests" test` từ `backend/`.

Test context/persistence/login dùng khóa RSA ngẫu nhiên trong thư mục tạm, không dùng khóa dự án. `LoginIntegrationTests` chỉ bật khi system property chọn rõ `UserDB_test`; test có commit thật và dọn đúng user fixture bằng ID sau mỗi ca, khác với rollback fixture của test persistence. Không thêm database test tự động hoặc thay V1–V6. Kết quả chạy mới được ghi bên dưới; kết quả 2026-11-03 tiếp tục là lịch sử nền tảng trước UC02.

`FoundationTests` kiểm tra DTO/validation/exception bằng mapper ứng dụng và controller chỉ trong test; `ApiDocumentationTests` kiểm tra hạ tầng Swagger. Hai lớp này không đọc `.env`, không dùng datasource/Flyway. Chạy từ `backend/`:

```powershell
.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests" test
```

`PersistenceFoundationTests` và `UserServiceApplicationTests.contextLoads` tải Spring context cùng datasource/Flyway. Test persistence chỉ bật khi system property chọn rõ `UserDB_test`; fixture rollback. Chưa có Testcontainers hoặc mock email/Google; login đã có test endpoint riêng. Database test phải chuẩn bị trước; không chạy migration trên database dùng chung để kiểm tra docs. Xem [giới hạn rollback](foundation.md#kiểm-thử).

**Kết quả ngày 2026-11-03:** đã chạy toàn bộ 27 test bằng JDK 25/Maven Wrapper: 17 nền tảng, 5 persistence, 4 tài liệu và 1 context; không lỗi hoặc bỏ qua. Do `UserDB_test` tại cổng 5432 chưa tồn tại, kiểm thử dùng PostgreSQL 18.4 tạm biệt lập trong `backend/target/`, cổng riêng trên loopback và database có tên `UserDB_test`; bỏ import `.env`, dùng tài khoản fixture. Flyway V1–V6 và Hibernate validate đã chạy trên instance tạm; không dùng/thay đổi database dự án. Instance tạm được dừng sau kiểm thử. Đây là kết quả nền tảng, không phải xác nhận 20 API nghiệp vụ đã hoạt động.

**Kết quả ngày 2026-11-10 — UC02:** chạy toàn bộ **45 test**, không lỗi hoặc bỏ qua: 17 nền tảng, 5 persistence, 4 tài liệu, 8 crypto/biên phiên, 4 HTTP security, 6 login tích hợp và 1 context. Dùng JDK 25/Maven Wrapper, PostgreSQL 18.4 tạm tại loopback cổng 55432, dữ liệu `backend/target/uc02-postgres`, database `UserDB_test`, bỏ import `.env` và dùng tài khoản fixture. V1–V6 chạy thành công trên database mới, Hibernate validate 7 entity; lần cuối xác nhận schema vẫn V6. Test chứng minh rollback cả lỗi COMMIT database, login đồng thời và chờ khóa user rồi kiểm tra trạng thái mới. Khóa RSA của test tự sinh trong thư mục tạm; không đọc/thay khóa dự án. Không dùng database dự án. OpenAPI được kiểm tra bằng openapi-spec-validator; 23 Markdown/610 link và 84 block JSON hợp lệ, đủ 20 operation với 1 IMPLEMENTED/19 DESIGN_ONLY. Không coi kết quả này là kiểm thử 19 API còn lại.

## Kế hoạch kiểm thử khi triển khai

Instance PostgreSQL tạm của đợt UC02 ngày 2026-11-10 đã dừng sau kiểm thử; trước khi dừng đã xác nhận không còn user/session/refresh token fixture và đủ 6 migration thành công. Các mục chưa triển khai trong kế hoạch dưới đây dành cho đợt tiếp theo.

**Thứ tự ngày 2026-11-10:** [hoàn tất chức năng trước, bổ sung xác thực service sau](confirmed-decisions.md#deferred-service-auth). Trong giai đoạn chức năng, các service mặc định tin tưởng nhau; kiểm thử hai API nội bộ/introspection không yêu cầu Service JWT và vẫn kiểm tra user/phiên, ownership, actor ADMIN khi cần. Các ca danh tính/quyền service, Service JWT thiếu/sai/hết hạn và chặn caller không được phép trong kế hoạch dưới đây dành cho bước bổ sung xác thực service sau. Không đánh dấu đã hoàn tất bảo mật service từ kết quả test nghiệp vụ. Giữ lớp xác thực service tách khỏi logic nghiệp vụ/transaction để bổ sung sau.

| Nhóm | Hành vi cần chứng minh |
| --- | --- |
| Đăng ký/mã xác minh | Trùng sau chuẩn hóa, sai purpose, mã sai/hết hạn/đã dùng, cạnh tranh dùng cùng mã, hạn thử/gửi lại đã thống nhất |
| Đăng nhập | Local có/không password, ACTIVE/BANNED, lỗi thông tin đăng nhập, Google token sai và các nhánh liên kết |
| Refresh/logout | Rotation một lần, hai request cùng token, token hết hạn/thu hồi, logout lặp lại, khóa cạnh tranh refresh |
| Hồ sơ/mật khẩu | Chỉ sửa trường cho phép, không sửa user khác, đối chiếu password, thu hồi mọi phiên/refresh token theo luồng, giữ identity Google |
| API nội bộ | Xác thực service, phân quyền từng thao tác, chặn client trực tiếp, không lộ hash/token, user BANNED vẫn được tra cứu |
| Database | Migration từ DB rỗng và phiên bản trước, chuẩn hóa/unique, FK token, trigger UpdatedAt, rollback transaction khi lỗi |
| Hợp đồng | Schema request/response, HTTP status/messageCode và các biến thể Google đúng hợp đồng được xác nhận |

Khi triển khai, kiểm thử JWT RS256, Service JWT Gateway độc lập, quyền sở hữu phiên, UC11 từ chối phiên hiện tại, UC12 giữ phiên hiện tại và introspection sau thu hồi. Mở khóa không khôi phục phiên/token cũ. Kiểm thử tại mốc [session hết hạn](confirmed-decisions.md#session-lifetime) dù token còn hạn riêng, rotation không gia hạn session và LastSeenAt không quyết định hết hạn. Cơ chế kiểm tra phiên đã chốt; cấu hình Service JWT còn Q11a. Các ca cho endpoint chưa triển khai vẫn là kế hoạch tương lai; test login UC02 và nền tảng user security đã có, không thay thế test các API còn lại.

Đây là kế hoạch, không phải kết quả đã pass. Kiểm thử về constraint/transaction cần PostgreSQL phù hợp thay vì giả định database in-memory có cùng hành vi.

## Khi sửa tài liệu

Theo [nơi duy trì chính và quy ước bảo trì](documentation.md#noi-duy-tri-chinh), kiểm tra link tương đối/anchor, JSON, method/path, mapping UC/API và trạng thái ví dụ. Đối chiếu phiên bản/cấu hình với repo khi sửa hướng dẫn chạy. Không chạy ứng dụng, Maven hoặc chạm database chỉ để sửa Markdown. Khi được yêu cầu sửa schema/code, chạy kiểm thử phù hợp và chỉ ghi kết quả đã thực hiện; thay đổi thiết kế API trong Markdown không phải bằng chứng endpoint đã được kiểm thử.
