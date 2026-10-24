# Tích hợp nội bộ

Trạng thái: **thiết kế, chưa có endpoint hoạt động**. Cập nhật cách trình bày: 2026-10-24. Nguồn: Excel Sheet1 hàng 18–19 và [quyết định đã xác nhận](../confirmed-decisions.md). Tài liệu này duy trì trách nhiệm và điều kiện tích hợp; request/response nằm tại bản API hiện hành.

**Đã chốt:** Gateway dùng Service JWT gọi [introspection](sessions.md#introspection-endpoint-rfc-7662). **PENDING DECISION:** xác thực riêng Admin/Borrow tại [Q03](../open-decisions.md#q03), cấu hình Service JWT Gateway tại [Q11a](../open-decisions.md#q11a). Không dùng access token người dùng làm danh tính Gateway hoặc tự áp dụng hợp đồng Gateway cho service khác.

## Tra cứu user

[GET /api/v1/internal/users/{id}](current-contract.md#internal-user-get) phục vụ Borrow Service và service được cấp quyền; không cho client Web/Android gọi trực tiếp. Tên “Service Token” trong nguồn chưa lựa chọn giao thức xác thực.

API trả cả user BANNED để service gọi tự kiểm tra nghiệp vụ. HTTP 200 chỉ xác nhận tra cứu thành công, không bảo đảm đủ điều kiện mượn. Borrow Service chịu trách nhiệm kiểm tra điều kiện nghiệp vụ từ status/roles và dữ liệu thuộc phạm vi của mình.

## Cập nhật trạng thái

[PATCH /api/v1/internal/users/{id}/status](current-contract.md#internal-user-status) chỉ dành cho Admin Service được cấp quyền. User Service xác thực service và quyền/actor theo hợp đồng sẽ chốt, rồi thực hiện thay đổi dữ liệu; Admin Service khởi xướng và điều phối.

Chính sách [khóa/mở khóa](../confirmed-decisions.md#account-lifecycle) đã chốt. Ghi trạng thái và thu hồi phiên/token khi khóa phải cùng [transaction](../requirements.md#ranh-giới-transaction); mở khóa không phục hồi phiên/token cũ. Theo thiết kế introspection, access token của phiên bị thu hồi bị từ chối ở lần kiểm tra sau commit, không cam kết hủy request đã qua kiểm tra.

## Quản lý role ngoài phạm vi V1

[Phạm vi role](../confirmed-decisions.md#roles) giữ nguyên: database hỗ trợ nhiều role; V1 không có API gán/thêm/gỡ/thay thế role. Không mở rộng API trạng thái hoặc API hồ sơ để xử lý role.

## Điều kiện hoàn tất hợp đồng

| Nhóm vấn đề | Cần chốt/đáp ứng trước triển khai |
| --- | --- |
| Danh tính và quyền | [Q03](../open-decisions.md#q03): danh tính service, cách truyền/xác minh Service Token, chứng minh actor Admin và phạm vi quyền. Không tin actor/role từ header tùy ý |
| HTTP | [Q09](../open-decisions.md#q09): schema, validation, HTTP status và response lỗi; danh sách lỗi nguồn chưa phải hợp đồng đầy đủ |
| Retry và cạnh tranh | [Q10](../open-decisions.md#q10): gọi lại cùng trạng thái, lệnh ngược nhau đồng thời, timeout, audit và chính sách khóa admin. Không tự chọn last-write-wins hoặc bảo đảm exactly-once |
| Dữ liệu | Các writer trạng thái/login/refresh/password/thu hồi phải giữ [tính nhất quán](../requirements.md#tính-nhất-quán-khi-có-request-đồng-thời); không ghi trực tiếp UserDB từ service khác |

Kế hoạch kiểm thử khi được yêu cầu code: xác thực thiếu/sai, service thiếu quyền, client gọi trực tiếp API nội bộ, user không tồn tại, tra cứu BANNED, khóa/mở khóa lặp, cạnh tranh khóa với login/refresh, không phục hồi phiên/token và introspection sau thu hồi. Cơ chế kiểm tra phiên đã chốt; ca kiểm thử cụ thể cần cấu hình/xác thực hoàn chỉnh và implementation. Kiểm thử role thuộc phiên bản mở rộng. Đây là kế hoạch, chưa phải kết quả kiểm thử.
