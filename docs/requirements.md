# Yêu cầu nghiệp vụ

Trạng thái: **thiết kế, chưa triển khai API**. Nguồn là các mục UC01–UC12 trong `DacTaUseCase.docx`, đối chiếu `ThietKeApi.xlsx` Sheet1 và [quyết định của chủ dự án](confirmed-decisions.md). Quyết định đã xác nhận được ưu tiên khi diễn giải phạm vi. Xem [luồng đầy đủ](use-cases.md) và [bản đồ API](api/README.md).

| Use case | Luồng chính và kết quả | Ngoại lệ cần kiểm thử |
| --- | --- | --- |
| UC01 Đăng ký | Gửi mã 6 chữ số, lưu hash với purpose REGISTER. Khi xác minh hợp lệ, tạo user ACTIVE, hash mật khẩu, gán READER và đánh dấu mã đã dùng | Trùng username/email, gửi mail lỗi, mã sai/hết hạn/đã dùng/vượt số lần thử |
| UC02 Đăng nhập | Nhận username hoặc email và mật khẩu, kiểm tra password và ACTIVE, tạo phiên, cấp JWT access token có sid và refresh token; lưu hash refresh token | Sai thông tin, chưa có password, tài khoản BANNED |
| UC03 Làm mới token | Kiểm tra hash, hạn, trạng thái thu hồi, user ACTIVE và session còn hiệu lực; rotation cùng sid, thu hồi token cũ và lưu liên kết thay thế | Token không tồn tại/hết hạn/đã thu hồi, user bị khóa hoặc session không còn hiệu lực. Dùng lại token đã rotation: từ chối cấp token mới, không thu hồi session hoặc token thay thế vì sự kiện này (chốt 2026-10-20) |
| UC04 Đăng xuất | Theo quyết định 2026-10-20: dùng refresh token, kể cả token cũ còn bản ghi, xác định session để thu hồi session và refresh token liên quan trong cùng transaction; client xóa token | Session đã thu hồi/hết hạn trả thành công; token không xác định được session chỉ bảo đảm đăng xuất client; lỗi transaction không được báo thu hồi thành công. Xem [quyết định UC04](confirmed-decisions.md) |
| UC05 Quên mật khẩu | Gửi mã PASSWORD_RESET, xác minh, đổi password, dùng mã một lần và thu hồi toàn bộ phiên và refresh token liên quan | Email không tồn tại trả thông báo chung; tài khoản chưa có password chuyển sang luồng UC09; mã hoặc mật khẩu không hợp lệ |
| UC06 Hồ sơ | User ACTIVE xem/cập nhật hồ sơ bản thân; trường cập nhật trong Excel là fullName và phoneNumber | Access token không hợp lệ/hết hạn, dữ liệu không hợp lệ; không được sửa role/status/password qua hồ sơ |
| UC07 Đổi mật khẩu | User ACTIVE có password, xác minh mật khẩu hiện tại, đặt password mới, thu hồi toàn bộ phiên và refresh token, kể cả phiên hiện tại, và yêu cầu đăng nhập lại | Sai mật khẩu, xác nhận không khớp, chưa có password |
| UC08 Google | Xác minh ID Token; tìm identity theo GOOGLE và sub. Đã liên kết thì đăng nhập; identity chưa tồn tại và email đã được Google xác minh trùng tài khoản Local thì tự liên kết, không yêu cầu mật khẩu Local; người mới hoàn tất đăng ký với READER và password NULL | Token không hợp lệ, hủy đăng nhập, username trùng, user BANNED |
| UC09 Thiết lập mật khẩu | User ACTIVE chưa có password nhận mã SET_PASSWORD, xác minh để đặt password, giữ identity Google, thu hồi toàn bộ phiên và refresh token, đăng nhập lại | Đã có password, gửi mail lỗi, mã sai/hết hạn, mật khẩu không hợp lệ/không khớp |
| UC10 Danh sách phiên | Xem các phiên còn hiệu lực của chính mình, đánh dấu isCurrent | Phiên gọi không hợp lệ, tải lỗi |
| UC11 Đăng xuất một phiên khác | Chỉ cho chọn phiên khác; kiểm tra ownership và khác sid tại server; thu hồi phiên đích và token | Không tìm thấy/không thuộc user, cố chọn phiên hiện tại, lỗi transaction; thu hồi lặp phiên của mình trả thành công khi phiên gọi hợp lệ |
| UC12 Đăng xuất tất cả phiên khác | Xác nhận rồi thu hồi các phiên khác và token liên quan, giữ phiên hiện tại | Không có phiên khác, phiên gọi không hợp lệ, lỗi transaction |

