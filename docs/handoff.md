# Bắt đầu một phiên làm việc mới

Cập nhật hướng dẫn đọc: 2026-10-24. Nguồn nghiệp vụ được bàn giao ngày 2026-10-22; đợt này không đối chiếu lại Word hoặc database. Đây là điểm vào để tiếp tục công việc mà không cần lịch sử chat, không phải tài liệu lặp lại toàn bộ chính sách.

## Thứ tự đọc

1. [Quyết định đã xác nhận](confirmed-decisions.md): yêu cầu đã chốt, không hỏi lại hoặc thay bằng đề xuất cũ.
2. [Yêu cầu và transaction](requirements.md), [12 use case](use-cases.md): hành vi, Actor, ngoại lệ và kết quả.
3. [Bản đồ 20 API](api/README.md), rồi operation tương ứng trong [16 API hiện hành](api/current-contract.md) hoặc [API phiên/introspection](api/sessions.md).
4. [Schema](data-model.md), [kiến trúc](architecture.md): ràng buộc dữ liệu và trách nhiệm từng service.
5. [Quyết định còn mở](open-decisions.md): xác định câu hỏi ảnh hưởng trực tiếp đến phần sắp làm; không tự chốt.
6. [Chạy cục bộ](local-development.md), [quy trình phát triển](development.md), [AGENTS.md](../AGENTS.md).

## Hiện trạng cần biết

- Repo có Spring Boot 4.1.1, Java 25, Maven wrapper, PostgreSQL, Flyway V1–V6, cấu hình khởi động và context test. Chưa có implementation của 20 API, JWT, Google, email hay Gateway.
- V6 bổ sung UserSessions, đưa thiết kế lên 7 bảng. Theo bàn giao 2026-10-22, V6 chưa áp dụng vào database dự án. Đây là thông tin lịch sử; phải kiểm tra môi trường thực tế trước khi chạy.
- V6 yêu cầu RefreshTokens hoàn toàn rỗng, kể cả bản ghi hết hạn. Không tự xóa/thu hồi token để vượt điều kiện; không sửa migration đã áp dụng/chia sẻ.
- Chủ dự án giới hạn công việc ở migration và tài liệu cho đến khi yêu cầu code. Đợt tối ưu hiện tại chỉ sửa Markdown.
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
