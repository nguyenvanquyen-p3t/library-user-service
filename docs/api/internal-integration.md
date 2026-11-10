# Tích hợp nội bộ

Trạng thái: **thiết kế, chưa có endpoint hoạt động**. Cập nhật cách trình bày: 2026-10-24. Nguồn: Excel Sheet1 hàng 18–19 và [quyết định đã xác nhận](../confirmed-decisions.md). Tài liệu này duy trì trách nhiệm và điều kiện tích hợp; request/response nằm tại bản API hiện hành.

**Đã xác nhận bổ sung ngày 2026-10-31:** [Q03](../confirmed-decisions.md#internal-service-auth) dùng Service JWT riêng cho Admin/Borrow, quyền từng service và header chứng minh actor Admin; [Q10](../confirmed-decisions.md#admin-status-contract) chốt retry/cạnh tranh/audit trạng thái. Gateway dùng Service JWT gọi [introspection](sessions.md#introspection-endpoint-rfc-7662). Cấu hình Service JWT tiếp tục **PENDING DECISION** tại [Q03](../open-decisions.md#q03)/[Q11a](../open-decisions.md#q11a). [User access token Q14](../confirmed-decisions.md#access-token-key-pair) đã chốt riêng; không dùng user token thay danh tính service.

## Giai đoạn chức năng: mặc định tin tưởng service

Theo [quyết định ngày 2026-11-10](../confirmed-decisions.md#deferred-service-auth), khi triển khai chức năng, Admin/Borrow/Gateway mặc định tin tưởng nhau. Hai API nội bộ và introspection chưa yêu cầu Service JWT hoặc xác minh danh tính/quyền service; không cần token service giả hay cấu hình bật riêng dev/test. Xác thực service sẽ được bổ sung sau khi hoàn tất tất cả chức năng.

| Operation | Điều kiện trong giai đoạn chức năng |
| --- | --- |
| GET user nội bộ | Không yêu cầu thông tin xác thực service; vẫn kiểm tra request và xử lý user không tồn tại |
| PATCH trạng thái | Không yêu cầu thông tin xác thực service; vẫn bắt buộc X-Admin-Access-Token, actor ACTIVE, phiên hợp lệ và role ADMIN hiện tại |
| POST introspection | Không yêu cầu thông tin xác thực service; vẫn xác minh user access token từ form, trạng thái user/phiên và roles hiện tại |

Admin/Borrow/Gateway vẫn có trách nhiệm nghiệp vụ như thiết kế; giai đoạn này chưa chứng minh danh tính service gọi hoặc thực thi quyền theo service. Thông tin service trong audit chỉ là ngữ cảnh chưa xác thực. Các kiểm tra actor, cấm khóa ADMIN, transaction/thu hồi phiên và lỗi nghiệp vụ/hạ tầng giữ nguyên. Chưa có endpoint được triển khai hoặc thay đổi security runtime trong lần cập nhật docs này.

## Xác thực và phân quyền service

Phần Service JWT/quyền service dưới đây là **thiết kế đích cho bước sau khi hoàn tất chức năng**. Trong giai đoạn chức năng, bỏ qua hai bước danh tính service/quyền service; kiểm tra actor Admin khi cần rồi xử lý nghiệp vụ. Giữ header X-Admin-Access-Token và xác minh actor ngay khi triển khai API trạng thái.

Admin/Borrow tự ký Service JWT bằng khóa riêng được User Service tin cậy, gửi trong `Authorization: Bearer <service-jwt>`. Chưa chốt thuật toán, issuer/audience/subject, TTL, tập claim hoặc cách phân phối/rotation khóa; không mặc định RS256/5 phút hoặc các giá trị mẫu trong đề xuất đã hoãn.

| Service | Quyền đã xác nhận |
| --- | --- |
| Borrow | users:read |
| Admin | users:read, users:status:write |
| Gateway | tokens:introspect |

User Service giới hạn quyền theo service đã được tin cậy, không cho tự nâng quyền bằng scope trong JWT. Kiểm tra API nội bộ theo thứ tự danh tính service → quyền service → actor Admin khi cần → nghiệp vụ; trả lỗi ở bước đầu tiên không đạt. Cách biểu diễn claim quyền trong JWT vẫn chưa chốt. Introspection giữ lỗi/header riêng.

## Tra cứu user

[GET /api/v1/internal/users/{id}](current-contract.md#internal-user-get) yêu cầu Service JWT của Borrow hoặc Admin với quyền users:read; không cho client Web/Android gọi trực tiếp. Tên “Service Token” trong Excel là nguồn lịch sử, không thay lựa chọn Service JWT đã chốt.

API trả cả user BANNED để service gọi tự kiểm tra nghiệp vụ. HTTP 200 chỉ xác nhận tra cứu thành công, không bảo đảm đủ điều kiện mượn. Borrow Service chịu trách nhiệm kiểm tra điều kiện nghiệp vụ từ status/roles và dữ liệu thuộc phạm vi của mình.

## Cập nhật trạng thái

[PATCH /api/v1/internal/users/{id}/status](current-contract.md#internal-user-status) yêu cầu Service JWT của Admin Service với quyền users:status:write và access token quản trị viên trong `X-Admin-Access-Token`. User Service xác minh user token, phiên, user ACTIVE và role ADMIN hiện tại từ database; không nhận actorId/role tự khai thay bằng chứng này. Admin Service khởi xướng và điều phối.

Chính sách [khóa/mở khóa](../confirmed-decisions.md#account-lifecycle) đã chốt. Ghi trạng thái và thu hồi phiên/token khi khóa phải cùng [transaction](../requirements.md#ranh-giới-transaction); mở khóa không phục hồi phiên/token cũ. Theo thiết kế introspection, access token của phiên bị thu hồi bị từ chối ở lần kiểm tra sau commit, không cam kết hủy request đã qua kiểm tra.

Theo [Q10 đã chốt](../confirmed-decisions.md#admin-status-contract), gửi lại trạng thái đang có trả 200 USER_STATUS_UPDATE_SUCCESS, không ghi đè thời điểm/lý do thu hồi cũ. Lệnh khóa/mở khóa cùng user khóa cùng bản ghi và xử lý tuần tự theo database. V1 cấm khóa đối tượng có role ADMIN, kể cả tự khóa, trả 403 ACCESS_DENIED.

Timeout chưa xác định được kết quả commit: Admin đọc lại user để đối soát, không tự retry lệnh trạng thái cũ; gửi lại sau đối soát là lệnh mới. Admin ghi audit yêu cầu/kết quả; User ghi sự kiện gồm service, actor, user đích, trạng thái trước/sau, thời gian và request ID, không ghi dữ liệu xác thực. Không thêm bảng hoặc cam kết audit log ngoài database nguyên tử với transaction.

## Quản lý role ngoài phạm vi V1

[Phạm vi role](../confirmed-decisions.md#roles) giữ nguyên: database hỗ trợ nhiều role; V1 không có API gán/thêm/gỡ/thay thế role. Không mở rộng API trạng thái hoặc API hồ sơ để xử lý role.

## Điều kiện hoàn tất hợp đồng

| Nhóm vấn đề | Cần chốt/đáp ứng trước triển khai |
| --- | --- |
| Danh tính và quyền | Giai đoạn chức năng mặc định tin tưởng service, vẫn xác minh actor theo Q14; chốt [Q03](../open-decisions.md#q03)/[Q11a](../open-decisions.md#q11a) trước bước bổ sung Service JWT sau khi hoàn tất chức năng |
| HTTP | Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi](http-errors.md), gồm lỗi service/actor ngày 2026-10-31; Q09 chỉ còn tình huống/mã ngoài danh mục |
| Retry và cạnh tranh | [Q10 đã chốt](../confirmed-decisions.md#admin-status-contract): trạng thái đích, tuần tự hóa theo user, đối soát timeout, audit và cấm khóa ADMIN; không bảo đảm exactly-once |
| Dữ liệu | Các writer trạng thái/login/refresh/password/thu hồi phải giữ [tính nhất quán](../requirements.md#tính-nhất-quán-khi-có-request-đồng-thời); không ghi trực tiếp UserDB từ service khác |

Kế hoạch kiểm thử khi được yêu cầu code chức năng: gọi nội bộ không có Service JWT, actor Admin thiếu/sai/không đủ quyền vẫn bị từ chối, user không tồn tại, tra cứu BANNED, khóa/mở khóa lặp, cạnh tranh khóa với login/refresh, không phục hồi phiên/token và introspection sau thu hồi. Kiểm thử danh tính/quyền service và chặn caller không được phép được thực hiện ở bước bổ sung Service JWT sau khi hoàn tất chức năng. Kiểm thử API quản lý role thuộc phiên bản mở rộng. Đây là kế hoạch, chưa phải kết quả kiểm thử.
