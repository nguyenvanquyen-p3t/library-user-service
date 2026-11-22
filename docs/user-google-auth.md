# Đăng nhập và đăng ký Google UC08

Triển khai ngày 2026-11-22. Hai API công khai nhận JSON và trả envelope với `Cache-Control: no-store` cho cả thành công và lỗi. [Đăng nhập Google](api/current-contract.md#google) · [Hoàn tất đăng ký](api/current-contract.md#google-register)

## Xác minh và cấu hình

- `GoogleIdTokenVerifier` dùng Nimbus với chữ ký RS256 và JWKS của Google. Server kiểm tra issuer, audience, presenter nếu khác audience, `sub` và thời hạn trước khi dùng danh tính. Xác minh bằng JWKS diễn ra ngoài transaction. Lỗi tải JWKS hoặc timeout trả `503 SERVICE_UNAVAILABLE`. [Google OpenID Connect](https://developers.google.com/identity/openid-connect/reference)
- `GOOGLE_ALLOWED_CLIENT_IDS` nhận danh sách client ID dùng làm audience. `GOOGLE_ALLOWED_PRESENTER_CLIENT_IDS` nhận danh sách client ID được phép xuất hiện trong `azp` khi khác audience, chẳng hạn client Android dùng audience của backend. Hai danh sách phân cách bằng dấu phẩy.
- `GOOGLE_REGISTRATION_HMAC_KEY` nhận 32 byte ngẫu nhiên mã hóa Base64. Dùng khóa riêng cho UC08 và dùng chung giữa các instance cùng môi trường. `GoogleRegistrationToken` ký HS256 với mục đích đăng ký Google, mang `sub` và email chuẩn hóa, có hạn đúng 600 giây. Thay khóa làm token đăng ký đã cấp hết khả năng sử dụng.
- Thiếu danh sách audience hoặc khóa ký hợp lệ trả `503 SERVICE_UNAVAILABLE` cho hai API. User Service vẫn khởi động. Cấp cấu hình triển khai qua môi trường hoặc `backend/.env` cục bộ. [Mẫu cấu hình](../backend/.env.example)

## Đăng nhập, liên kết và hoàn tất

- Tìm identity theo `GOOGLE` + `sub` trước. Nếu identity có liên kết và user còn `ACTIVE`, API tạo phiên mới và trả `GOOGLE_LOGIN_SUCCESS`. Email Google thay đổi không đổi chủ sở hữu identity.
- Khi chưa có identity, dùng email Google đã xác minh. Tự liên kết với tài khoản Local có mật khẩu, chưa có identity Google, nếu email là Gmail hoặc Google xác nhận thuộc Workspace qua `hd`. Giữ mật khẩu và role hiện tại. Email đã thuộc tài khoản nhưng không đủ điều kiện liên kết trả `401 GOOGLE_TOKEN_INVALID`. Tài khoản đủ điều kiện đăng nhập/liên kết nhưng `BANNED` trả `403 ACCOUNT_BANNED`. [Quyết định tự liên kết](confirmed-decisions.md#google-linking)
- Với email đã xác minh và chưa được dùng, kể cả email bên thứ ba, API trả `GOOGLE_REGISTRATION_REQUIRED` cùng `registrationToken`. `fullName` và phần trước `@` của email được chuẩn hóa và dùng làm gợi ý nếu hợp lệ, còn lại trả `null`. Bước này chưa tạo tài khoản hoặc phiên.
- Hoàn tất đăng ký lưu user `ACTIVE` chưa có mật khẩu, identity `GOOGLE`, role `READER`, phiên và refresh token cùng transaction. Xung đột identity, email hoặc username trả `409` với mã tương ứng. Sửa dữ liệu rồi gửi lại trong thời hạn token gốc. Nếu đã commit và mất response, người dùng đăng nhập lại qua API Google.

## Transaction

`GoogleAuthTransactions` dùng `READ_COMMITTED` và khóa advisory theo Google `sub` trước khi tra identity. Với user đã tồn tại, khóa tiếp cùng bản ghi user như Local login rồi kiểm tra lại hạn token, trạng thái và điều kiện liên kết. Bước hoàn tất kiểm tra lại hạn token sau khi lấy khóa advisory. Các constraint UNIQUE hiện có xử lý xung đột email/username với luồng khác.

`LoginSessionIssuer` cấp phiên và cặp token cho cả UC02 và UC08. Lỗi lưu, ký token hoặc commit rollback thao tác. Phiên, access token và refresh token giữ thời hạn 30 ngày, 1 giờ và 7 ngày mỗi lần cấp. [Kiểm thử](development.md#kiểm-thử-hiện-có)