## Điểm đối chiếu giữa các nguồn

UC01 mô tả nhập đầy đủ thông tin trước khi gửi mã. Excel tách request-code chỉ nhận `userName`, `email`; request tạo tài khoản sau đó nhận toàn bộ thông tin cùng `verificationCode`. Không tự giả định server đã lưu password hoặc dữ liệu đăng ký tạm khi gửi mã. Ngày 2026-10-20 đã chốt OTP chỉ gắn với email và `Purpose = REGISTER`, không gắn username. Đổi username vẫn dùng được mã cho cùng email nếu còn hiệu lực, không gia hạn mã hoặc reset số lần thử; đổi email phải xin mã cho email mới. Username vẫn phải đáp ứng quy tắc dữ liệu và ràng buộc duy nhất hiện có.

UC08 mô tả ba kết quả. Excel cụ thể hóa bước hoàn tất Google bằng `registrationToken` và endpoint riêng. Ngày 2026-10-20 đã chốt token có thời hạn 10 phút (600 giây) kể từ lúc cấp, không gia hạn. Sửa dữ liệu hoặc username trùng không kéo dài TTL; hết hạn phải thực hiện lại bước xác minh Google để nhận token mới. Token chỉ dùng để hoàn tất đăng ký với danh tính Google đã được server xác minh, không thay thế access/refresh token.

Q08 đã chốt chống đăng ký Google lặp: identity GOOGLE với ProviderUserId bằng sub đã tồn tại thì từ chối hoàn tất đăng ký, không cấp thêm session/token; người dùng đăng nhập Google. Nếu chưa tồn tại, tạo user/identity/READER/session/refresh token cùng transaction, chỉ trả token sau commit; xung đột UNIQUE identity phải rollback toàn bộ. Transaction rollback được thử lại trong TTL; đã commit nhưng mất response thì chuyển sang đăng nhập Google. Không lưu trạng thái đã dùng của từng registrationToken: nếu identity bị xóa khi token còn hạn, cơ chế này không chứng minh được token đã từng dùng. Xem [quyết định đã xác nhận](confirmed-decisions.md).

Excel dùng `expiresIn: 600` trong ví dụ mã xác minh và `1800` trong ví dụ access token. Ngày 2026-10-20 đã chốt access token 1 giờ (3600 giây), refresh token 7 ngày; ví dụ 1800 không còn đại diện TTL đã chốt. Session cố định 30 ngày và TTL mã xác minh 10 phút (600 giây) đã chốt; chưa có cấu hình runtime.

Ngày 2026-10-20 đã chốt UC08 A1 tự liên kết Google identity chưa tồn tại với tài khoản Local cùng email đã được Google xác minh, không yêu cầu nhập mật khẩu Local hoặc xác thực bổ sung. Server lấy sub/email/trạng thái email đã xác minh từ Google ID Token hợp lệ, không tin dữ liệu danh tính do client tự khai. Liên kết vào user hiện có, không tạo user mới, không thay mật khẩu/role; chỉ user ACTIVE được đăng nhập và cấp session/token. Q07 chỉ còn danh sách Google client ID được chấp nhận chưa chốt. Xem [quyết định đã xác nhận](confirmed-decisions.md).

## Chính sách OTP và mật khẩu đã chốt

Ngày 2026-10-20 chốt OTP 6 chữ số cho REGISTER/PASSWORD_RESET/SET_PASSWORD, hạn 10 phút, tối đa 5 lần nhập sai cho mỗi mã. Sau lần sai thứ 5, mã không được chấp nhận kể cả nhập đúng. Cấp mã cách nhau ít nhất 60 giây, tối đa 5 lần trong cửa sổ trượt 1 giờ theo cùng email và purpose. Mã mới làm mất hiệu lực mã cũ, không reset lịch sử giới hạn gửi. Dùng mã một lần và cập nhật nghiệp vụ liên quan trong cùng transaction; không BANNED tài khoản vì sai OTP. Không bổ sung giới hạn theo IP tại Gateway theo đề xuất đã được điều chỉnh.

Mật khẩu dài 6–128 ký tự, cho phép khoảng trắng/Unicode; không bắt tổ hợp ký tự, không tự trim/cắt ngắn, không kiểm tra danh sách mật khẩu phổ biến/đã lộ và không bắt đổi định kỳ nếu không có dấu hiệu lộ lọt. Áp dụng thống nhất cho UC01/UC05/UC07/UC09. Xem [quyết định Q05](confirmed-decisions.md); đây là yêu cầu chưa triển khai, lỗi gửi email còn chờ Q06.

