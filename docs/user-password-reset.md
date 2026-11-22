# Quên và đặt lại mật khẩu UC05

Triển khai ngày 2026-11-22. Hai API công khai nhận JSON và trả envelope chung với `Cache-Control: no-store`. Người dùng có thể gọi API khi phiên cũ đã hết hiệu lực. [Hợp đồng UC05](api/current-contract.md#forgot-password-request-code)

## Xin mã

- `POST /api/v1/auth/forgot-password/request-code` trả `200 PASSWORD_RESET_CODE_SENT` với `data.expiresIn = 600` cho tài khoản đủ điều kiện. Email không tồn tại nhận cùng phản hồi và không cấp mã. Tài khoản chưa có mật khẩu trả `409 PASSWORD_NOT_CONFIGURED`.
- UC05 dùng chung `EmailOtpService` với UC01. Transaction `READ_COMMITTED` khóa theo email chuẩn hóa và purpose `PASSWORD_RESET`. Chỉ mã mới nhất được xác minh. TTL là 600 giây, tối thiểu 60 giây giữa hai lần cấp, tối đa 5 lần trong cửa sổ trượt 1 giờ. Vượt giới hạn trả `429 OTP_RATE_LIMITED` và `Retry-After`.
- Thiếu cấu hình SMTP hoặc khóa HMAC hợp lệ trả `503 SERVICE_UNAVAILABLE` trước khi cấp cho tài khoản đủ điều kiện. Adapter gửi thư sau commit. SMTP từ chối hoặc timeout vẫn trả phản hồi chung 200, ghi lỗi vận hành và tính lần cấp vào hạn mức. Lỗi database hoặc commit trả theo bộ lỗi chung. [Cấu hình OTP và SMTP](local-development.md#cau-hinh-otp-smtp)

## Đặt lại mật khẩu

- `POST /api/v1/auth/reset-password` nhận email, OTP, mật khẩu mới và xác nhận. Transaction `READ_COMMITTED` lấy khóa email/purpose trước, rồi khóa bản ghi user như login/refresh và lấy thời điểm xử lý. Tài khoản không tồn tại trả `400 OTP_INVALID`. Tài khoản chưa có mật khẩu trả `409 PASSWORD_NOT_CONFIGURED`.
- Mã hết hạn, đã dùng hoặc không hợp lệ trả `400 OTP_INVALID`. Lần nhập sai của mã còn hiệu lực được commit trước khi trả lỗi. Từ lần sai thứ 5 trả `400 OTP_ATTEMPTS_EXCEEDED` cho đến khi mã hết hiệu lực hoặc được thay thế.
- Hash mật khẩu bằng Argon2id, đánh dấu OTP đã dùng và thu hồi tất cả phiên/token còn hiệu lực của user trong cùng transaction. Phiên thu hồi ghi `PASSWORD_RESET`. Token còn hiệu lực được thu hồi kể cả khi phiên chứa nó đã kết thúc. Giữ lịch sử đã kết thúc và trạng thái tài khoản, kể cả `BANNED`. Kết quả `200 PASSWORD_RESET_SUCCESS` với `data: null` chỉ được trả sau commit.

Xem [kiểm thử](development.md#kiểm-thử-hiện-có).
