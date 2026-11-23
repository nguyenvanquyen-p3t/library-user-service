# Thiết lập mật khẩu lần đầu UC09

Triển khai ngày 2026-11-23 cho tài khoản đăng nhập Google chưa có mật khẩu. Cả hai API dùng User Bearer và envelope chung. Email lấy từ tài khoản đã xác thực. [Hợp đồng UC09](api/current-contract.md#password-request-code)

## Xin mã

- `POST /api/v1/users/me/password/request-code` không có body. Cấp OTP `SET_PASSWORD` qua `EmailOtpService` với cùng chính sách HMAC, thời hạn 600 giây và hạn mức gửi như UC01/UC05. Vượt hạn mức trả `429 OTP_RATE_LIMITED` kèm `Retry-After`.
- Kiểm tra cấu hình HMAC và SMTP trước khi lưu mã. `OtpMailSender` gửi sau commit. SMTP chấp nhận thư thì trả `200 SET_PASSWORD_CODE_SENT` với `data.expiresIn = 600`. SMTP lỗi hoặc timeout trả `503 SERVICE_UNAVAILABLE` và lần cấp đã commit vẫn tính hạn mức. [Cấu hình OTP/SMTP](local-development.md#cau-hinh-otp-smtp)

## Đặt mật khẩu

- `POST /api/v1/users/me/password/set` nhận `verificationCode`, `newPassword`, `confirmPassword`. Chỉ xác minh mã mới nhất đúng email/purpose. Mã sai hoặc hết hiệu lực trả `400 OTP_INVALID`. Lần nhập sai thứ 5 được lưu trước khi trả `400 OTP_ATTEMPTS_EXCEEDED`.
- Cùng transaction, lưu hash Argon2id, đánh dấu OTP đã dùng và thu hồi mọi phiên/refresh token còn hiệu lực, kể cả phiên gọi. Phiên ghi lý do `PASSWORD_SET`. Refresh token được xét theo hạn riêng, kể cả khi phiên đã kết thúc. Giữ liên kết Google và lịch sử đã hết hạn hoặc thu hồi.
- Sau commit, trả `200 PASSWORD_SET_SUCCESS` với `data: null`. Client xóa token và đăng nhập lại bằng Google hoặc bằng username/email và mật khẩu.

Hai thao tác dùng `READ_COMMITTED`. Service đọc email theo ID, lấy khóa advisory theo email và purpose, rồi khóa cùng bản ghi user với login/refresh. Sau khóa, service lấy thời điểm xử lý và kiểm tra lại email, user `ACTIVE` và phiên gọi. User hoặc phiên mất hiệu lực trả `401 SESSION_INACTIVE`. User có phiên hợp lệ và đã có mật khẩu nhận `409 PASSWORD_ALREADY_CONFIGURED`. Hai request đặt mật khẩu từ các phiên cũ xử lý tuần tự. Sau khi request đầu commit, request đang chờ thấy phiên đã bị thu hồi.

Xem [kiểm thử](development.md#kiểm-thử-hiện-có).
