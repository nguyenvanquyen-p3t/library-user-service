# Quyết định còn mở

Cập nhật 2026-10-19: đã cho phép 7 bảng/20 API, session revocation và Gateway introspection RFC 7662; Web/Android giữ refresh token trong JSON. Xem [quyết định đã xác nhận](confirmed-decisions.md). Các mục còn mở bên dưới đều là **PENDING DECISION**.

Q01 đã chốt: kích hoạt là BANNED → ACTIVE, không thêm trạng thái; Local xác minh OTP và Google xác minh/hoàn tất đăng ký trước khi tạo user ACTIVE, READER. Khóa thu hồi refresh token còn hiệu lực; mở khóa không khôi phục token, người dùng đăng nhập lại.

Q02 đã chốt phạm vi: database hỗ trợ nhiều role, bao gồm ADMIN với role khác; API gán/thêm/gỡ/thay thế role ngoài V1, không tự nâng quyền. Quy tắc quản lý role và hiệu lực token sau đổi role là **PENDING DECISION cho phiên bản mở rộng**, không phải điều kiện bổ sung API vào V1.

Q03/Q04 đã chốt một phần: RS256, refresh token opaque lưu hash và rotation; Gateway introspection kiểm tra JWT cùng phiên. BANNED không đăng nhập/refresh. Session revocation được cho phép, không có JWT Blacklist hoặc Redis.

Q11 đã chốt: **Gateway dùng Service JWT**. Q12 đã chốt: **V6 chỉ chạy khi RefreshTokens rỗng**, không tự xóa/thu hồi token cũ. Công việc hiện tại chỉ migration và docs; 20 API chưa triển khai.

Q05 đã chốt ngày 2026-10-20: OTP 6 chữ số, hạn 10 phút, tối đa 5 lần nhập sai/mã; cấp mã cách nhau ít nhất 60 giây và tối đa 5 lần/cửa sổ trượt 1 giờ theo email và purpose. Mã mới vô hiệu mã cũ; không reset lịch sử giới hạn gửi. Mật khẩu 6–128 ký tự, cho phép khoảng trắng/Unicode, không bắt tổ hợp ký tự, không trim/cắt ngắn, không kiểm tra danh sách mật khẩu phổ biến/đã lộ, không đổi định kỳ nếu không có dấu hiệu lộ lọt. Đã bỏ đề xuất giới hạn theo IP tại Gateway. Access token 1 giờ, refresh token 7 ngày, session cố định 30 ngày và registrationToken 10 phút đã chốt. Xem [chi tiết quyết định](confirmed-decisions.md).

Q08 đã chốt ngày 2026-10-20: OTP REGISTER chỉ gắn email và purpose, không gắn username; mã mới vô hiệu mã cũ cùng email và purpose. registrationToken có hạn 10 phút; chống đăng ký lặp bằng Google identity và UNIQUE `(Provider, ProviderUserId)`. Identity đã tồn tại thì từ chối hoàn tất đăng ký, không cấp session/token; chuyển sang đăng nhập Google. Không lưu trạng thái đã dùng của từng token, chấp nhận giới hạn nếu identity bị xóa khi token còn hạn. Xem [quyết định chi tiết](confirmed-decisions.md).

## PENDING DECISION trong V1

Ngày 2026-10-20 đã chốt ngoại lệ logout UC04: token cũ còn bản ghi có thể xác định session để thu hồi; phân biệt thu hồi server với xóa token client. UC03 đã chốt: dùng lại refresh token đã rotation bị từ chối cấp token mới, không thu hồi session hoặc token thay thế vì sự kiện này. Lịch sử refresh token được giữ đến khi session kết thúc cộng 7 ngày; dọn hằng ngày theo lô, toàn bộ token cùng phiên trong một transaction. Xem [quyết định UC03, UC04 và lưu giữ token](confirmed-decisions.md). Thời gian lưu/xóa bản ghi UserSessions vẫn **PENDING DECISION**, không được suy ra từ thời gian dọn refresh token.

| ID | Cần xác nhận | Tại sao cần |
| --- | --- | --- |
| Q03 | Xác thực Admin/Borrow Service, phạm vi quyền và actor Admin | Service JWT đã chốt cho Gateway introspection; không tự áp dụng cùng hợp đồng cho service khác |
| Q06 | Dùng dịch vụ email nào và cách tích hợp; lỗi gửi email xử lý, retry thế nào? | Có yêu cầu gửi email nhưng chưa có cấu hình/hợp đồng |
| Q07 | Google client ID nào được server chấp nhận? | Ngày 2026-10-20 đã chốt tự liên kết Google identity chưa tồn tại với tài khoản Local cùng email đã được Google xác minh, không yêu cầu mật khẩu Local/xác thực bổ sung. Server phải xác minh ID Token; danh sách client ID được phép còn chờ chốt |
| Q09 | Quy ước lỗi: giữ envelope success/messageCode/message/data như Excel? Phân biệt 400/422, missing/null PATCH và trường ngoài danh sách thế nào? | Nhiều ô lỗi để trống; chưa có schema đầy đủ |
| Q10 | Khi Admin gọi lại cùng trạng thái, hai lệnh đồng thời hoặc timeout thì xử lý thế nào? Ghi audit ở service nào? | Cần hợp đồng retry, tính nhất quán và truy vết |
| Q11a | Chi tiết Service JWT Gateway | Còn issuer/audience/subject, thuật toán, TTL, bên phát token và phân phối/rotation khóa |
| Q13 | UC05/UC09 xác minh OTP ở request hoàn tất hay ở bước riêng trước khi nhập mật khẩu? | Word mô tả kiểm tra mã trước rồi nhập mật khẩu; API nguồn nhận mã và mật khẩu trong cùng request. Có thể chia màn hình phía client nhưng chưa được coi là xác minh server thành công trước request cuối. Không tự thêm endpoint/ticket |
| Q14 | Cấu hình user JWT: issuer/audience, quản lý và phân phối/rotation khóa; nguồn roles cho introspection | RS256 và TTL đã chốt; các giá trị cấu hình và nguồn roles chưa thành hợp đồng. Không dùng giá trị example làm cấu hình thật |
| Q15 | Thời gian lưu/xóa UserSessions | Retention refresh token đã chốt không đồng nghĩa được xóa bản ghi phiên sau cùng thời gian |

Q04 không còn là lựa chọn cơ chế xác thực chưa chốt: đã chọn introspection kiểm tra phiên, không cache active ở giai đoạn đầu. Phần còn lại là công việc triển khai, chỉ bắt đầu khi được yêu cầu code; không hỏi lại lựa chọn này. Q09 cũng bao gồm lỗi khi UC11 cố chọn phiên hiện tại, schema/validation và sự thống nhất response của các nhánh Google.

Q13–Q15 được tập hợp khi bàn giao docs ngày 2026-10-22, chưa phải quyết định mới của chủ dự án. Chỉ cần giải quyết các mục liên quan trước khi triển khai phần tương ứng.

Phạm vi được mở rộng thành 7 bảng, 20 API. Không tự mở rộng thêm để giải quyết các câu hỏi còn lại.
