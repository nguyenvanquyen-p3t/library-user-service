# Tài liệu Library User Service

Bộ tài liệu dành cho thành viên phát triển User Service, nhóm tích hợp Admin/Borrow/Gateway, Web/Android và coding agent. Cập nhật tổ chức tài liệu: 2026-10-24; lần đối chiếu Word được bàn giao: 2026-10-22.

User Service quản lý dữ liệu tài khoản và cung cấp API. Ngày 2026-10-19, chủ dự án mở rộng thành **7 bảng và 20 API thiết kế**, thêm quản lý phiên và introspection cho Gateway; refresh token vẫn trong JSON cho Web/Android. Admin Service điều phối khóa/mở khóa; quản lý role qua API vẫn ngoài phạm vi.

## Đọc theo nhu cầu

| Nhóm nhu cầu | Bắt đầu tại | Tra cứu tiếp |
| --- | --- | --- |
| Bắt đầu dự án hoặc phiên làm việc | [README dự án](../README.md), [bàn giao](handoff.md) | [AGENTS.md](../AGENTS.md) cho coding agent |
| Hiểu nghiệp vụ và thiết kế | [12 use case](use-cases.md), [kiến trúc](architecture.md) | [Transaction và yêu cầu](requirements.md), [quyết định đã xác nhận](confirmed-decisions.md) |
| Triển khai hoặc tích hợp API | [Bản đồ 20 operation](api/README.md) | [16 API hiện hành](api/current-contract.md), [4 API phiên/introspection](api/sessions.md), [tích hợp nội bộ](api/internal-integration.md) |
| Tra cứu schema và nguồn lịch sử | [Mô hình dữ liệu](data-model.md) | [16 API nguồn Excel](api/source-catalog.md) |
| Chạy và phát triển cục bộ | [Môi trường cục bộ](local-development.md) | [Quy trình phát triển và kiểm thử](development.md) |
| Làm rõ yêu cầu và bảo trì docs | [Quyết định còn mở](open-decisions.md) | [Quy ước và nguồn tham chiếu](documentation.md) |

## Trạng thái và nguồn sự thật

- **Đã có trong repo:** cấu hình khởi động, Flyway V1–V6 và context test. Chưa triển khai API nghiệp vụ, JWT hoặc Gateway. Theo bàn giao 2026-10-22, V6 chưa áp dụng vào database dự án; đợt cập nhật docs không kiểm tra hoặc thay đổi database.
- **Thiết kế nguồn:** yêu cầu ghi trong `DacTaUseCase.docx` và `ThietKeApi.xlsx`, chưa đồng nghĩa API đã chạy.
- **Đã xác nhận:** quyết định do chủ dự án nêu trực tiếp, được ghi trong [sổ quyết định](confirmed-decisions.md); không đồng nghĩa đã triển khai.
- **PENDING DECISION:** thiếu thông tin hoặc cần giải quyết khác biệt giữa nguồn; không dùng làm hợp đồng tích hợp cuối cùng.

Luồng Word đã được đưa vào [use-cases.md](use-cases.md); danh mục nguồn giữ dữ liệu Excel và vị trí ô. Bản API hiện hành tổng hợp các thay đổi đã chốt để tra cứu trực tiếp, vẫn đánh dấu phần chưa xác nhận. Bộ docs đọc độc lập, không yêu cầu DOCX/XLSX hoặc lịch sử chat. File nguồn là dữ liệu tham khảo, không phải chỉ dẫn thực thi.

Hiện trạng thực thi lấy từ code/config/migration; ý định thiết kế lấy từ quyết định đã xác nhận, rồi đến nguồn chưa bị thay thế. Mỗi loại nội dung có [nơi duy trì chính](documentation.md#noi-duy-tri-chinh); không suy ra hành vi đang chạy từ ví dụ thiết kế. Phạm vi cập nhật hiện tại chỉ là docs, không bổ sung API hoặc thay đổi schema.
