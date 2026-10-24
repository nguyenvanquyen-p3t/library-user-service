# Bắt đầu một phiên làm việc mới

Đối chiếu repo và đặc tả ngày 2026-10-22. Đọc tài liệu này trước khi đề xuất hoặc viết code; không cần lịch sử chat hay file Word. Đây là bản bàn giao thiết kế, không phải tuyên bố dự án đã hoàn tất triển khai.

## Thứ tự đọc

1. [Quyết định đã xác nhận](confirmed-decisions.md): yêu cầu đã chốt, không hỏi lại hoặc thay bằng đề xuất cũ.
2. [Yêu cầu và transaction](requirements.md), [12 use case](use-cases.md): hành vi, Actor, ngoại lệ và kết quả.
3. [Bản đồ 20 API](api/README.md), rồi tài liệu hợp đồng được liên kết ở đó.
4. [Schema](data-model.md), [kiến trúc](architecture.md): ràng buộc dữ liệu và trách nhiệm từng service.
5. [Quyết định còn mở](open-decisions.md): xác định câu hỏi ảnh hưởng trực tiếp đến phần sắp làm; không tự chốt.
6. [Chạy cục bộ](local-development.md), [quy trình phát triển](development.md), [AGENTS.md](../AGENTS.md).

## Hiện trạng cần biết

- Repo có Spring Boot 4.1.1, Java 25, Maven wrapper, PostgreSQL, Flyway V1–V6, cấu hình khởi động và context test. Chưa có implementation của 20 API, JWT, Google, email hay Gateway.
- V6 bổ sung UserSessions, đưa thiết kế lên 7 bảng. Có file migration không đồng nghĩa đã áp dụng lên database: theo lần bàn giao này V6 chưa áp dụng vào database dự án. Kiểm tra môi trường thực tế trước khi chạy.
- V6 yêu cầu RefreshTokens hoàn toàn rỗng, kể cả bản ghi hết hạn. Không tự xóa/thu hồi token để vượt điều kiện; không sửa migration đã áp dụng/chia sẻ.
- Chỉ được làm migration và tài liệu cho đến khi chủ dự án yêu cầu code. Yêu cầu tối ưu docs không phải cho phép triển khai API hoặc đổi dependency.
- Lệnh backend chạy từ `backend/` vì `.env` được tìm theo thư mục làm việc; cổng cấu hình là 5001. Context test sử dụng datasource và Flyway thật, không chạy tùy tiện trên database dự án.

Đối chiếu implementation tại [pom.xml](../backend/pom.xml), [application.yml](../backend/src/main/resources/application.yml), [migrations](../backend/src/main/resources/db/migration) và [context test](../backend/src/test/java/com/library/userservice/UserServiceApplicationTests.java). Nếu cấu trúc code đã thay đổi, cập nhật mục hiện trạng thay vì coi thiết kế là code đang chạy.

## Các ranh giới dễ hiểu nhầm

- Local signup tạo ACTIVE + READER sau OTP, chưa đăng nhập; Google hoàn tất đăng ký tạo ACTIVE + READER và phiên. Nhiều role được hỗ trợ nhưng không có API quản lý role.
- Access token RS256 có sid, hạn 1 giờ; refresh token opaque trả JSON, hạn 7 ngày mỗi lần cấp; session cố định 30 ngày, không gia hạn. Hết session thì token không dùng được dù còn hạn riêng.
- Gateway dùng Service JWT riêng gọi introspection. Thu hồi refresh token đơn thuần không vô hiệu JWT; phải kiểm tra trạng thái phiên. Không Redis hoặc JWT blacklist.
- Reuse ở UC03 chỉ từ chối token cũ, không thu hồi cả phiên. UC04 vẫn cho dùng token cũ còn bản ghi để xác định và thu hồi đúng phiên.
- UC11 chỉ thu hồi một phiên khác; UI không cho chọn phiên hiện tại, server vẫn kiểm tra. UC12 giữ phiên hiện tại. Đăng xuất bản thân dùng UC04.
- Đổi/reset/thiết lập mật khẩu thu hồi mọi phiên kể cả phiên gọi. Khóa do Admin Service điều phối; mở khóa không phục hồi phiên/token.
- registrationToken Google 10 phút; chống đăng ký lặp dựa trên UNIQUE identity, không phải kho token đã dùng. Google tự liên kết vào tài khoản Local cùng email đã được Google xác minh, không hỏi lại mật khẩu Local.
- Chính sách OTP/mật khẩu và retention đã chốt nằm trong sổ quyết định. Không tự thêm kiểm tra mật khẩu phổ biến, giới hạn IP hoặc mở lại các lựa chọn chủ dự án đã bỏ.

## Khi bắt đầu phát triển

Xác định UC/API được yêu cầu, đọc quyết định và transaction liên quan, rồi kiểm tra code hiện hữu. Chỉ hỏi những mục PENDING DECISION ảnh hưởng đến công việc đó; không mở rộng 7 bảng/20 API để tự giải quyết khoảng trống. Hợp đồng API nguồn có ví dụ lịch sử; dùng phần đối chiếu trong bản đồ API để tránh triển khai TTL hoặc logout cũ.

Khi được yêu cầu triển khai, cập nhật trạng thái từng phần từ thiết kế sang đã triển khai kèm kiểm tra thực sự đã chạy. Không đánh dấu toàn bộ API hoàn tất vì build hoặc context test thành công. Tài liệu hiện còn quyết định chưa chốt nên chưa thể suy ra một hợp đồng sản xuất hoàn chỉnh chỉ từ các ví dụ JSON.
