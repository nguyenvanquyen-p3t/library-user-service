# Hướng dẫn phát triển và kiểm thử

Biên tập ngày 2026-10-24. Đợt tối ưu hiện tại chỉ sửa Markdown; các bước code và kiểm thử API bên dưới chỉ áp dụng khi chủ dự án yêu cầu triển khai.

## Bắt đầu

Theo [README dự án](../README.md) để bắt đầu nhanh hoặc [hướng dẫn môi trường cục bộ](local-development.md) để cấu hình, đóng gói và xử lý lỗi. Cần JDK 25, PostgreSQL 15+ và `backend/.env`. Lệnh Maven phải chạy từ `backend/` để import `.env` đúng vị trí. Không lưu mật khẩu thực vào docs hoặc commit `.env`.

## Quy trình thay đổi

1. Đọc [kiến trúc](architecture.md), [quyết định đã xác nhận](confirmed-decisions.md) và use case liên quan. Phạm vi thiết kế là 7 bảng/20 API, có session revocation; không có API quản lý role, JWT blacklist hoặc Redis. Hiện chỉ thêm migration và docs, chưa code API. Hỏi chủ dự án trước khi giải quyết yêu cầu chưa rõ.
2. Tra cứu operation tại [bản đồ API](api/README.md) và bản hiện hành được liên kết; danh mục nguồn chỉ để truy vết Excel. Chốt phần request/response/auth/lỗi liên quan còn PENDING DECISION trước khi tích hợp.
3. Khi được yêu cầu triển khai, viết code và kiểm thử hành vi; thêm migration mới nếu cần thay schema đã được chia sẻ. Với yêu cầu docs-only, chỉ sửa và kiểm tra tài liệu.
4. Cập nhật trạng thái docs theo phần thực sự hoàn thành. Ghi rõ kiểm thử đã chạy và giới hạn môi trường trong PR.

Hiện chưa có cấu trúc tầng nghiệp vụ để tuyên bố controller/service/repository là kiến trúc đã triển khai. Khi thêm, giữ logic nghiệp vụ và transaction tách khỏi xử lý HTTP, không trả entity chứa hash ra response.

## Kiểm thử hiện có

`UserServiceApplicationTests.contextLoads` tải Spring context cùng datasource/Flyway. Chưa có profile test, Testcontainers, mock email/Google hoặc kiểm thử API tự động. Dùng database test riêng như README hướng dẫn; không chạy thử migration trên database dùng chung để kiểm tra docs.

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
