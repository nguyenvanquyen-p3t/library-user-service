# Hướng dẫn phát triển và kiểm thử

## Bắt đầu

Theo [README dự án](../README.md) để bắt đầu nhanh hoặc [hướng dẫn môi trường cục bộ](local-development.md) để cấu hình, đóng gói và xử lý lỗi. Cần JDK 25, PostgreSQL 15+ và `backend/.env`. Lệnh Maven phải chạy từ `backend/` để import `.env` đúng vị trí. Không lưu mật khẩu thực vào docs hoặc commit `.env`.

## Quy trình thay đổi

1. Đọc [kiến trúc](architecture.md), [quyết định đã xác nhận](confirmed-decisions.md) và use case liên quan. Phạm vi thiết kế là 7 bảng/20 API, có session revocation; không có API quản lý role, JWT blacklist hoặc Redis. Hiện chỉ thêm migration và docs, chưa code API. Hỏi chủ dự án trước khi giải quyết yêu cầu chưa rõ.
2. Đối chiếu endpoint với [danh mục nguồn](api/source-catalog.md). Chốt hợp đồng request, response, auth và lỗi trước khi nhóm khác tích hợp.
3. Viết code và kiểm thử hành vi; thêm migration mới nếu cần thay schema đã được chia sẻ.
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
| Hồ sơ/mật khẩu | Chỉ sửa trường cho phép, không sửa user khác, đối chiếu password, thu hồi refresh token, giữ identity Google |
| API nội bộ | Xác thực service, phân quyền từng thao tác, chặn client trực tiếp, không lộ hash/token, user BANNED vẫn được tra cứu |
| Database | Migration từ DB rỗng và phiên bản trước, chuẩn hóa/unique, FK token, trigger UpdatedAt, rollback transaction khi lỗi |
| Hợp đồng | Schema request/response, HTTP status/messageCode và các biến thể Google đúng hợp đồng được xác nhận |

Khi triển khai, kiểm thử JWT RS256, Service JWT Gateway độc lập, quyền sở hữu phiên, giữ phiên hiện tại và introspection sau thu hồi. Mở khóa không được khôi phục phiên/token cũ. TTL access token 1 giờ và refresh token 7 ngày đã chốt; Session cố định 30 ngày đã chốt; chi tiết Service JWT còn chờ chốt. Cần kiểm thử tại mốc session hết hạn dù token còn hạn riêng, rotation không gia hạn session và LastSeenAt không quyết định hết hạn. Đây là kế hoạch kiểm thử tương lai, chưa có test API trong repo.

Đây là kế hoạch, không phải kết quả đã pass. Kiểm thử về constraint/transaction cần PostgreSQL phù hợp thay vì giả định database in-memory có cùng hành vi.

## Khi sửa tài liệu

Kiểm tra link tương đối, ví dụ JSON, tên endpoint, phiên bản trong pom và cấu hình thực tế. Không cần chạy ứng dụng hoặc chạm database chỉ để sửa Markdown. Nếu sửa API/schema/code, chạy các kiểm thử liên quan và ghi lại lệnh/kết quả thực tế.
