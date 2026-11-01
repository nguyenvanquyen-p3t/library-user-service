# Quyết định còn mở

Cập nhật danh mục: 2026-10-31. Schema request/response của 20 API, danh mục mã và introspection đã chốt trong các đợt 2026-10-25/26. Ngày 2026-10-31 chốt Service JWT/quyền Admin/Borrow và bằng chứng actor Admin, SMTP, quản trị trạng thái, lỗi tích hợp và cấu hình user access token Q14 với issuer chung không chia môi trường. Cấu hình Service JWT Q03/Q11a vẫn **PENDING DECISION**. Q09 chưa đóng toàn bộ; tổng phạm vi vẫn 7 bảng/20 API, chưa triển khai API hoặc tạo OpenAPI. ID cũ được giữ để liên kết không đổi.

## Các phần đã chốt

| ID | Tra cứu quyết định hiện hành |
| --- | --- |
| Q01 | [Vòng đời tài khoản, khóa và mở khóa](confirmed-decisions.md#account-lifecycle) |
| Q02 | [Phạm vi role](confirmed-decisions.md#roles); cơ chế quản lý role dành cho phiên bản mở rộng |
| <a id="q03"></a>Q03/Q04 | [Token người dùng và introspection kiểm tra phiên](confirmed-decisions.md#user-tokens); [Service JWT/quyền và actor Admin](confirmed-decisions.md#internal-service-auth) chốt ngày 2026-10-31. Cấu hình JWT còn mở bên dưới |
| <a id="q06"></a>Q06 (hành vi) | [SMTP sau commit, lỗi gửi/retry và giới hạn gửi](confirmed-decisions.md#otp-email-delivery), chốt ngày 2026-10-31; chỉ còn lựa chọn/cấu hình môi trường bên dưới |
| Q05 | [OTP và mật khẩu](confirmed-decisions.md#otp-password-policy), [thời hạn phiên](confirmed-decisions.md#session-lifetime), [registrationToken](confirmed-decisions.md#google-registration-ttl) |
| Q07 | [Tự liên kết Google](confirmed-decisions.md#google-linking); chỉ danh sách client ID còn mở |
| Q08 | [OTP REGISTER gắn email/purpose](confirmed-decisions.md#register-otp-binding), [chống đăng ký Google lặp](confirmed-decisions.md#google-replay) |
| Q09 (một phần) | [Schema JSON](confirmed-decisions.md#json-schema-contract), [schema response](confirmed-decisions.md#response-schema-contract) và [HTTP/lỗi](confirmed-decisions.md#http-error-contract), chốt ngày 2026-10-25; [ba API phiên](confirmed-decisions.md#session-response-contract), [danh mục mã phản hồi](confirmed-decisions.md#response-code-contract) và [introspection](confirmed-decisions.md#introspection-contract) chốt ngày 2026-10-26; [lỗi tích hợp và thứ tự kiểm tra API nội bộ](confirmed-decisions.md#integration-error-contract) chốt ngày 2026-10-31. Phần còn mở ở bảng bên dưới |
| <a id="q10"></a>Q10 | [Gửi lại trạng thái đích, tuần tự hóa, đối soát timeout, audit và cấm khóa ADMIN](confirmed-decisions.md#admin-status-contract), chốt ngày 2026-10-31 |
| Q11/Q12 | [Phạm vi đã chốt](confirmed-decisions.md#phạm-vi): Gateway dùng Service JWT, V6 chỉ chạy khi RefreshTokens rỗng |
| <a id="q14"></a>Q14 | [Nguồn roles của introspection](confirmed-decisions.md#introspection-contract) từ database chốt ngày 2026-10-26; [cặp khóa và cấu hình user access token](confirmed-decisions.md#access-token-key-pair) chốt ngày 2026-10-31: iss urn:library:user-service dùng chung, aud ["library-api"], kid user-access-v1, file khóa trong backend/.secrets/, cấp/phân phối/thay khóa có kiểm soát. Chưa triển khai JWT |
| <a id="q13"></a>Q13 | [Xác minh OTP khi reset/thiết lập mật khẩu](confirmed-decisions.md#password-otp-flow), chốt ngày 2026-10-25: gửi OTP cùng mật khẩu trong request hoàn tất, không thêm API xác minh riêng |

UC03 reuse, UC04 logout và retention đã chốt: đọc [reuse](confirmed-decisions.md#refresh-reuse), [logout](confirmed-decisions.md#logout), [lưu giữ refresh token](confirmed-decisions.md#refresh-retention). Q04 không còn mở về lựa chọn introspection hoặc không cache active ở giai đoạn đầu; chưa triển khai không đồng nghĩa chưa chọn cơ chế.

## PENDING DECISION trong V1

Mỗi hàng nêu phần cần chốt trước khi triển khai hoặc tích hợp phần bị ảnh hưởng. Không cần giải quyết toàn bộ bảng để sửa docs; không tự bổ sung endpoint hoặc schema để lấp khoảng trống.

| ID | Phần còn cần xác nhận | UC/API bị ảnh hưởng và thời điểm cần chốt |
| --- | --- | --- |
| Q03 (cấu hình JWT) | Thuật toán, issuer/audience/subject, TTL, tập claim và phân phối/rotation khóa Service JWT Admin/Borrow | Hai API nội bộ; chốt trước triển khai xác thực. Đã chọn Service JWT riêng, service tự ký, quyền từng service và header X-Admin-Access-Token; không hỏi lại các lựa chọn đó hoặc tự áp đề xuất cấu hình JWT chưa chốt |
| Q06 (môi trường) | Nhà cung cấp/tài khoản SMTP, host/port, thông tin xác thực và giá trị timeout cụ thể | Chốt/cấu hình trước triển khai gửi mail; không lưu secret trong docs/Git. Hành vi SMTP, lỗi gửi và retry đã xác nhận, không mở lại |
| <a id="q07"></a>Q07 | Danh sách Google client ID được server chấp nhận | UC08; chốt trước khi cấu hình xác minh ID Token. Không chấp nhận mọi audience hoặc hỏi lại chính sách tự liên kết |
| <a id="q09"></a>Q09 | Tình huống/mã/chi tiết ngoài danh mục đã xác nhận | Áp dụng [schema JSON](api/json-schema.md), [API phiên/introspection](api/sessions.md) và [HTTP/lỗi](api/http-errors.md), gồm hai mã SERVICE_TOKEN_INVALID/ADMIN_ACTOR_INVALID và thứ tự kiểm tra API nội bộ đã chốt ngày 2026-10-31. Không mở lại hành vi SMTP/quản trị đã chốt; cấu hình JWT vẫn mở |
| <a id="q11a"></a>Q11a | Bên phát Service JWT Gateway, thuật toán, issuer/audience/subject, TTL, tập claim và phân phối/rotation khóa | Introspection; chủ dự án hoãn toàn bộ đề xuất cấu hình JWT ngày 2026-10-31. Quyền Gateway tokens:introspect đã xác nhận trong Q03; cách biểu diễn trong JWT vẫn mở. Không suy ra thuật toán từ RS256 của access token người dùng |
| <a id="q15"></a>Q15 | Thời gian lưu/xóa UserSessions | Chốt trước khi xây tác vụ dọn phiên. Retention refresh token đã chốt không cho phép suy ra thời điểm xóa bản ghi phiên |

Q13–Q15 được tập hợp khi bàn giao docs ngày 2026-10-22; Q13 đã chốt ngày 2026-10-25, Q14 chốt ngày 2026-10-31, Q15 vẫn mở. Mã lý do cho khóa/UC11/UC12 và chính sách cập nhật LastSeenAt vẫn cần thống nhất khi hoàn thiện thiết kế tương ứng; xem [schema](data-model.md#migration-v6-quản-lý-phiên) và [API phiên](api/sessions.md#danh-sách-phiên-của-chính-mình), không tự coi ví dụ là enum SQL hoặc cấu hình đã chốt.

Không coi RS256/TTL 5 phút, bảng định danh issuer/audience/subject mẫu, kid hoặc phương án public key/JWKS/rotation dành cho Service JWT trong đề xuất là đã xác nhận. Việc giữ Q03/Q11a mở không ảnh hưởng cấu hình user access token Q14 và introspection không cache đã chốt.

Sau quyết định hoãn cấu hình JWT ban đầu, chủ dự án chốt riêng toàn bộ phần đề xuất user access token theo [Q14 bổ sung](confirmed-decisions.md#access-token-key-pair), điều chỉnh issuer thành urn:library:user-service dùng chung. Không mở lại Q14 vì chưa triển khai code. Service JWT tiếp tục theo Q03/Q11a chưa chốt cấu hình.

## Ngoài phạm vi V1

Quy tắc thay thế hay thêm/gỡ role, cơ chế gán/thu hồi ADMIN và hiệu lực access token sau đổi role thuộc phiên bản mở rộng. Không dùng các câu hỏi này làm điều kiện bổ sung API quản lý role vào 20 API hiện tại. Phạm vi vẫn là 7 bảng; không tự thêm bảng hoặc endpoint.
