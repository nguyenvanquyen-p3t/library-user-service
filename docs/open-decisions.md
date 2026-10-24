# Quyết định còn mở

Cập nhật danh mục: 2026-10-24. Chỉ các câu hỏi chưa được chủ dự án xác nhận mới mang nhãn **PENDING DECISION**. ID cũ được giữ để liên kết không đổi; cập nhật câu hỏi không phải quyết định nghiệp vụ mới.

## Các phần đã chốt

| ID | Tra cứu quyết định hiện hành |
| --- | --- |
| Q01 | [Vòng đời tài khoản, khóa và mở khóa](confirmed-decisions.md#account-lifecycle) |
| Q02 | [Phạm vi role](confirmed-decisions.md#roles); cơ chế quản lý role dành cho phiên bản mở rộng |
| Q03/Q04 | [Token người dùng và introspection kiểm tra phiên](confirmed-decisions.md#user-tokens); Q03 chỉ còn xác thực Admin/Borrow bên dưới |
| Q05 | [OTP và mật khẩu](confirmed-decisions.md#otp-password-policy), [thời hạn phiên](confirmed-decisions.md#session-lifetime), [registrationToken](confirmed-decisions.md#google-registration-ttl) |
| Q07 | [Tự liên kết Google](confirmed-decisions.md#google-linking); chỉ danh sách client ID còn mở |
| Q08 | [OTP REGISTER gắn email/purpose](confirmed-decisions.md#register-otp-binding), [chống đăng ký Google lặp](confirmed-decisions.md#google-replay) |
| Q11/Q12 | [Phạm vi đã chốt](confirmed-decisions.md#phạm-vi): Gateway dùng Service JWT, V6 chỉ chạy khi RefreshTokens rỗng |

UC03 reuse, UC04 logout và retention đã chốt: đọc [reuse](confirmed-decisions.md#refresh-reuse), [logout](confirmed-decisions.md#logout), [lưu giữ refresh token](confirmed-decisions.md#refresh-retention). Q04 không còn mở về lựa chọn introspection hoặc không cache active ở giai đoạn đầu; chưa triển khai không đồng nghĩa chưa chọn cơ chế.

## PENDING DECISION trong V1

Mỗi hàng nêu phần cần chốt trước khi triển khai hoặc tích hợp phần bị ảnh hưởng. Không cần giải quyết toàn bộ bảng để sửa docs; không tự bổ sung endpoint hoặc schema để lấp khoảng trống.

| ID | Phần còn cần xác nhận | UC/API bị ảnh hưởng và thời điểm cần chốt |
| --- | --- | --- |
| <a id="q03"></a>Q03 | Cơ chế xác thực riêng của Admin/Borrow Service, phạm vi quyền và cách chứng minh actor Admin | Hai API nội bộ; chốt trước khi triển khai xác thực/tích hợp. Service JWT đã chọn cho Gateway không tự áp dụng cho service khác |
| <a id="q06"></a>Q06 | Dịch vụ email, phương thức tích hợp, lỗi gửi và retry | UC01/UC05/UC09; chốt trước khi triển khai gửi OTP. Không giả định database và email commit nguyên tử |
| <a id="q07"></a>Q07 | Danh sách Google client ID được server chấp nhận | UC08; chốt trước khi cấu hình xác minh ID Token. Không chấp nhận mọi audience hoặc hỏi lại chính sách tự liên kết |
| <a id="q09"></a>Q09 | Envelope, schema request/response, required/null, validation, missing/null PATCH, trường ngoài danh sách, HTTP 400/422, messageCode và response lỗi; sự thống nhất các nhánh Google; HTTP/messageCode khi UC11 chọn phiên hiện tại | 20 API; chốt phần liên quan trước khi dùng làm hợp đồng tích hợp. Response/phân trang API phiên còn là đề xuất; không coi ví dụ là schema đầy đủ |
| <a id="q10"></a>Q10 | Gọi lại cùng trạng thái, lệnh trạng thái ngược nhau đồng thời, timeout/retry, actor audit và chính sách khóa tài khoản admin | API cập nhật trạng thái; chốt trước khi triển khai hành vi quản trị. Xác định nơi ghi audit, không tự thêm bảng |
| <a id="q11a"></a>Q11a | Bên phát Service JWT Gateway, thuật toán, issuer/audience/subject, TTL, quyền gọi và phân phối/rotation khóa | Introspection; chốt trước khi cấu hình xác thực Gateway. Không suy ra thuật toán từ RS256 của access token người dùng |
| <a id="q13"></a>Q13 | UC05/UC09 xác minh OTP trong request hoàn tất hay ở bước riêng trước khi nhập mật khẩu? | Word mô tả kiểm tra mã trước nhập mật khẩu; API nguồn nhận chung. Chốt cách tương tác trước triển khai màn hình/liên kết API; không tự thêm verify-code endpoint hoặc ticket |
| <a id="q14"></a>Q14 | Issuer/audience của user JWT, quản lý/phân phối/rotation khóa và nguồn roles cho introspection | Các API cấp token, API được bảo vệ và introspection; chốt trước triển khai JWT/phân quyền. RS256 và TTL đã chốt; example không phải cấu hình thật |
| <a id="q15"></a>Q15 | Thời gian lưu/xóa UserSessions | Chốt trước khi xây tác vụ dọn phiên. Retention refresh token đã chốt không cho phép suy ra thời điểm xóa bản ghi phiên |

Q13–Q15 được tập hợp khi bàn giao docs ngày 2026-10-22, chưa phải quyết định mới của chủ dự án. Mã lý do cho khóa/UC11/UC12 và chính sách cập nhật LastSeenAt vẫn cần thống nhất khi hoàn thiện thiết kế tương ứng; xem [schema](data-model.md#migration-v6-quản-lý-phiên) và [API phiên](api/sessions.md#danh-sách-phiên-của-chính-mình), không tự coi ví dụ là enum SQL hoặc cấu hình đã chốt.

## Ngoài phạm vi V1

Quy tắc thay thế hay thêm/gỡ role, cơ chế gán/thu hồi ADMIN và hiệu lực access token sau đổi role thuộc phiên bản mở rộng. Không dùng các câu hỏi này làm điều kiện bổ sung API quản lý role vào 20 API hiện tại. Phạm vi vẫn là 7 bảng; không tự thêm bảng hoặc endpoint.
