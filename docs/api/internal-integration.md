# Tích hợp nội bộ

Trạng thái: **thiết kế, chưa có endpoint hoạt động**. Nguồn: `ThietKeApi.xlsx`, Sheet1 hàng 18–19 và [quyết định đã xác nhận](../confirmed-decisions.md).

**Đã chốt:** Gateway dùng Service JWT gọi [introspection](sessions.md). **PENDING DECISION:** hợp đồng xác thực riêng của Admin Service/Borrow Service và chi tiết Service JWT. Không dùng access token người dùng làm danh tính Gateway.

## Tra cứu user

`GET /api/v1/internal/users/{id}` phục vụ Borrow Service và service được cấp quyền. `id` là User ID kiểu Integer; không có request body. Thiết kế yêu cầu Service Token và không cho client Web/Android gọi trực tiếp.

Thành công HTTP 200:

```json
{
  "success": true,
  "messageCode": "USER_GET_SUCCESS",
  "message": "Lấy thông tin người dùng thành công",
  "data": {
    "id": 3,
    "userName": "reader01",
    "fullName": "Nguyen Van A",
    "status": "ACTIVE",
    "roles": ["READER"]
  }
}
```

API vẫn trả user BANNED để service gọi tự kiểm tra nghiệp vụ. Các lỗi được liệt kê trong Excel: 400, 401, 403, 404. Hợp đồng lỗi chi tiết chưa có. Borrow Service không được suy diễn HTTP 200 là đủ điều kiện mượn.

## Cập nhật trạng thái

`PATCH /api/v1/internal/users/{id}/status` chỉ phục vụ Admin Service được cấp quyền. Thiết kế yêu cầu Service Token và kiểm tra quyền thao tác Admin.

```json
{ "status": "BANNED" }
```

Chỉ nhận ACTIVE hoặc BANNED. Khóa ACTIVE → BANNED thu hồi toàn bộ refresh token còn hiệu lực; BANNED không được đăng nhập hoặc refresh. Mở khóa/kích hoạt lại BANNED → ACTIVE không khôi phục token đã thu hồi và yêu cầu đăng nhập lại. Không có trạng thái chờ hoặc endpoint kích hoạt riêng.

Thu hồi refresh token không tự vô hiệu JWT. Thiết kế mở rộng yêu cầu thu hồi phiên khi khóa và Gateway introspection kiểm tra trạng thái phiên; chưa có implementation. Không dùng JWT blacklist hoặc Redis.

Thành công HTTP 200:

```json
{
  "success": true,
  "messageCode": "USER_STATUS_UPDATE_SUCCESS",
  "message": "Cập nhật trạng thái tài khoản thành công",
  "data": { "id": 3, "status": "BANNED" }
}
```

Lỗi được liệt kê: 400, 401, 403, 404, 422. Excel chưa quy định gọi lại cùng trạng thái, xử lý hai lệnh ngược nhau đồng thời, actor audit hoặc chính sách khóa tài khoản admin.

## Quản lý role ngoài phạm vi V1

Phạm vi mới gồm 7 bảng/20 API, không có API gán/thêm/gỡ/thay thế role. Database hỗ trợ nhiều role, kể cả ADMIN cùng role khác. Đăng ký mới mặc định READER; không cho tự nâng quyền.

Nghiệp vụ thay thế danh sách hay thêm/gỡ role và chính sách access token sau đổi role là **PENDING DECISION cho phiên bản mở rộng**. Không tự tạo endpoint hoặc thay database để phục vụ tính năng này.

## Điều kiện hoàn tất hợp đồng

Trước khi triển khai tích hợp, hai nhóm cần chốt danh tính service, cách truyền và xác minh Service Token, cách chứng minh người quản trị, phạm vi quyền từng service, mã lỗi và các hành vi đồng thời. Không tin role hoặc actor từ header tùy ý do client gửi.

Kế hoạch kiểm thử V1 bao gồm: thiếu/sai thông tin xác thực theo cơ chế sẽ chốt, service hợp lệ nhưng thiếu quyền, client gọi API nội bộ, user không tồn tại, khóa/mở khóa lặp lại, không khôi phục refresh token, BANNED không đăng nhập/refresh và cạnh tranh khóa với refresh. Kiểm thử từ chối access token toàn hệ thống phụ thuộc quyết định còn mở. Kiểm thử quản lý role dành cho phiên bản mở rộng. Đây là kế hoạch, chưa có trong repo.
