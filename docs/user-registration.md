# Đăng ký Local UC01

Triển khai ngày 2026-11-20. API [xin mã đăng ký](api/current-contract.md#register-request-code) trả `200 REGISTER_CODE_SENT` sau khi SMTP chấp nhận thư. API [hoàn tất đăng ký](api/current-contract.md#register) trả `201 REGISTER_SUCCESS` với `UserProfile`. Người dùng đăng nhập riêng qua UC02.

## Cấp và xác minh OTP

- Hai thao tác dùng `READ_COMMITTED` và khóa advisory theo transaction của PostgreSQL trên email chuẩn hóa và purpose `REGISTER`. Khóa dùng được ngay từ yêu cầu đầu khi chưa có bản ghi. Mỗi thao tác lấy thời điểm xử lý sau khi có khóa.
- OTP gồm 6 chữ số, có hiệu lực 600 giây. `VerificationCodes.CodeHash` lưu HMAC-SHA-256 theo purpose, email và mã. Chỉ bản ghi cấp sau cùng được xác minh. Username có thể đổi giữa hai bước với cùng email.
- Hai lần cấp cách nhau ít nhất 60 giây và tối đa 5 lần trong cửa sổ trượt 1 giờ. `429 OTP_RATE_LIMITED` trả `Retry-After` làm tròn lên đến thời điểm được cấp tiếp. Gửi SMTP sau commit giúp giải phóng khóa trước khi chờ mail. SMTP từ chối hoặc timeout trả `503 SERVICE_UNAVAILABLE`; lần cấp vẫn tính giới hạn.
- Lần nhập sai của mã còn hiệu lực được commit trước khi trả lỗi. Lần thứ 5 trả `400 OTP_ATTEMPTS_EXCEEDED`. Trong thời gian hiệu lực, mã đã đạt giới hạn bị từ chối kể cả khi nhập đúng. Mã đã dùng, hết hạn hoặc không xác định được trả `400 OTP_INVALID`.

## Tạo tài khoản

Sau khi xác minh mã, service hash mật khẩu bằng Argon2id, tạo user `ACTIVE`, gán `READER` và cập nhật `UsedAt` cùng transaction. Trùng username/email dùng mã `409` theo constraint hiện có. Lỗi ghi hoặc commit rollback toàn bộ thao tác và mã có thể thử lại nếu còn hiệu lực.

Xem [cấu hình OTP và SMTP](local-development.md#cau-hinh-otp-smtp) và [kiểm thử](development.md#kiểm-thử-hiện-có).
