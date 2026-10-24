# Quyết định đã xác nhận

Nguồn: chủ dự án xác nhận ngày 2026-10-17, mở rộng phạm vi ngày 2026-10-19 và xác nhận bổ sung theo ngày ghi tại từng mục. Biên tập, liên kết lại ngày 2026-10-24; không tạo quyết định nghiệp vụ mới. Đây là nơi duy trì đầy đủ chính sách đã chốt, chưa phải hành vi đang chạy. Tra cứu HTTP tại [bản đồ API](api/README.md), hiện trạng tại [mục lục docs](README.md#trạng-thái-và-nguồn-sự-thật).

## Phạm vi

Quyết định 2026-10-19 thay giới hạn 6 bảng/16 API trước đây: thêm UserSessions, 3 API quản lý phiên và introspection RFC 7662 dành cho Gateway. Tổng thiết kế 7 bảng/20 API. Giữ refresh token trong JSON cho Web/Android. **Chỉ thêm migration và docs, chưa code API.** Gateway dùng **Service JWT**. V6 **chỉ áp dụng khi RefreshTokens rỗng**, không tự thu hồi/xóa dữ liệu cũ. Không thêm Redis, JWT blacklist hoặc API quản lý role. [Danh mục nguồn](api/source-catalog.md) giữ nguyên 16 API ban đầu.

<a id="account-lifecycle"></a>

## Vòng đời tài khoản — Q01 đã chốt

Chỉ có `ACTIVE` và `BANNED`. “Kích hoạt tài khoản” là mở khóa/kích hoạt lại, tức `BANNED → ACTIVE`; không có PENDING hoặc trạng thái chờ kích hoạt khác.

| Sự kiện | Kết quả đã chốt |
| --- | --- |
| Đăng ký Local | Xác minh OTP email thành công trước khi tạo user; user mới ACTIVE, role mặc định READER |
| Đăng ký Google | Xác minh Google hợp lệ và hoàn tất đăng ký trước khi tạo user; user mới ACTIVE, role mặc định READER |
| Khóa ACTIVE → BANNED | Theo thiết kế mở rộng 2026-10-19, thu hồi mọi phiên và refresh token còn hiệu lực liên quan; user BANNED không được đăng nhập hoặc refresh |
| Mở khóa/kích hoạt lại BANNED → ACTIVE | Người dùng phải đăng nhập lại; không khôi phục phiên hoặc refresh token đã thu hồi |

Admin Service điều phối và gọi `PATCH /api/v1/internal/users/{id}/status` của User Service. Không tạo API kích hoạt riêng. Thiết kế đã chọn Gateway introspection kiểm tra user/phiên: sau khi transaction khóa hoặc thu hồi phiên commit, access token của phiên mất hiệu lực bị từ chối ở lần kiểm tra tiếp theo, dù JWT còn hạn. Mở khóa không làm phiên cũ hợp lệ trở lại. Không cam kết hủy request đã vượt kiểm tra trước commit; cơ chế này chưa triển khai.

<a id="roles"></a>

## Role — Q02 đã chốt phạm vi

Roles và UserRoles hỗ trợ quan hệ nhiều–nhiều. Các role hiện có: READER, LIBRARIAN, ADMIN. Về thiết kế database, user có thể đồng thời có ADMIN và role khác. Đăng ký mới mặc định gán READER; người dùng không được tự nâng quyền.

V1 không triển khai API gán, thêm, gỡ hoặc thay thế role. Việc gán/thu hồi ADMIN chỉ được thực hiện bởi cơ chế quản trị có thẩm quyền; xác nhận này không lựa chọn một cơ chế cụ thể hoặc cho phép client trực tiếp sửa database. Quy tắc thay thế hay thêm/gỡ role và hiệu lực access token sau thay đổi role để dành cho phiên bản mở rộng, không phải điều kiện để bổ sung API vào V1.

<a id="user-tokens"></a>

## Token — Q03/Q04 đã chốt một phần

- User Service ký JWT Access Token bằng **RS256 với Private Key**. Các microservice xác minh bằng **Public Key**.
- Ngày 2026-10-20 chốt TTL access token **1 giờ (3600 giây)** và refresh token **7 ngày (604800 giây)**. `1800` trong Excel là ví dụ cũ, không phải TTL đã chốt. Session có hạn cố định **30 ngày**, theo quy tắc bên dưới; chưa có cấu hình runtime.
- Refresh token là **opaque token**, lưu **hash trong PostgreSQL**, có **Refresh Token Rotation**.
- Khóa tài khoản thu hồi toàn bộ refresh token còn hiệu lực. BANNED không được đăng nhập/refresh; mở khóa không khôi phục token đã thu hồi.
- Đã cho phép session revocation; không triển khai JWT Blacklist hoặc Redis.

Thu hồi refresh token **không tự vô hiệu JWT đã phát**. Đã chọn Gateway introspection kiểm tra JWT và phiên, dùng Service JWT xác thực Gateway. Chưa có implementation. Thuật toán, issuer, audience, subject, TTL, bên phát và cách phân phối khóa của Service JWT còn PENDING DECISION, không suy ra từ RS256 của access token người dùng.

Q04 đã chốt introspection kiểm tra phiên và không cache active response ở giai đoạn đầu. Không mở lại lựa chọn cơ chế; các phần cấu hình còn thiếu theo dõi tại [Q11a](open-decisions.md#q11a) và [Q14](open-decisions.md#q14).

Cơ chế xác thực Admin Service → User Service là **PENDING DECISION**: chưa chọn Service JWT, API Key hay phương án khác. Quyết định RS256 cho access token người dùng không quyết định cách xác thực service-to-service. Cụm “Service Token” trong Excel chưa phải một giao thức đã được chọn.

<a id="session-lifetime"></a>

## Thời hạn session — đã chốt ngày 2026-10-20

- Khi đăng nhập thành công, tạo session với `ExpiresAt = CreatedAt + 30 ngày` (2592000 giây). Refresh/hoạt động người dùng không gia hạn mốc này; rotation giữ nguyên `sid`.
- Mỗi access token có `exp = thời điểm cấp + 1 giờ`; mỗi refresh token mới có `ExpiresAt = thời điểm cấp + 7 ngày`, kể cả token cấp khi rotation. Không cắt ngắn thời hạn riêng của token theo hạn session.
- Token chỉ được dùng để truy cập/refresh khi cả token và session còn hiệu lực. Khi `now >= UserSessions.ExpiresAt`, introspection trả `active: false` cho access token và refresh bị từ chối, dù thời hạn riêng của token chưa hết. Người dùng phải đăng nhập lại để tạo session mới.
- Ví dụ refresh vào ngày 28 tạo refresh token có hạn riêng đến ngày 35, nhưng chỉ dùng để refresh đến trước mốc hết hạn session ngày 30. Quy tắc này không thay đổi ngoại lệ xác định session để logout bằng token cũ ở UC04.
- Giai đoạn đầu không dùng `LastSeenAt` làm thời hạn không hoạt động; trường này phục vụ hiển thị hoạt động gần nhất. Refresh token 7 ngày không đồng nghĩa tự động đăng xuất sau 7 ngày không thao tác, vì client có thể refresh nền. Thu hồi phiên hoặc khóa tài khoản vẫn có thể kết thúc quyền sử dụng trước hạn.

Đây là chính sách thiết kế đã chốt, chưa triển khai API; TTL Service JWT vẫn chờ quyết định. TTL registration token được chốt riêng bên dưới.

<a id="google-linking"></a>

## Tự liên kết Google với tài khoản Local — Q07 chốt một phần ngày 2026-10-20

UC08 A1 tự liên kết Google identity chưa tồn tại với tài khoản Local có cùng email đã được Google xác minh, không yêu cầu nhập lại mật khẩu Local hoặc thêm bước xác thực chủ tài khoản Local.

Server phải xác minh Google ID Token hợp lệ trước khi sử dụng `sub`, email và trạng thái email đã xác minh; không coi email hoặc cờ xác minh do client tự khai là bằng chứng danh tính. Khi đủ điều kiện liên kết, thêm UserIdentities với `Provider = GOOGLE`, `ProviderUserId = Google sub`, `UserId` của tài khoản hiện có; không tạo Users mới, không thay mật khẩu hoặc role. User phải ACTIVE để đăng nhập, tạo session và nhận token; user BANNED không được cấp token.

Đây là quyết định nghiệp vụ đã chốt, chưa triển khai API. Danh sách Google client ID được server chấp nhận vẫn **PENDING DECISION tại Q07**, không tự chấp nhận mọi audience.

<a id="google-registration-ttl"></a>

## Thời hạn registrationToken — đã chốt ngày 2026-10-20

`registrationToken` dùng để hoàn tất đăng ký Google có thời hạn **10 phút (600 giây) kể từ lúc cấp**, không gia hạn. Khi người dùng sửa username trùng hoặc dữ liệu không hợp lệ, có thể gửi lại trong thời hạn còn lại; thao tác này không kéo dài TTL. Khi token hết hạn, người dùng thực hiện lại bước xác minh Google để nhận token mới.

Token gắn với danh tính Google đã được server xác minh, chỉ dùng cho bước hoàn tất đăng ký, không thay thế access token hoặc refresh token. TTL 10 phút không tự bảo đảm dùng một lần; đã chốt chống đăng ký lặp dựa trên Google identity như bên dưới. Chính sách OTP và mật khẩu được chốt riêng bên dưới. Chưa có implementation.

<a id="google-replay"></a>

## Chống đăng ký Google lặp — Q08 đã chốt ngày 2026-10-20

- Server xác minh registrationToken còn hạn, hợp lệ và đúng mục đích hoàn tất đăng ký; lấy Google sub và email từ danh tính đã được xác minh trong token, không nhận danh tính thay thế do client tự khai.
- Tra cứu UserIdentities theo `Provider = GOOGLE` và `ProviderUserId = Google sub`. Nếu identity đã tồn tại, từ chối hoàn tất đăng ký; không tạo user, không tạo session và không cấp token đăng nhập. Người dùng chuyển sang luồng đăng nhập Google.
- Nếu identity chưa tồn tại, tạo user, identity, role READER, session và refresh token trong cùng transaction. Chỉ trả token đăng nhập sau khi commit. Ràng buộc UNIQUE `(Provider, ProviderUserId)` ngăn request đồng thời tạo cùng identity; request gặp xung đột rollback toàn bộ phần đăng ký.
- Username trùng hoặc dữ liệu sai: cho sửa và gửi lại khi registrationToken còn hạn, không gia hạn TTL. Transaction đã rollback có thể thử lại khi token còn hạn.
- Nếu đăng ký đã commit nhưng client không nhận được response, người dùng đăng nhập Google; không dùng registrationToken để lấy lại hoặc cấp thêm token đăng nhập. Token hết hạn phải xác minh Google lại.

Phạm vi vẫn 7 bảng, không thêm Redis hoặc kho lưu trạng thái tiêu thụ từng token. Đây là chống đăng ký lặp dựa trên sự tồn tại của identity, không phải bảo đảm dùng một lần tuyệt đối theo từng token. Nếu identity bị xóa khi token còn hạn, cơ chế này không chứng minh được token đã từng dùng; chủ dự án đã chấp nhận giới hạn này. Quyết định không cấp phép thêm chức năng xóa identity. Chưa triển khai API.

<a id="otp-password-policy"></a>

## OTP và mật khẩu — Q05 đã chốt ngày 2026-10-20

Áp dụng OTP email cho `REGISTER`, `PASSWORD_RESET`, `SET_PASSWORD`:

- Mã gồm 6 chữ số, hiệu lực 10 phút (600 giây) kể từ lúc cấp, chỉ dùng một lần.
- Mỗi mã cho phép tối đa 5 lần nhập sai. Sau lần sai thứ 5, mã không còn được chấp nhận kể cả khi nhập đúng; người dùng phải yêu cầu mã mới theo giới hạn gửi.
- Hai lần cấp mã cho cùng email và purpose cách nhau tối thiểu 60 giây; tối đa 5 lần trong cửa sổ trượt 1 giờ cho cùng email và purpose.
- Khi cấp mã mới, mã cũ cùng email và purpose mất hiệu lực. Gửi lại không xóa lịch sử giới hạn gửi trong giờ đó.
- Đánh dấu mã đã dùng và tạo tài khoản/đổi hoặc thiết lập mật khẩu phải nằm trong cùng transaction.
- Không chuyển tài khoản sang `BANNED` vì nhập sai OTP. Giới hạn này áp dụng cho việc xác minh mã.
- Chủ dự án bỏ yêu cầu bổ sung giới hạn request theo IP tại Gateway khỏi đề xuất này; không đặt ngưỡng IP chờ chốt.

Chính sách mật khẩu dùng chung khi đăng ký, đổi, reset và thiết lập mật khẩu lần đầu:

- Dài **6–128 ký tự**, cho phép khoảng trắng và Unicode.
- Không bắt buộc tổ hợp chữ hoa, chữ thường, chữ số hoặc ký tự đặc biệt.
- Không tự trim hoặc cắt ngắn mật khẩu.
- Không kiểm tra từ chối mật khẩu phổ biến/đã bị lộ theo danh sách.
- Không bắt đổi mật khẩu định kỳ nếu không có dấu hiệu lộ lọt.

Đây là chính sách thiết kế đã xác nhận, chưa triển khai kiểm tra hoặc cấu hình runtime. Q06 vẫn mở cho dịch vụ email và cách xử lý lỗi gửi/retry; không giả định database và dịch vụ email commit nguyên tử. Q08 đã chốt cơ chế chống đăng ký Google lặp, quy tắc cấp mã mới làm mất hiệu lực mã cũ và ràng buộc mã REGISTER bên dưới.

<a id="register-otp-binding"></a>

## Ràng buộc OTP đăng ký — Q08 đã chốt ngày 2026-10-20

OTP đăng ký chỉ gắn với **email và `Purpose = REGISTER`**, không gắn với username. Người dùng có thể đổi username giữa bước xin mã và hoàn tất đăng ký mà vẫn dùng mã cho cùng email nếu mã đáp ứng đầy đủ điều kiện hiệu lực đã chốt. Việc đổi username không gia hạn mã hoặc đặt lại số lần thử.

Đổi email phải xin mã cho email mới; không được dùng mã cấp cho email cũ. Mã REGISTER không dùng cho PASSWORD_RESET hoặc SET_PASSWORD. Username vẫn tuân theo quy tắc dữ liệu và ràng buộc duy nhất hiện có; việc API request-code nhận userName không có nghĩa mã bị ràng buộc với trường này. Đây là thiết kế, chưa triển khai; không cần thêm cột username vào VerificationCodes.

<a id="refresh-reuse"></a>

## UC03 — dùng lại refresh token đã rotation đã chốt ngày 2026-10-20

Khi UC03 nhận lại refresh token đã rotation, hệ thống từ chối cấp token mới; không thu hồi cả session chỉ vì request dùng lại token cũ. Token cũ tiếp tục bị thu hồi, không được rotation lần hai. Token thay thế và session không bị thu hồi do sự kiện này, nhưng vẫn phải đáp ứng các điều kiện hiệu lực thông thường khi sử dụng.

Quyết định này không thay đổi logout UC04: token cũ còn bản ghi vẫn có thể xác định session để thực hiện yêu cầu đăng xuất và thu hồi session. Chính sách lưu giữ lịch sử token đã chốt bên dưới; hợp đồng lỗi chi tiết còn chờ chốt. Đây là thiết kế, chưa triển khai API.

<a id="logout"></a>

## UC04 — xử lý logout đã chốt ngày 2026-10-20

Chủ dự án chấp nhận logout thu hồi cả session. Refresh token đã rotation, bị thu hồi hoặc hết hạn vẫn được dùng để xác định đúng session nếu server còn lưu bản ghi; không được dùng token đó để cấp token mới, chọn session khác hoặc thu hồi toàn bộ tài khoản.

- Nếu xác định được session còn hiệu lực: thu hồi session và các refresh token còn hiệu lực liên quan trong cùng transaction.
- Nếu session đã thu hồi/hết hạn: trả kết quả thành công, không ghi đè thời điểm/lý do thu hồi trước đó.
- Nếu token không tồn tại hoặc không còn xác định được session: trả kết quả đăng xuất chung, không tiết lộ token có tồn tại; chỉ xác nhận đăng xuất phía client, không khẳng định session đã thu hồi phía server.
- Nếu database lỗi hoặc transaction không hoàn tất: trả lỗi, không thông báo thu hồi thành công. Client có thể xóa token để thoát trên thiết bị nhưng phải thông báo chưa xác nhận được việc thu hồi phía server.

Client xóa access token và refresh token khi đăng xuất. Hậu điều kiện phân biệt thu hồi phía server thành công với chỉ xóa token phía client. Chủ dự án chấp nhận đánh đổi: người giữ refresh token cũ có thể buộc chính session gắn với token đó đăng xuất. Chính sách lưu giữ lịch sử token đã chốt bên dưới; hợp đồng HTTP chi tiết còn chờ chốt. Đây là yêu cầu thiết kế, chưa triển khai API; chủ dự án sẽ cập nhật bản DOCX.

<a id="refresh-retention"></a>

## Lưu giữ lịch sử refresh token — đã chốt ngày 2026-10-20

Giữ toàn bộ bản ghi RefreshTokens và liên kết rotation của một session đến khi session kết thúc, cộng thêm 7 ngày (604800 giây). Không xóa riêng token đã rotation, thu hồi hoặc hết hạn trong khi session còn hiệu lực.

- Session chưa bị thu hồi: thời điểm đủ điều kiện dọn là `UserSessions.ExpiresAt + 7 ngày`.
- Session đã bị thu hồi: thời điểm đủ điều kiện dọn là `min(UserSessions.RevokedAt, UserSessions.ExpiresAt) + 7 ngày`.
- Tác vụ dọn dự kiến chạy mỗi ngày một lần, theo lô; chỉ dọn phiên đã kết thúc và đến thời điểm đủ điều kiện. Xóa toàn bộ refresh token cùng phiên trong một transaction, không chọn xóa từng token chỉ theo RefreshTokens.ExpiresAt.
- Sau khi dọn, refresh bằng token cũ bị từ chối vì không tìm thấy; logout theo UC04 trả kết quả chung và client xóa token, không khẳng định vừa thu hồi được phiên phía server.

Thời gian giữ thêm 7 ngày không gia hạn hiệu lực token/session. Chính sách này không thêm bảng hoặc Redis, không quyết định thời gian lưu/xóa bản ghi UserSessions. Chưa triển khai tác vụ dọn hoặc chạy xóa dữ liệu; migration hiện có giữ nguyên.

<a id="session-revocation"></a>

## UC11 và phạm vi đặc tả — tổng hợp bàn giao 2026-10-22

UC11 chỉ đăng xuất một phiên khác của chính người dùng. UI đánh dấu phiên hiện tại và không cho chọn phiên đó; server vẫn kiểm tra sessionId khác sid đã xác minh. Request trực tiếp chọn phiên hiện tại bị từ chối, không thay đổi phiên/token. Đăng xuất bản thân dùng UC04; UC12 giữ phiên hiện tại khi thu hồi tất cả phiên khác. Quy tắc này thay đề xuất cũ trong API phiên cho phép chọn phiên hiện tại.

Chủ dự án không yêu cầu thêm luồng ngoại lệ riêng về đăng ký Local trùng trong lúc chờ OTP hoặc mô tả kiểm tra trùng lần hai; vẫn giữ ràng buộc UNIQUE hiện có, không hiểu quyết định này thành cho phép lưu dữ liệu trùng. Word tập trung chuỗi tương tác Actor–hệ thống; TTL, chính sách, transaction và rollback được lưu trong docs, không bắt buộc lặp trong Word.

Các vấn đề còn lại được theo dõi tại [quyết định còn mở](open-decisions.md).