## Ranh giới transaction

Theo xác nhận ngày 2026-10-22, các chi tiết này được lưu trong docs, không cần đưa vào chuỗi tương tác Actor–hệ thống trong bản Word. Đây là yêu cầu thiết kế, chưa triển khai API.

| Use case | Các thay đổi phải cùng transaction |
| --- | --- |
| UC01 | Tạo user ACTIVE, gán role READER và đánh dấu mã xác minh đã dùng |
| UC03 | Thu hồi refresh token cũ, lưu token thay thế và liên kết rotation cùng phiên |
| UC05 | Cập nhật PasswordHash, đánh dấu OTP đã dùng, thu hồi toàn bộ phiên và refresh token liên quan |
| UC07 | Cập nhật PasswordHash và thu hồi toàn bộ phiên cùng refresh token liên quan |
| UC08 hoàn tất đăng ký | Tạo user, identity GOOGLE, READER, session và refresh token; chỉ trả token sau commit |
| UC11 | Thu hồi phiên đích khác phiên hiện tại cùng refresh token liên quan |
| UC04 | Thu hồi phiên được xác định từ refresh token và các refresh token còn hiệu lực thuộc phiên đó |
| UC09 | Cập nhật PasswordHash, đánh dấu mã xác minh đã dùng và thu hồi toàn bộ phiên cùng các refresh token liên quan |
| UC12 | Thu hồi các phiên khác còn hiệu lực và các refresh token liên quan, giữ nguyên phiên có sid từ token đã xác minh |

Chỉ xác nhận thành công sau commit. Nếu transaction thất bại, rollback các thay đổi trong transaction, không để cập nhật một phần hoặc báo thành công. Với UC04 không xác định được session và chỉ xóa token ở client, áp dụng ngoại lệ đã chốt thay vì khẳng định thu hồi server thành công. Việc gửi email và xóa token phía client không nằm trong transaction database.

## Quản trị

Admin Service điều phối khóa và mở khóa/kích hoạt lại qua API cập nhật trạng thái nội bộ. Kích hoạt là BANNED → ACTIVE; không có trạng thái chờ kích hoạt. Local xác minh OTP thành công trước khi tạo user ACTIVE; Google xác minh hợp lệ và hoàn tất đăng ký trước khi tạo user ACTIVE. Cả hai gán mặc định READER.

Khóa ACTIVE → BANNED thu hồi mọi phiên và refresh token còn hiệu lực trong thiết kế mở rộng; BANNED không được đăng nhập/refresh. Mở khóa không khôi phục phiên/token, người dùng đăng nhập lại. Gateway kiểm tra JWT và phiên qua introspection, chưa có implementation.

Database hỗ trợ nhiều role, bao gồm ADMIN cùng role khác. API gán/thêm/gỡ/thay thế role vẫn ngoài phạm vi. Phạm vi mới có 7 bảng/20 API, hiện chỉ thêm migration và docs.

## Quyết định xác thực đã chốt

JWT Access Token dùng RS256. Refresh token opaque lưu hash, rotation trong cùng phiên, trả JSON cho Web/Android. Gateway dùng Service JWT gọi introspection; chi tiết claim/khóa và TTL Service JWT còn chờ chốt. TTL access token 1 giờ, refresh token 7 ngày đã chốt ngày 2026-10-20; Session có hạn cố định 30 ngày từ lúc tạo, không gia hạn khi refresh. Token giữ TTL riêng nhưng bị từ chối truy cập/refresh khi session hết hạn; LastSeenAt chưa dùng làm thời hạn không hoạt động. Thiết kế có session revocation, không có JWT blacklist hoặc Redis; thu hồi refresh token không tự vô hiệu JWT.

## Quản lý phiên và thiết bị

Thêm thiết kế danh sách phiên của chính mình, đăng xuất một phiên khác (UC11 không cho chọn phiên hiện tại) và đăng xuất tất cả phiên khác giữ phiên hiện tại; xem [API phiên](api/sessions.md). Thông tin thiết bị là metadata phiên, không phải bằng chứng danh tính. Login tạo phiên; refresh giữ sid; logout thu hồi phiên. Khóa/đổi/reset/thiết lập mật khẩu cần tích hợp thu hồi phiên khi code các API gốc; hiện chỉ có schema và thiết kế.
