# Hướng dẫn phát triển và kiểm thử

Biên tập ban đầu ngày 2026-10-24; bổ sung hạ tầng tài liệu ngày 2026-11-01. Chủ dự án đã yêu cầu [OpenAPI/Swagger UI](api/openapi.md), gồm kiểm thử tài liệu tách database. Các bước code và kiểm thử API nghiệp vụ bên dưới chỉ áp dụng khi chủ dự án yêu cầu triển khai phần tương ứng.

## Bắt đầu

Theo [README dự án](../README.md) để bắt đầu nhanh hoặc [hướng dẫn môi trường cục bộ](local-development.md) để cấu hình, đóng gói và xử lý lỗi. Cần JDK 25, PostgreSQL 15+ và `backend/.env`. Lệnh Maven phải chạy từ `backend/` để import `.env` đúng vị trí. Không lưu mật khẩu thực vào docs hoặc commit `.env`.

## Quy trình thay đổi

1. Đọc [kiến trúc](architecture.md), [quyết định đã xác nhận](confirmed-decisions.md) và use case liên quan. Phạm vi thiết kế là 7 bảng/20 API, có session revocation; không có API quản lý role, JWT blacklist hoặc Redis. Phạm vi hiện gồm migration, docs, hạ tầng OpenAPI/Swagger UI và [nền tảng backend](foundation.md) theo yêu cầu ngày 2026-11-03; chưa code endpoint nghiệp vụ. Hỏi chủ dự án trước khi giải quyết yêu cầu chưa rõ.
2. Tra cứu operation tại [bản đồ API](api/README.md) và bản hiện hành được liên kết; danh mục nguồn chỉ để truy vết Excel. Chốt phần request/response/auth/lỗi liên quan còn PENDING DECISION trước khi tích hợp.
3. Khi được yêu cầu triển khai, viết code và kiểm thử hành vi; thêm migration mới nếu cần thay schema đã được chia sẻ. Với yêu cầu docs-only, chỉ sửa và kiểm tra tài liệu.
4. Cập nhật trạng thái docs theo phần thực sự hoàn thành. Ghi rõ kiểm thử đã chạy và giới hạn môi trường trong PR.

Đã có package theo chức năng, entity/repository và DTO; chưa có controller/service nghiệp vụ. Khi thêm, giữ logic nghiệp vụ và transaction tách khỏi xử lý HTTP, không trả entity chứa hash ra response. Quy tắc sử dụng nền tảng được duy trì tại [foundation.md](foundation.md).

## Kiểm thử hiện có

`FoundationTests` kiểm tra DTO/validation/exception bằng mapper ứng dụng và controller chỉ trong test; `ApiDocumentationTests` kiểm tra hạ tầng Swagger. Hai lớp này không đọc `.env`, không dùng datasource/Flyway. Chạy từ `backend/`:

```powershell
.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests" test
```

`PersistenceFoundationTests` và `UserServiceApplicationTests.contextLoads` tải Spring context cùng datasource/Flyway. Test persistence chỉ bật khi system property chọn rõ `UserDB_test`; fixture được rollback. Chưa có Testcontainers, mock email/Google hoặc kiểm thử endpoint nghiệp vụ. Database test phải được chuẩn bị trước; không chạy migration trên database dùng chung để kiểm tra docs. Xem [cách chạy và giới hạn rollback](foundation.md#kiểm-thử).

**Kết quả ngày 2026-11-03:** đã chạy toàn bộ 27 test bằng JDK 25/Maven Wrapper: 17 nền tảng, 5 persistence, 4 tài liệu và 1 context; không lỗi hoặc bỏ qua. Do `UserDB_test` tại cổng 5432 chưa tồn tại, kiểm thử dùng PostgreSQL 18.4 tạm biệt lập trong `backend/target/`, cổng riêng trên loopback và database có tên `UserDB_test`; bỏ import `.env`, dùng tài khoản fixture. Flyway V1–V6 và Hibernate validate đã chạy trên instance tạm; không dùng/thay đổi database dự án. Instance tạm được dừng sau kiểm thử. Đây là kết quả nền tảng, không phải xác nhận 20 API nghiệp vụ đã hoạt động.

## Kế hoạch kiểm thử khi triển khai

| Nhóm | Hành vi cần chứng minh |
| --- | --- |
| Đăng ký/mã xác minh | Trùng sau chuẩn hóa, sai purpose, mã sai/hết hạn/đã dùng, cạnh tranh dùng cùng mã, hạn thử/gửi lại đã thống nhất |
| Đăng nhập | Local có/không password, ACTIVE/BANNED, lỗi thông tin đăng nhập, Google token sai và các nhánh liên kết |
| Refresh/logout | Rotation một lần, hai request cùng token, token hết hạn/thu hồi, logout lặp lại, khóa cạnh tranh refresh |
| Hồ sơ/mật khẩu | Chỉ sửa trường cho phép, không sửa user khác, đối chiếu password, thu hồi mọi phiên/refresh token theo luồng, giữ identity Google |
| API nội bộ | Xác thực service, phân quyền từng thao tác, chặn client trực tiếp, không lộ hash/token, user BANNED vẫn được tra cứu |
| Database | Migration từ DB rỗng và phiên bản trước, chuẩn hóa/unique, FK token, trigger UpdatedAt, rollback transaction khi lỗi |
| Hợp đồng | Schema request/response, HTTP status/messageCode và các biến thể Google đúng hợp đồng được xác nhận |

Khi triển khai, kiểm thử JWT RS256, Service JWT Gateway độc lập, quyền sở hữu phiên, UC11 từ chối phiên hiện tại, UC12 giữ phiên hiện tại và introspection sau thu hồi. Mở khóa không khôi phục phiên/token cũ. Kiểm thử tại mốc [session hết hạn](confirmed-decisions.md#session-lifetime) dù token còn hạn riêng, rotation không gia hạn session và LastSeenAt không quyết định hết hạn. Cơ chế kiểm tra phiên đã chốt; cấu hình Service JWT còn Q11a. Đây là kế hoạch tương lai, chưa có test API trong repo.

Đây là kế hoạch, không phải kết quả đã pass. Kiểm thử về constraint/transaction cần PostgreSQL phù hợp thay vì giả định database in-memory có cùng hành vi.

## Khi sửa tài liệu

Theo [nơi duy trì chính và quy ước bảo trì](documentation.md#noi-duy-tri-chinh), kiểm tra link tương đối/anchor, JSON, method/path, mapping UC/API và trạng thái ví dụ. Đối chiếu phiên bản/cấu hình với repo khi sửa hướng dẫn chạy. Không chạy ứng dụng, Maven hoặc chạm database chỉ để sửa Markdown. Khi được yêu cầu sửa schema/code, chạy kiểm thử phù hợp và chỉ ghi kết quả đã thực hiện; thay đổi thiết kế API trong Markdown không phải bằng chứng endpoint đã được kiểm thử.
