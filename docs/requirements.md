# Yêu cầu nghiệp vụ

Trạng thái: **thiết kế, chưa triển khai API**. Biên tập lại ngày 2026-10-24 từ bản bàn giao UC01–UC12 ngày 2026-10-22, Excel và quyết định đã xác nhận. Tài liệu này duy trì ranh giới transaction và tính nhất quán; chuỗi tương tác đầy đủ nằm tại [use case](use-cases.md), chính sách tại [sổ quyết định](confirmed-decisions.md), HTTP tại [bản đồ API](api/README.md).

| Use case | Nơi tra cứu hành vi và ngoại lệ | Điểm cần đọc trước triển khai |
| --- | --- | --- |
| UC01 | [Đăng ký email](use-cases.md#uc01-đăng-ký-bằng-email) | OTP REGISTER và transaction tạo tài khoản |
| UC02 | [Đăng nhập Local](use-cases.md#uc02-đăng-nhập-local) | Trạng thái user, tạo phiên và cấp token |
| UC03 | [Làm mới token](use-cases.md#uc03-làm-mới-token) | Rotation một lần, cạnh tranh và reuse |
| UC04 | [Đăng xuất](use-cases.md#uc04-đăng-xuất-phiên-hiện-tại) | Token cũ, thu hồi server khác xóa token client |
| UC05 | [Quên mật khẩu](use-cases.md#uc05-quên-và-đặt-lại-mật-khẩu) | OTP PASSWORD_RESET, Q13 và thu hồi phiên |
| UC06 | [Hồ sơ](use-cases.md#uc06-xem-và-cập-nhật-hồ-sơ) | Chỉ sửa fullName/phoneNumber, UpdatedAt từ trigger |
| UC07 | [Đổi mật khẩu](use-cases.md#uc07-đổi-mật-khẩu) | Thu hồi cả phiên đang gọi |
| UC08 | [Google](use-cases.md#uc08-đăng-nhập-và-đăng-ký-google) | Ba nhánh, tự liên kết và chống đăng ký lặp |
| UC09 | [Thiết lập mật khẩu](use-cases.md#uc09-thiết-lập-mật-khẩu-lần-đầu) | OTP SET_PASSWORD, Q13 và giữ identity Google |
| UC10 | [Danh sách phiên](use-cases.md#uc10-xem-danh-sách-phiên) | Ownership và isCurrent từ sid đã xác minh |
| UC11 | [Đăng xuất một phiên khác](use-cases.md#uc11-đăng-xuất-một-phiên-khác) | Server từ chối phiên hiện tại, thu hồi lặp |
| UC12 | [Đăng xuất các phiên khác](use-cases.md#uc12-đăng-xuất-tất-cả-phiên-khác) | Giữ phiên hiện tại, không cấm login mới sau thao tác |

## Điểm đối chiếu giữa các nguồn

UC01 trong Word nhập đầy đủ thông tin trước khi gửi mã; API tách xin mã với username/email và hoàn tất với toàn bộ dữ liệu. Không giả định server lưu password hoặc dữ liệu đăng ký tạm. Quy tắc đổi username/email khi chờ OTP nằm tại [ràng buộc OTP REGISTER](confirmed-decisions.md#register-otp-binding); UNIQUE database vẫn áp dụng.

UC08 có ba nhánh và endpoint hoàn tất bằng registrationToken. Chính sách [tự liên kết](confirmed-decisions.md#google-linking), [TTL registrationToken](confirmed-decisions.md#google-registration-ttl) và [chống lặp/rollback/mất response](confirmed-decisions.md#google-replay) đã chốt. Không suy ra token dùng một lần tuyệt đối hoặc thêm kho trạng thái tiêu thụ token.

Ví dụ HTTP hiện hành đã được tổng hợp tại [16 API gốc](api/current-contract.md). Danh mục nguồn giữ nguyên ví dụ Excel, gồm TTL access token cũ; không dùng nó thay chính sách hiện hành. Word và API còn khác cách chia bước OTP UC05/UC09 tại [Q13](open-decisions.md#q13), không tự thêm endpoint.

## Chính sách OTP và mật khẩu đã chốt

Áp dụng [chính sách Q05](confirmed-decisions.md#otp-password-policy) cho UC01/UC05/UC07/UC09 theo chức năng tương ứng; [OTP REGISTER](confirmed-decisions.md#register-otp-binding) chỉ gắn email/purpose. Các giá trị TTL, số lần thử/gửi lại và giới hạn mật khẩu được duy trì tại sổ quyết định để tránh nhiều bản khác nhau. Lỗi gửi email/retry vẫn thuộc [Q06](open-decisions.md#q06).

## Ranh giới transaction

Theo xác nhận ngày 2026-10-22, chi tiết kỹ thuật được lưu ở docs, không bắt buộc lặp trong chuỗi Actor–hệ thống ở Word. Bảng dưới tổng hợp các thay đổi phải nhất quán theo luồng đã mô tả; không lựa chọn isolation level hoặc cách khóa cụ thể.

| Use case/thao tác | Các thay đổi phải cùng transaction |
| --- | --- |
| UC01 | Tạo user ACTIVE với PasswordHash, gán READER và đánh dấu OTP đã dùng |
| UC02 | Tạo session và refresh token liên kết đúng user/phiên; chỉ trả cặp token sau commit |
| UC03 | Thu hồi token cũ, tạo refresh token mới và liên kết rotation cùng phiên |
| UC04 | Thu hồi phiên được xác định từ refresh token và các refresh token còn hiệu lực của phiên |
| UC05 | Cập nhật PasswordHash, dùng OTP một lần và thu hồi toàn bộ phiên/refresh token liên quan |
| UC06 cập nhật | Lưu các trường hồ sơ được phép; trigger Users.UpdatedAt nằm trong transaction cập nhật |
| UC07 | Cập nhật PasswordHash và thu hồi toàn bộ phiên/refresh token, kể cả phiên gọi |
| UC08 identity đã có | Tạo session và refresh token sau khi kiểm tra user ACTIVE |
| UC08 tự liên kết | Thêm identity GOOGLE cho user hiện có, tạo session và refresh token; không tạo user hoặc thay role/password |
| UC08 hoàn tất đăng ký | Tạo user, identity GOOGLE, READER, session và refresh token; xung đột UNIQUE identity rollback toàn bộ |
| UC09 | Cập nhật PasswordHash, dùng OTP một lần và thu hồi toàn bộ phiên/refresh token; giữ identity Google |
| UC11 | Thu hồi phiên đích khác phiên hiện tại cùng refresh token liên quan; không ghi đè thời điểm/lý do thu hồi cũ |
| UC12 | Thu hồi các phiên khác còn hiệu lực cùng refresh token liên quan, giữ phiên có sid đã xác minh |
| Khóa tài khoản qua API nội bộ | Đổi ACTIVE → BANNED, thu hồi mọi phiên và refresh token còn hiệu lực liên quan |
| Mở khóa qua API nội bộ | Đổi BANNED → ACTIVE; không phục hồi phiên/token cũ. Retry/lệnh đồng thời/audit còn Q10 |

Chỉ báo thành công và trả token đăng nhập sau commit. Lỗi transaction phải rollback các thay đổi trong transaction, không cập nhật một phần. UC04 áp dụng [ngoại lệ đã chốt](confirmed-decisions.md#logout): không tìm được session chỉ bảo đảm đăng xuất client; database lỗi không được báo thu hồi server thành công. Email và xóa token client không thuộc transaction database.

## Tính nhất quán khi có request đồng thời

Login, refresh, đổi/reset/thiết lập mật khẩu, cập nhật trạng thái và thu hồi phiên phải được tuần tự hóa với thao tác thu hồi liên quan. Không để writer dùng kết quả kiểm tra user/phiên trước thu hồi để cấp token hoặc tạo dữ liệu trái với trạng thái sau transaction. Thu hồi và login mới có thứ tự trước/sau; UC12 không cấm login mới sau thao tác.

Rotation chỉ thành công một lần cho một token; constraint không thay thế kiểm soát cạnh tranh. Tạo user/identity phải tuân thủ UNIQUE và rollback, không bỏ constraint để xử lý xung đột. Việc dùng OTP một lần, đếm lần nhập sai, vô hiệu mã cũ và giới hạn gửi cũng phải giữ đúng [Q05](confirmed-decisions.md#otp-password-policy) khi có request đồng thời. Tài liệu chưa chọn thứ tự khóa/isolation hoặc cách lưu trạng thái bổ sung.

## Quản trị

Trách nhiệm Admin/Borrow và quyền nội bộ nằm tại [tích hợp nội bộ](api/internal-integration.md). Chính sách [khóa/mở khóa](confirmed-decisions.md#account-lifecycle) và [role ngoài phạm vi](confirmed-decisions.md#roles) được duy trì ở sổ quyết định; cách xử lý retry/audit còn [Q10](open-decisions.md#q10).

## Quyết định xác thực đã chốt

Đọc [token người dùng và Gateway](confirmed-decisions.md#user-tokens), [thời hạn session](confirmed-decisions.md#session-lifetime). Cơ chế introspection đã chọn; issuer/audience/khóa và nguồn roles còn [Q11a](open-decisions.md#q11a)/[Q14](open-decisions.md#q14). Chưa có cấu hình runtime.

## Quản lý phiên và thiết bị

[API phiên](api/sessions.md) duy trì HTTP cho UC10–UC12 và introspection; [schema](data-model.md) duy trì cột/constraint. Thông tin thiết bị chỉ là metadata. Chính sách [thu hồi phiên](confirmed-decisions.md#session-revocation) và [retention refresh token](confirmed-decisions.md#refresh-retention) không đồng nghĩa đã có tác vụ thực thi hoặc đã chốt thời gian xóa UserSessions.
