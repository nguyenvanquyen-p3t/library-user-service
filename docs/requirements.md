# Yêu cầu nghiệp vụ

Trạng thái: **UC02–UC04, UC06 và UC07 đã triển khai; các thao tác khác còn thiết kế**. Biên tập lại ngày 2026-10-24 từ bản bàn giao UC01–UC12 ngày 2026-10-22, Excel và quyết định đã xác nhận. Tài liệu này duy trì ranh giới transaction và tính nhất quán; chuỗi tương tác đầy đủ nằm tại [use case](use-cases.md), chính sách tại [sổ quyết định](confirmed-decisions.md), HTTP tại [bản đồ API](api/README.md).

| Use case | Nơi tra cứu hành vi và ngoại lệ | Điểm cần đọc trước triển khai |
| --- | --- | --- |
| UC01 | [Đăng ký email](use-cases.md#uc01-đăng-ký-bằng-email) | OTP REGISTER và transaction tạo tài khoản |
| UC02 | [Đăng nhập Local](use-cases.md#uc02-đăng-nhập-local) | Trạng thái user, tạo phiên và cấp token |
| UC03 | [Làm mới token](use-cases.md#uc03-làm-mới-token) | Rotation một lần, cạnh tranh và reuse |
| UC04 | [Đăng xuất](use-cases.md#uc04-đăng-xuất-phiên-hiện-tại) | Token cũ, thu hồi server khác xóa token client |
| UC05 | [Quên mật khẩu](use-cases.md#uc05-quên-và-đặt-lại-mật-khẩu) | OTP PASSWORD_RESET gửi cùng mật khẩu mới và thu hồi phiên |
| UC06 | [Hồ sơ](use-cases.md#uc06-xem-và-cập-nhật-hồ-sơ) | Chỉ sửa fullName/phoneNumber, UpdatedAt từ trigger |
| UC07 | [Đổi mật khẩu](use-cases.md#uc07-đổi-mật-khẩu) | Thu hồi cả phiên đang gọi |
| UC08 | [Google](use-cases.md#uc08-đăng-nhập-và-đăng-ký-google) | Ba nhánh, tự liên kết và chống đăng ký lặp |
| UC09 | [Thiết lập mật khẩu](use-cases.md#uc09-thiết-lập-mật-khẩu-lần-đầu) | OTP SET_PASSWORD gửi cùng mật khẩu mới và giữ identity Google |
| UC10 | [Danh sách phiên](use-cases.md#uc10-xem-danh-sách-phiên) | Ownership và isCurrent từ sid đã xác minh |
| UC11 | [Đăng xuất một phiên khác](use-cases.md#uc11-đăng-xuất-một-phiên-khác) | Server từ chối phiên hiện tại, thu hồi lặp |
| UC12 | [Đăng xuất các phiên khác](use-cases.md#uc12-đăng-xuất-tất-cả-phiên-khác) | Giữ phiên hiện tại, không cấm login mới sau thao tác |

## Điểm đối chiếu giữa các nguồn

UC01 trong Word nhập đầy đủ thông tin trước khi gửi mã; API tách xin mã với username/email và hoàn tất với toàn bộ dữ liệu. Không giả định server lưu password hoặc dữ liệu đăng ký tạm. Quy tắc đổi username/email khi chờ OTP nằm tại [ràng buộc OTP REGISTER](confirmed-decisions.md#register-otp-binding); UNIQUE database vẫn áp dụng.

UC08 có ba nhánh và endpoint hoàn tất bằng registrationToken. Chính sách [tự liên kết](confirmed-decisions.md#google-linking), [TTL registrationToken](confirmed-decisions.md#google-registration-ttl) và [chống lặp/rollback/mất response](confirmed-decisions.md#google-replay) đã chốt. Không suy ra token dùng một lần tuyệt đối hoặc thêm kho trạng thái tiêu thụ token.

Ví dụ HTTP hiện hành đã được tổng hợp tại [16 API gốc](api/current-contract.md). Danh mục nguồn giữ nguyên ví dụ Excel, gồm TTL access token cũ; không dùng nó thay chính sách hiện hành. [Q13 đã chốt ngày 2026-10-25](confirmed-decisions.md#password-otp-flow): UC05/UC09 gửi OTP cùng mật khẩu mới/xác nhận trong request hoàn tất; không thêm API xác minh riêng hoặc ticket. Quyết định này thay cách chia bước trong Word đã đối chiếu; đợt này không cập nhật hoặc đối chiếu lại Word.

## Chính sách OTP và mật khẩu đã chốt

Áp dụng [chính sách Q05](confirmed-decisions.md#otp-password-policy) cho UC01/UC05/UC07/UC09 theo chức năng tương ứng; [OTP REGISTER](confirmed-decisions.md#register-otp-binding) chỉ gắn email/purpose. Các giá trị TTL, số lần thử/gửi lại và giới hạn mật khẩu được duy trì tại sổ quyết định. [Q06 chốt ngày 2026-10-31](confirmed-decisions.md#otp-email-delivery): commit hash OTP mới/vô hiệu mã cũ trước khi gửi SMTP đồng bộ; lần cấp mã vẫn tính giới hạn dù SMTP lỗi, không tự cấp mã retry. REGISTER/SET_PASSWORD gửi lỗi trả 503; PASSWORD_RESET giữ 200/thông báo chung. Chỉ còn lựa chọn/cấu hình môi trường SMTP chưa chốt.

## Ranh giới transaction

**Đã triển khai ngày 2026-11-10:** UC02 giữ khóa user `PESSIMISTIC_WRITE`, tạo session/refresh token, ký JWT và commit trước khi trả token. Ngày 2026-11-11 bổ sung UC03/UC04 ở isolation READ_COMMITTED: projection owner → khóa user → đọc lại token/phiên → kiểm tra thời gian và thay đổi dữ liệu. Các writer password/status/revoke sau này phải khóa cùng user để tuần tự hóa. Xem [implementation và kiểm thử](user-security.md).

Theo xác nhận ngày 2026-10-22, chi tiết kỹ thuật được lưu ở docs, không bắt buộc lặp trong chuỗi Actor–hệ thống ở Word. Bảng dưới tổng hợp thay đổi phải nhất quán; ngày 2026-10-31 chốt khóa cùng bản ghi user cho lệnh trạng thái cùng user, chưa chọn toàn bộ thứ tự khóa hoặc isolation level.

| Use case/thao tác | Các thay đổi phải cùng transaction |
| --- | --- |
| Xin OTP UC01/UC05/UC09 | Kiểm tra giới hạn gửi, lưu hash OTP mới và vô hiệu mã cũ cùng email/purpose; gửi SMTP sau commit |
| UC01 | Tạo user ACTIVE với PasswordHash, gán READER và đánh dấu OTP đã dùng |
| UC02 | Tạo session và refresh token liên kết đúng user/phiên; chỉ trả cặp token sau commit |
| UC03 | Thu hồi token cũ, tạo refresh token mới và liên kết rotation cùng phiên |
| UC04 | Thu hồi phiên được xác định từ refresh token và các refresh token còn hiệu lực của phiên |
| UC05 | Cập nhật PasswordHash, dùng OTP một lần và thu hồi toàn bộ phiên/refresh token liên quan |
| UC06 cập nhật | Đã triển khai: READ_COMMITTED, khóa user rồi kiểm tra lại user/phiên, chỉ lưu fullName/phoneNumber; trigger Users.UpdatedAt trong transaction; thành công sau commit |
| UC07 | Đã triển khai: READ_COMMITTED, khóa user và kiểm tra lại user/phiên; đổi PasswordHash, thu hồi mọi phiên/token còn hiệu lực kể cả phiên gọi với PASSWORD_CHANGED; giữ lịch sử đã kết thúc; thành công sau commit |
| UC08 identity đã có | Tạo session và refresh token sau khi kiểm tra user ACTIVE |
| UC08 tự liên kết | Thêm identity GOOGLE cho user hiện có, tạo session và refresh token; không tạo user hoặc thay role/password |
| UC08 hoàn tất đăng ký | Tạo user, identity GOOGLE, READER, session và refresh token; xung đột UNIQUE identity rollback toàn bộ |
| UC09 | Cập nhật PasswordHash, dùng OTP một lần và thu hồi toàn bộ phiên/refresh token; giữ identity Google |
| UC11 | Thu hồi phiên đích khác phiên hiện tại cùng refresh token liên quan; không ghi đè thời điểm/lý do thu hồi cũ |
| UC12 | Thu hồi các phiên khác còn hiệu lực cùng refresh token liên quan, giữ phiên có sid đã xác minh |
| Khóa tài khoản qua API nội bộ | Đổi ACTIVE → BANNED, thu hồi mọi phiên và refresh token còn hiệu lực liên quan |
| Mở khóa qua API nội bộ | Đổi BANNED → ACTIVE; không phục hồi phiên/token cũ. Retry/lệnh đồng thời/audit theo Q10 đã chốt |

Chỉ báo thành công và trả token đăng nhập sau commit. Lỗi transaction phải rollback các thay đổi trong transaction, không cập nhật một phần. UC04 áp dụng [ngoại lệ đã chốt](confirmed-decisions.md#logout): không tìm được session chỉ bảo đảm đăng xuất client; database lỗi không được báo thu hồi server thành công. Email và xóa token client không thuộc transaction database.

**UC06 chốt và triển khai ngày 2026-11-12:** [PATCH hồ sơ](confirmed-decisions.md#user-profile-uc06) khóa cùng user với login/refresh/logout, lấy now sau khóa và đọc lại phiên. User không còn/không ACTIVE hoặc phiên thiếu/sai owner/hết hạn/thu hồi trả 401 SESSION_INACTIVE, không lưu. Hai PATCH tuần tự, trường bị bỏ qua không ghi đè; cùng trường theo thứ tự database. Lỗi lưu/dựng DTO/commit rollback; GET READ_COMMITTED/readOnly không thay đổi dữ liệu. Xem [implementation UC06](user-profile.md).

## Tính nhất quán khi có request đồng thời

**UC07 ngày 2026-11-13:** [đổi mật khẩu](user-password.md) dùng cùng khóa user, lấy now sau khóa và kiểm tra lại phiên như PATCH. Login/refresh hoàn tất trước UC07 thì phiên/token vừa tạo thuộc phạm vi thu hồi; login sau UC07 phải dùng mật khẩu mới. Hai UC07 đã qua Bearer vẫn tuần tự: request sau thấy phiên bị thu hồi và trả SESSION_INACTIVE. Chỉ cập nhật bản ghi chưa thu hồi/chưa hết hạn; không ghi đè lịch sử.

Login, refresh, đổi/reset/thiết lập mật khẩu, cập nhật trạng thái và thu hồi phiên phải được tuần tự hóa với thao tác thu hồi liên quan. Không để writer dùng kết quả kiểm tra user/phiên trước thu hồi để cấp token hoặc tạo dữ liệu trái với trạng thái sau transaction. Thu hồi và login mới có thứ tự trước/sau; UC12 không cấm login mới sau thao tác.

Rotation chỉ thành công một lần cho một token; constraint không thay thế kiểm soát cạnh tranh. Tạo user/identity phải tuân thủ UNIQUE và rollback, không bỏ constraint để xử lý xung đột. Việc dùng OTP một lần, đếm lần nhập sai, vô hiệu mã cũ và giới hạn gửi cũng phải giữ đúng [Q05](confirmed-decisions.md#otp-password-policy) khi có request đồng thời. UC03–UC04 đã chọn khóa user trước khi đọc lại token/phiên và isolation READ_COMMITTED; không nạp entity token trước khóa. Những writer khác phải giữ cùng quy ước; các chi tiết OTP chưa được lựa chọn bởi đợt này.

## Quản trị

Trách nhiệm Admin/Borrow và quyền nội bộ nằm tại [tích hợp nội bộ](api/internal-integration.md). Chính sách [khóa/mở khóa](confirmed-decisions.md#account-lifecycle) và [role ngoài phạm vi](confirmed-decisions.md#roles) giữ nguyên; [Q10 chốt ngày 2026-10-31](confirmed-decisions.md#admin-status-contract) bổ sung cùng trạng thái trả 200, tuần tự hóa theo bản ghi user, đối soát timeout và cấm khóa đối tượng ADMIN. Admin ghi audit yêu cầu/kết quả, User ghi sự kiện; không thêm bảng hoặc cam kết audit ngoài database nguyên tử với transaction.

## Quyết định xác thực đã chốt

**Cập nhật ngày 2026-11-10:** [giai đoạn chức năng mặc định tin tưởng service](confirmed-decisions.md#deferred-service-auth). Bỏ qua bước danh tính/quyền service của Admin/Borrow/Gateway khi code chức năng; thứ tự API trạng thái là actor Admin → nghiệp vụ, còn introspection vẫn kiểm tra user token/phiên. Các yêu cầu Service JWT/thứ tự đầy đủ bên dưới là thiết kế đích sau khi hoàn tất tất cả chức năng. Không hoãn xác thực user/actor, ownership hoặc các yêu cầu transaction/cạnh tranh.

Đọc [token người dùng và Gateway](confirmed-decisions.md#user-tokens), [thời hạn session](confirmed-decisions.md#session-lifetime). [Bộ introspection chốt ngày 2026-10-26](confirmed-decisions.md#introspection-contract) xác nhận request/response, lỗi/header và nguồn roles hiện tại từ UserRoles/Roles trong database; không chỉ dựa vào role trong JWT hoặc role Gateway. Kiểm tra danh tính/quyền Gateway trước khi xử lý user token; lỗi danh tính/hạ tầng không chuyển thành active false. [User access token Q14](confirmed-decisions.md#access-token-key-pair) đã chốt issuer chung, audience, kid và quy trình khóa ngày 2026-10-31. Cấu hình Service JWT còn [Q03](open-decisions.md#q03)/[Q11a](open-decisions.md#q11a), không áp cấu hình user JWT sang service. User JWT đã có cấu hình runtime từ đợt UC02; Service JWT/introspection chưa triển khai, API quản lý role vẫn ngoài phạm vi.

[Q03 chốt ngày 2026-10-31](confirmed-decisions.md#internal-service-auth): Service JWT riêng cho Admin/Borrow, quyền users:read/users:status:write và tokens:introspect cho Gateway; actor Admin được chứng minh bằng access token trong X-Admin-Access-Token, kiểm tra phiên/ACTIVE/ADMIN hiện tại. Quyền đã chọn không tự chốt cách biểu diễn claim hoặc các cấu hình JWT bị hoãn. Thứ tự API nội bộ: danh tính service → quyền service → actor khi cần → nghiệp vụ.

## Quản lý phiên và thiết bị

[API phiên](api/sessions.md) duy trì HTTP cho UC10–UC12 và introspection; [schema](data-model.md) duy trì cột/constraint. Thông tin thiết bị chỉ là metadata. Chính sách [thu hồi phiên](confirmed-decisions.md#session-revocation) và [retention refresh token](confirmed-decisions.md#refresh-retention) không đồng nghĩa đã có tác vụ thực thi hoặc đã chốt thời gian xóa UserSessions.
