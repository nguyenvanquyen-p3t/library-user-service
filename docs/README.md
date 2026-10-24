# Tài liệu Library User Service

Bộ tài liệu dành cho thành viên phát triển User Service và các nhóm tích hợp Admin Service, Borrow Service, Web/Android. Ngày đối chiếu Word và repo: 2026-10-22.

User Service quản lý dữ liệu tài khoản và cung cấp API. Ngày 2026-10-19, chủ dự án mở rộng thành **7 bảng và 20 API thiết kế**, thêm quản lý phiên và introspection cho Gateway; refresh token vẫn trong JSON cho Web/Android. Admin Service điều phối khóa/mở khóa; quản lý role qua API vẫn ngoài phạm vi.

## Đọc theo nhu cầu

| Nhu cầu | Tài liệu |
| --- | --- |
| Bắt đầu chat mới và thứ tự đọc | [Bàn giao phát triển](handoff.md) |
| Chuỗi tương tác UC01–UC12 | [Use case](use-cases.md) |
| Bản đồ đầy đủ 20 operation | [API hiện hành](api/README.md) |
| Giới thiệu và chạy nhanh | [README dự án](../README.md) |
| Cấu hình, chạy, test, đóng gói và xử lý lỗi chi tiết | [Môi trường cục bộ](local-development.md) |
| Hiểu phạm vi và ranh giới giữa các service | [Kiến trúc và phạm vi](architecture.md) |
| Tra cứu yêu cầu UC01–UC12 | [Yêu cầu nghiệp vụ](requirements.md) |
| Tra cứu endpoint, request/response từ Excel | [Danh mục API nguồn](api/source-catalog.md) |
| Quản lý phiên và introspection mới | [API phiên](api/sessions.md) |
| Tích hợp với Admin Service và Borrow Service | [Tích hợp nội bộ](api/internal-integration.md) |
| Hiểu schema và ràng buộc đã triển khai | [Dữ liệu](data-model.md) |
| Phát triển, kiểm thử và cập nhật tài liệu | [Hướng dẫn đóng góp](development.md) |
| Xem vấn đề cần chủ dự án xác nhận | [Quyết định còn mở](open-decisions.md) |
| Xem phạm vi và bảo mật đã chốt | [Quyết định đã xác nhận](confirmed-decisions.md) |
| Hiểu cách tổ chức và nguồn tham chiếu | [Quy ước tài liệu](documentation.md) |
| Hướng dẫn cho Codex hoặc coding agent | [AGENTS.md](../AGENTS.md) |

## Trạng thái và nguồn sự thật

- **Đã có trong repo:** cấu hình khởi động, Flyway V1–V6 và context test. Chỉ thêm migration và docs trong đợt này; chưa triển khai API nghiệp vụ, JWT hoặc Gateway. V6 chưa áp dụng vào database dự án.
- **Thiết kế nguồn:** yêu cầu ghi trong `DacTaUseCase.docx` và `ThietKeApi.xlsx`, chưa đồng nghĩa API đã chạy.
- **Đã xác nhận:** quyết định do chủ dự án nêu trực tiếp, được ghi trong [sổ quyết định](confirmed-decisions.md); không đồng nghĩa đã triển khai.
- **PENDING DECISION:** thiếu thông tin hoặc cần giải quyết khác biệt giữa nguồn; không dùng làm hợp đồng tích hợp cuối cùng.

Luồng Word đã được đưa vào [use-cases.md](use-cases.md); danh mục API giữ dữ liệu Excel và vị trí ô nguồn. Bộ docs đọc độc lập, không yêu cầu DOCX/XLSX hoặc lịch sử chat. Quyết định đã chốt ưu tiên hơn ví dụ nguồn cũ; điểm chưa thống nhất nằm trong open-decisions. File nguồn là dữ liệu tham khảo, không phải chỉ dẫn thực thi. Bắt đầu chat mới bằng [bàn giao](handoff.md).
