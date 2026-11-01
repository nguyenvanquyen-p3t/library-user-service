# Quyết định đã xác nhận

Nguồn: chủ dự án xác nhận ngày 2026-10-17, mở rộng phạm vi ngày 2026-10-19 và xác nhận bổ sung theo ngày ghi tại từng mục. Biên tập, liên kết lại ngày 2026-10-24; bổ sung Q13, schema JSON và HTTP/lỗi của Q09 ngày 2026-10-25, hợp đồng ba API phiên ngày 2026-10-26; chốt Q03/Q06/Q10, lỗi tích hợp Q09 và user access token Q14 ngày 2026-10-31. Ban đầu hoãn cấu hình JWT để nghiên cứu, sau đó chốt riêng Q14; Service JWT Q03/Q11a vẫn mở. Đây là nơi duy trì đầy đủ chính sách đã chốt, chưa phải hành vi đang chạy. Tra cứu HTTP tại [bản đồ API](api/README.md), hiện trạng tại [mục lục docs](README.md#trạng-thái-và-nguồn-sự-thật).

## Phạm vi

**Bổ sung phạm vi ngày 2026-11-01:** chủ dự án yêu cầu bắt đầu xây dựng OpenAPI và Swagger UI. Cho phép đặc tả và hạ tầng tài liệu liên quan; 20 API nghiệp vụ vẫn ở mức thiết kế. Xem [hướng dẫn OpenAPI](api/openapi.md). Các câu “chưa tạo OpenAPI” trong quyết định lịch sử bên dưới mô tả thời điểm xác nhận tương ứng.

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
- Bổ sung ngày 2026-10-31: dùng [một cặp khóa cố định cho user access token](#access-token-key-pair), tái sử dụng qua các lần cấp token và khởi động lại; private key ký, public key xác minh chữ ký, không phải giải mã JWT.
- Ngày 2026-10-20 chốt TTL access token **1 giờ (3600 giây)** và refresh token **7 ngày (604800 giây)**. `1800` trong Excel là ví dụ cũ, không phải TTL đã chốt. Session có hạn cố định **30 ngày**, theo quy tắc bên dưới; chưa có cấu hình runtime.
- Refresh token là **opaque token**, lưu **hash trong PostgreSQL**, có **Refresh Token Rotation**.
- Khóa tài khoản thu hồi toàn bộ refresh token còn hiệu lực. BANNED không được đăng nhập/refresh; mở khóa không khôi phục token đã thu hồi.
- Đã cho phép session revocation; không triển khai JWT Blacklist hoặc Redis.

Thu hồi refresh token **không tự vô hiệu JWT đã phát**. Đã chọn Gateway introspection kiểm tra JWT và phiên, dùng Service JWT xác thực Gateway. Chưa có implementation. Thuật toán, issuer, audience, subject, TTL, bên phát và cách phân phối khóa của Service JWT còn PENDING DECISION, không suy ra từ RS256 của access token người dùng.

Q04 đã chốt introspection kiểm tra phiên và không cache active response ở giai đoạn đầu. Không mở lại lựa chọn cơ chế; cấu hình user access token đã chốt tại [Q14](#access-token-key-pair), cấu hình Service JWT còn [Q03](open-decisions.md#q03)/[Q11a](open-decisions.md#q11a).

Cơ chế xác thực Admin/Borrow → User Service đã được [chốt bổ sung ngày 2026-10-31](#internal-service-auth): Service JWT riêng và access token của actor Admin khi cập nhật trạng thái. Cụm “Service Token” trong Excel là nguồn lịch sử. Cấu hình JWT vẫn PENDING DECISION; RS256 của access token người dùng không quyết định thuật toán Service JWT.

<a id="access-token-key-pair"></a>

## Cặp khóa access token người dùng — Q14 đã chốt ngày 2026-10-31

Sau khi hoãn đề xuất cấu hình JWT để nghiên cứu, chủ dự án xác nhận dùng một cặp public key/private key cố định cho access token người dùng. User Service tái sử dụng cặp khóa qua các lần cấp token; không sinh cặp mới theo user, lần đăng nhập, từng token hoặc mỗi lần khởi động ứng dụng.

Private key dùng ký RS256; public key tương ứng dùng xác minh chữ ký. Access token được ký, chưa có thiết kế mã hóa JWT; thao tác đọc payload không phải giải mã bằng public key. Giữ nguyên TTL 1 giờ, sub/sid và kiểm tra phiên qua introspection đã chốt.

Bổ sung cùng ngày: chủ dự án chọn thư mục `backend/.secrets/` để chứa khóa cục bộ, được loại khỏi Git bằng `.gitignore`. Khi chạy từ backend/, đường dẫn tương đối bắt đầu bằng `.secrets/`. Theo yêu cầu chủ dự án, đã sinh một cặp RSA 3072 bit bằng JDK 25 vào `user-access-private.pem` (PKCS#8 PEM, chưa mã hóa bằng mật khẩu) và `user-access-public.pem` (X.509 SubjectPublicKeyInfo PEM). Đã nạp lại hai file để kiểm tra ký/xác minh SHA256withRSA tương ứng RS256, xác minh thành công dữ liệu gốc và từ chối dữ liệu sửa đổi. Không ghi nội dung khóa vào docs/log; chưa cấu hình ứng dụng đọc khóa hoặc triển khai JWT/API.

Bổ sung sau đó cùng ngày: chủ dự án chấp nhận phần cấu hình/vận hành user access token với một issuer chung, không chia issuer theo môi trường. Q14 đã chốt trong phạm vi dưới đây; không áp dụng sang Service JWT Q03/Q11a.

| Thành phần | Giá trị/quy tắc đã xác nhận |
| --- | --- |
| `iss` | `urn:library:user-service`, dùng chung, không thêm hậu tố dev/prod |
| `aud` | `["library-api"]`, xem toàn bộ Library API là một nhóm tài nguyên nhận user token |
| Header `kid` | `user-access-v1` cho cặp khóa hiện tại; dùng mã mới khi thay khóa, ví dụ user-access-v2 |
| Chữ ký và thời hạn | Giữ RS256, TTL 1 giờ và sub/sid theo quyết định trước đây |

Bên xác minh phải kiểm tra đúng issuer/audience đã cấu hình ngoài chữ ký và thời hạn. Audience chung không thay kiểm tra role/quyền nghiệp vụ. User Service kiểm tra token và phiên khi introspection; Gateway tiếp tục gọi introspection, không cache kết quả active, đồng thời đối chiếu audience với Library API.

**Cấp và phân phối khóa:** giữ hai file trong backend/.secrets/ cho việc chạy cục bộ. User Service đọc private key để ký và public key để xác minh khi introspection; service nào tự xác minh JWT chỉ được cấp public key. V1 phân phối public key bằng file/cấu hình triển khai, chưa thêm endpoint JWKS. Nhận diện public key theo kid trong danh sách khóa được tin cậy; kid không tự cấp độ tin cậy cho một khóa do client cung cấp.

Khi triển khai, cấp file khóa qua cấu hình triển khai, cấu hình đường dẫn theo nơi chạy và giới hạn quyền đọc private key cho User Service. Thiếu khóa hoặc khóa không hợp lệ thì báo lỗi khởi động, không âm thầm sinh cặp khóa mới. Không bổ sung issuer tách môi trường hoặc Service JWT vào quyết định này.

**Thay khóa có kiểm soát:** tiếp tục dùng ổn định cặp hiện tại, chưa cần tự động hóa hoặc ấn định lịch rotation. Khi chủ động thay: cấp public key mới cho bên xác minh trước; chuyển User Service sang private key/kid mới; giữ public key cũ đến khi toàn bộ token cũ hết hạn, tính cả dung sai đồng hồ nếu có; sau đó loại public key cũ. Nếu private key bị lộ thì ngừng chấp nhận khóa cũ ngay, không chờ token hết hạn. Các bước cấp/thay khóa là quy trình thiết kế đã chốt, chưa được triển khai trong ứng dụng; chưa chọn giá trị dung sai đồng hồ bằng quyết định này.

Chỉ cập nhật docs, không tạo/thay file khóa, cấu hình security, OpenAPI hoặc code API trong lần chốt Q14 này. Việc đã có hai file khóa không đồng nghĩa đã triển khai JWT hoặc hoàn tất cấu hình Service JWT.

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
- Bổ sung ngày 2026-10-26: email trong registrationToken đã được tài khoản khác sử dụng trước khi hoàn tất trả HTTP 409 EMAIL_ALREADY_EXISTS, rollback đăng ký; người dùng quay lại đăng nhập Google theo luồng đã xác nhận. Không tự liên kết hoặc cấp token ở request hoàn tất bị từ chối.
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

Đây là chính sách thiết kế đã xác nhận, chưa triển khai kiểm tra hoặc cấu hình runtime. [Q06 được chốt bổ sung ngày 2026-10-31](#otp-email-delivery) về SMTP, lỗi gửi/retry; không giả định database và dịch vụ email commit nguyên tử. Q08 đã chốt cơ chế chống đăng ký Google lặp, quy tắc cấp mã mới làm mất hiệu lực mã cũ và ràng buộc mã REGISTER bên dưới.

<a id="password-otp-flow"></a>

## Xác minh OTP khi reset/thiết lập mật khẩu — Q13 đã chốt ngày 2026-10-25

Chủ dự án xác nhận UC05 và UC09 gửi OTP cùng mật khẩu mới và xác nhận mật khẩu trong một request hoàn tất. Server xác minh OTP và kiểm tra mật khẩu trong request này; chỉ lưu mật khẩu khi mọi điều kiện hợp lệ. Không thêm API xác minh OTP riêng trước bước nhập mật khẩu hoặc token/ticket xác minh. Phạm vi giữ nguyên 7 bảng/20 API.

- UC05 dùng `POST /api/v1/auth/reset-password` với `email`, `verificationCode`, `newPassword`, `confirmPassword`; không yêu cầu đăng nhập.
- UC09 dùng `POST /api/v1/users/me/password/set` với `verificationCode`, `newPassword`, `confirmPassword`; yêu cầu phiên đăng nhập hợp lệ, email lấy từ tài khoản đang xác thực.
- Đánh dấu OTP đã dùng, lưu mật khẩu và thu hồi mọi phiên/refresh token liên quan trong cùng transaction. Chính sách OTP/mật khẩu và yêu cầu đăng nhập lại giữ nguyên.

Quyết định này thay cách chia bước trong Word được đối chiếu ngày 2026-10-22; file Word chưa được cập nhật hoặc đối chiếu lại trong đợt này. Q13 chốt cách xác minh và nhóm trường gửi chung; schema JSON và HTTP/lỗi đã được chốt riêng ở các mục Q09 bên dưới. Các chi tiết chưa được nêu vẫn thuộc Q09 còn mở. Đây là thiết kế đã xác nhận, chưa triển khai API.

<a id="json-schema-contract"></a>

## Schema JSON — Q09 chốt một phần ngày 2026-10-25

Chủ dự án xác nhận đề xuất giữ envelope hiện có, bổ sung quy tắc bắt buộc/null/validation và tái sử dụng schema. Hợp đồng HTTP chi tiết duy trì tại [quy tắc schema JSON](api/json-schema.md), áp dụng cho 20 API hiện tại; chỉ cập nhật docs, chưa tạo OpenAPI hoặc code API.

- Envelope luôn có `success`, `messageCode`, `message`, `data`; `data` theo schema cụ thể hoặc null khi không trả dữ liệu. Introspection giữ form request và JSON RFC 7662 không bọc envelope.
- Chốt bảng trường request bắt buộc/không null; `phoneNumber` khi đăng ký được bỏ qua hoặc null. Request JSON từ chối trường ngoài danh sách, dùng `additionalProperties: false` khi mô tả schema.
- Chốt validation theo V3 và chính sách mật khẩu/OTP hiện hành; số điện thoại là chuỗi 8–15 chữ số, có thể bắt đầu bằng `+`, không cố định đầu số Việt Nam. User ID integer dương/int32, session ID UUID; thời gian dạng chuỗi có múi giờ, response UTC.
- PATCH hồ sơ: bỏ qua trường để giữ nguyên; `fullName` không null/trắng; `phoneNumber: null` để xóa, chuỗi rỗng bị từ chối; body phải có ít nhất một trường hợp lệ.
- Tái sử dụng schema hồ sơ/cặp token; response hồ sơ luôn có `phoneNumber` có thể null, `roles`/`linkedProviders` luôn là mảng. Giữ biến thể Google riêng và schema tối thiểu của API nội bộ.

Required/null response và biến thể Google được chốt bổ sung tại [schema response](#response-schema-contract), HTTP/lỗi tại mục bên dưới; [ba API phiên](#session-response-contract) và [introspection](#introspection-contract) chốt ngày 2026-10-26. Q09 chưa đóng: tình huống/mã/chi tiết ngoài danh mục vẫn cần xác nhận. Không coi việc chốt các schema này là đã chốt xác thực service hoặc mọi hành vi tích hợp.

<a id="response-schema-contract"></a>

## Schema response — Q09 chốt bổ sung ngày 2026-10-25

Chủ dự án xác nhận đề xuất schema response sau khi làm rõ required/null, tái sử dụng cấu trúc và các biến thể Google. Chi tiết trường/kiểu và phạm vi áp dụng duy trì tại [schema response](api/json-schema.md#responses); chỉ cập nhật tài liệu, chưa tạo OpenAPI hoặc code API.

- Mọi trường liệt kê trong UserProfile, TokenPair, OtpRequestResult, InternalUserSummary, UserStatusResult và GoogleRegistrationResult đều bắt buộc xuất hiện. Chỉ UserProfile.phoneNumber nhận null; các trường khác của các schema này không null. Mảng rỗng trả `[]`. Giữ nguyên nhóm trường hiện hành, `data.user`/`data.token` khi hoàn tất đăng ký Google và `data: null` ở bốn thao tác không trả dữ liệu của 16 API gốc.
- Ba biến thể đăng nhập Google giữ hai cờ boolean bắt buộc: identity đã có false/false, tự liên kết false/true, cần đăng ký true/false theo thứ tự isNewUser/accountLinked. Nhánh đăng nhập có nhóm token và cờ; nhánh cần đăng ký có nhóm dữ liệu đăng ký và cờ.
- Nhánh cần đăng ký luôn có email, fullName, suggestedUserName, registrationToken và hai cờ. email/registrationToken không null; fullName nhận null khi không có tên phù hợp từ Google, suggestedUserName nhận null khi không tạo được gợi ý hợp lệ. Gợi ý username không giữ chỗ hoặc bảo đảm còn trống. Request hoàn tất đăng ký và hồ sơ sau đăng ký vẫn yêu cầu họ tên hợp lệ, không null.
- Client bỏ qua trường response mới chưa sử dụng; backend chỉ trả trường được đưa vào hợp đồng, dùng DTO response riêng. Giữ quy tắc request JSON từ chối trường lạ.

Schema phiên/phân trang, revokedCount và ba mã thành công API phiên đã được [chốt bổ sung ngày 2026-10-26](#session-response-contract); [danh mục mã phản hồi](#response-code-contract) và [introspection](#introspection-contract) chốt cùng ngày. Tình huống/mã ngoài danh mục vẫn thuộc Q09 còn mở. Xác nhận này không lựa chọn cơ chế xác thực nội bộ hoặc cấu hình JWT còn chờ quyết định.

<a id="session-response-contract"></a>

## Response API phiên — Q09 chốt bổ sung ngày 2026-10-26

Chủ dự án xác nhận đề xuất hoàn thiện ba API quản lý phiên UC10–UC12. Tổng phạm vi vẫn là 20 API; schema response của 16 API gốc và ba API phiên đã chốt, [introspection](#introspection-contract) được chốt bổ sung cùng ngày. Chỉ cập nhật docs, chưa tạo OpenAPI hoặc triển khai API. Chi tiết trường/kiểu duy trì tại [API phiên](api/sessions.md#session-list), schema liên kết tại [schema JSON](api/json-schema.md#session-responses).

- SessionItem luôn có id, deviceName, clientType, createdAt, lastSeenAt, expiresAt, isCurrent. Chỉ deviceName/clientType nhận null; deviceName tối đa 150 ký tự, clientType WEB/ANDROID nếu có. Session ID UUID, thời gian date-time UTC. lastSeenAt là hoạt động đã ghi nhận, không khẳng định online hoặc quyết định hết hạn.
- UC10 liệt kê phiên của mình chưa thu hồi/chưa hết hạn. page integer/int32 từ 0, mặc định 0; size integer/int32 1–100, mặc định 20. Sắp xếp cố định createdAt giảm dần rồi id tăng dần. Sai kiểu/giới hạn trả 400. Trang vượt kết quả trả 200/items rỗng, giữ page/size được yêu cầu; không tự đưa phiên hiện tại lên đầu, phiên này có thể ở trang khác. Danh sách có thể thay đổi giữa các lần tải.
- SessionPage luôn có items (array SessionItem, có thể rỗng), page, size, totalElements; không trường nào null. totalElements integer/int64 từ 0, đếm toàn bộ phiên phù hợp, không phải kích thước trang.
- UC11 thành công HTTP 200/data null. Phiên đích của mình đã thu hồi hoặc hết hạn vẫn trả kết quả chung nếu phiên gọi hợp lệ; không ghi đè thời điểm/lý do thu hồi cũ, không cập nhật lại phiên hết hạn. Giữ lỗi 404 cho phiên không tồn tại/thuộc người khác và 400 khi chọn phiên hiện tại.
- UC12 thành công HTTP 200, data chỉ có revokedCount bắt buộc/không null, integer/int64 từ 0. Chỉ đếm số phiên còn hiệu lực được chính thao tác này thu hồi thành công; không đếm phiên hiện tại, phiên đã thu hồi/hết hạn hoặc số refresh token. Không có phiên cần thu hồi/gọi lại khi chưa có phiên mới trả 0. Transaction thất bại trả lỗi, không trả số đếm một phần. Login mới sau thao tác vẫn được phép.
- Chốt ba mã thành công: SESSION_LIST_SUCCESS, SESSION_REVOKE_SUCCESS, OTHER_SESSIONS_REVOKED; nội dung message là minh họa, client không so sánh chuỗi message. [HTTP/lỗi](api/http-errors.md#session-success-codes) duy trì ánh xạ.

Các mã thành công của 16 API gốc và bộ mã validation được [chốt bổ sung cùng ngày](#response-code-contract), [introspection](#introspection-contract) cũng đã chốt schema/lỗi/header. Tình huống/mã ngoài danh mục và xác thực nội bộ vẫn còn mở. Quyết định này không chốt mã lý do thu hồi nội bộ, lịch cập nhật LastSeenAt hoặc thời gian xóa UserSessions.

<a id="http-error-contract"></a>

## HTTP và lỗi — Q09 chốt một phần ngày 2026-10-25

Chủ dự án xác nhận đề xuất HTTP status, danh mục messageCode và envelope lỗi. Chi tiết và ánh xạ vào 20 operation duy trì tại [HTTP/lỗi](api/http-errors.md); chỉ cập nhật tài liệu, chưa viết code hoặc OpenAPI.

- Thành công dùng 200; đăng ký Local và hoàn tất đăng ký Google dùng 201. V1 dùng 400 cho lỗi định dạng/validation, không dùng 422; giữ 401/403/404/409/429/500/503 theo nhóm nguyên nhân, method/media type sai dùng 405/415.
- Lỗi giữ envelope bốn trường với success false; validation dùng `VALIDATION_ERROR` và `data.errors` không rỗng, mỗi phần tử có `field`, `code`, `message`; `$` chỉ toàn bộ body. Lỗi nghiệp vụ/xác thực không cần chỉ ra trường dùng data null. Không trả lại dữ liệu xác thực đã nhập hoặc thông tin nội bộ.
- Chốt danh mục mã theo tình huống trong tài liệu HTTP/lỗi, dùng UPPER_SNAKE_CASE ổn định; client dựa HTTP status/messageCode, không so sánh message. Mã con xác nhận mật khẩu không khớp là `PASSWORD_MISMATCH`; [bộ mã validation bổ sung ngày 2026-10-26](#response-code-contract) thay phần còn chờ, không suy ra mã công khai từ framework.
- Giữ ngoại lệ đã chốt cho quên mật khẩu, logout, thu hồi phiên lặp và introspection; PATCH cùng giá trị vẫn trả 200. Introspection dùng JSON riêng, không bọc envelope chung.
- Vượt giới hạn gửi OTP dùng 429 kèm Retry-After tính bằng giây. Đạt giới hạn nhập sai OTP dùng 400 `OTP_ATTEMPTS_EXCEEDED`; 503 không tự làm client xóa token hoặc kết luận phiên bị thu hồi.

Q09 còn mở cho các trường hợp/mã ngoài [danh mục mã phản hồi](#response-code-contract) và [introspection chốt ngày 2026-10-26](#introspection-contract). Q03/Q06/Q10 được xác nhận riêng ngày 2026-10-31 ở các mục bổ sung bên dưới, không suy ra từ bảng HTTP cũ; cấu hình Service JWT Q03/Q11a vẫn mở, user access token Q14 đã chốt riêng.

<a id="response-code-contract"></a>

## Danh mục mã phản hồi — Q09 chốt bổ sung ngày 2026-10-26

Chủ dự án xác nhận đề xuất giữ các mã lỗi đã chốt, xác nhận mã thành công đang có trong ví dụ và bổ sung các mã còn thiếu. Danh mục/ánh xạ đầy đủ duy trì tại [HTTP/lỗi](api/http-errors.md); chỉ cập nhật docs, chưa tạo OpenAPI hoặc code API.

- Chốt mã thành công của 16 API gốc theo đề xuất, gồm ba nhánh của POST /api/v1/auth/google; tên/HTTP status theo [bảng mã thành công](api/http-errors.md#success-codes). Riêng thiết lập mật khẩu chốt PASSWORD_SET_SUCCESS, thay SET_PASSWORD_SUCCESS trong ví dụ cũ; danh mục nguồn giữ tên lịch sử. Cùng ba mã API phiên đã chốt, 19 API dùng envelope có danh mục thành công xác định. Introspection không dùng messageCode.
- Bổ sung AUTHENTICATION_REQUIRED (401) khi chưa cung cấp thông tin xác thực cho API được bảo vệ, METHOD_NOT_ALLOWED (405) và UNSUPPORTED_MEDIA_TYPE (415). Dùng envelope lỗi, success false, data null; không dùng mã thiếu xác thực thay validation trường password thiếu trong JSON login. Thông tin xác thực đã cung cấp nhưng sai dùng mã tương ứng; hợp đồng xác thực service còn Q03. Không tự áp ba mã này cho introspection.
- Chốt [10 mã con validation](api/http-errors.md#validation-codes): REQUIRED, TYPE_MISMATCH, NOT_BLANK, INVALID_LENGTH, INVALID_FORMAT, INVALID_VALUE, OUT_OF_RANGE, UNKNOWN_FIELD, EMPTY_PATCH và PASSWORD_MISMATCH. Dùng HTTP 400/messageCode VALIDATION_ERROR, mã con nằm trong data.errors[].code. JSON hỏng/thiếu toàn bộ body bắt buộc giữ INVALID_REQUEST. Chỉ áp dụng quy tắc của trường; không tự cấm khoảng trắng hoặc trim mật khẩu/token.
- Giữ mã nghiệp vụ/token/OTP đã chốt. Bổ sung ánh xạ hoàn tất đăng ký Google: email trong registrationToken đã được tài khoản khác sử dụng trước khi hoàn tất trả 409 EMAIL_ALREADY_EXISTS, rollback đăng ký, hướng người dùng quay lại đăng nhập Google theo luồng đã xác nhận.
- Giữ ngoại lệ LOGOUT_SUCCESS với token không tìm thấy và thông báo chung PASSWORD_RESET_CODE_SENT khi email không tồn tại. Tên mã không tự chốt hành vi gửi email lỗi tại Q06 hoặc retry/quản trị tại Q10. Nội dung message chỉ để hiển thị, không dùng làm điều kiện xử lý của client.

Q09 vẫn mở cho tình huống/mã ngoài danh mục đã nêu; [introspection](#introspection-contract) đã chốt bổ sung cùng ngày. Tại thời điểm 2026-10-26, cơ chế/lỗi xác thực service, quản trị/gửi email và thứ tự kiểm tra nội bộ chưa chốt; các phần đó được [xác nhận bổ sung ngày 2026-10-31](#internal-service-auth), không suy ra từ danh mục mã cũ. Chưa đặt thứ tự ưu tiên chung cho mọi tổ hợp validation.

<a id="introspection-contract"></a>

## Introspection — Q09 và nguồn roles của Q14 chốt ngày 2026-10-26

Chủ dự án xác nhận bộ hợp đồng introspection: request form, hai biến thể response thành công, nguồn roles, body lỗi và header. Tất cả 20 API đã có schema request/response được xác nhận trong phạm vi được nêu; điều này không đồng nghĩa hoàn tất toàn bộ security/hành vi tích hợp hoặc đã triển khai. Chi tiết request/response duy trì tại [introspection](api/sessions.md#introspect), bộ lỗi/header tại [HTTP/lỗi](api/http-errors.md#introspection-errors).

- Request chỉ nhận token bắt buộc/string không rỗng và token_type_hint tùy chọn/string; giữ nguyên token, không trim. Thiếu/rỗng token, lặp tham số hoặc trường ngoài danh sách trả 400 invalid_request. Hint chỉ là gợi ý, không giới hạn bằng enum access_token và có thể bị bỏ qua để xác định loại token thực tế.
- Gateway chỉ được introspect user access token. Refresh token, registrationToken hoặc Service JWT gửi vào trường token trả 200/active false. Hint refresh_token với user access token hợp lệ vẫn kiểm tra access token. Kiểm tra danh tính/quyền Gateway trước khi xử lý token người dùng.
- Khi active true, bắt buộc đủ chín trường không null: active, sub, sid, token_type, iss, aud, exp, iat, roles. sub là string biểu diễn User ID; sid UUID; token_type Bearer; aud luôn là array string không rỗng; exp/iat integer/int64 theo giây Unix lấy từ user JWT. Giữ exp là hạn JWT, không cắt theo hạn phiên; phiên hết hạn thì active false dù JWT còn hạn.
- Q14 đã chốt nguồn roles: lấy role hiện tại qua UserRoles/Roles tại thời điểm introspection, không lấy role của Gateway hoặc chỉ dựa vào role trong JWT từ lúc đăng nhập. roles luôn là array string, không có role thì []. Không thêm API quản lý role hoặc chốt chính sách thay đổi role ngoài V1.
- Token sai/hết hạn/sai mục đích, user BANNED, phiên không hợp lệ hoặc token Gateway không được phép biết thông tin trả HTTP 200 chỉ có active false; không trả user/role/lý do hoặc các trường khác dưới dạng null.
- Chốt tám trường hợp lỗi theo bảng HTTP/lỗi: 401 thiếu xác thực body {}; 401 Service JWT sai/hết hạn với invalid_token; 401 thiếu quyền với insufficient_scope; 400 form sai, 405 method sai và 415 media type sai cùng invalid_request; 503 phụ thuộc tạm lỗi với temporarily_unavailable; 500 lỗi ngoài dự kiến với server_error. Body lỗi khác object rỗng chỉ có trường error string không null, không dùng envelope/messageCode chung.
- Mọi response dùng Content-Type application/json và Cache-Control no-store. 401 có WWW-Authenticate Bearer, thêm error tương ứng khi invalid_token/insufficient_scope; 405 có Allow POST. Không chuyển lỗi danh tính/quyền Gateway hoặc lỗi hạ tầng thành active false. Gateway tiếp tục không cache active response theo Q04.

Q11a vẫn mở cho bên phát Service JWT Gateway, thuật toán, issuer/audience/subject, TTL, tập claim/cách biểu diễn quyền và phân phối/rotation khóa. Quyền Gateway tokens:introspect được [chốt bổ sung trong Q03 ngày 2026-10-31](#internal-service-auth); không suy ra từ quyết định introspection 2026-10-26. [Q14](#access-token-key-pair) đã chốt riêng issuer/audience, kid và quy trình cấp/phân phối/thay khóa user access token ngày 2026-10-31; nguồn roles đã chốt từ database. Q03/Q06/Q10 được chốt riêng ở các mục bổ sung bên dưới; các tình huống/mã ngoài danh mục vẫn mở. Chỉ cập nhật tài liệu, chưa tạo OpenAPI, code API hoặc cấu hình JWT.

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

<a id="internal-service-auth"></a>

## Xác thực service và actor Admin — Q03 chốt ngày 2026-10-31

Chủ dự án chấp nhận đề xuất Q03/Q06/Q10 và phần bổ sung Q09, ngoại trừ toàn bộ phần đề xuất cấu hình JWT Q11a/Q14 để nghiên cứu thêm. Chỉ cập nhật docs; chưa tạo OpenAPI, code API, dependency hoặc cấu hình security.

- Admin Service và Borrow Service dùng Service JWT riêng trong `Authorization: Bearer <service-jwt>`. Mỗi service tự ký bằng khóa riêng được User Service tin cậy; token này chứng minh service gọi, không thay access token người dùng.
- Phạm vi quyền được xác nhận: Borrow có `users:read`; Admin có `users:read` và `users:status:write`; Gateway có `tokens:introspect`. User Service giới hạn quyền theo service đã được tin cậy; service không được tự nâng quyền bằng cách thêm scope vào JWT. Cách biểu diễn claim quyền thuộc cấu hình JWT chưa chốt.
- API trạng thái yêu cầu thêm access token của quản trị viên trong header `X-Admin-Access-Token`. User Service xác minh user token, phiên, user ACTIVE và role ADMIN hiện tại từ database; không tin actorId/role do client tự khai. Header này không thay Service JWT trong Authorization.
- Kiểm tra API nội bộ theo thứ tự: danh tính service → quyền service → actor Admin khi cần → nghiệp vụ; trả lỗi của bước đầu tiên không đạt. Lỗi HTTP chi tiết tại [bổ sung Q09](#integration-error-contract).
- Cấu hình thuật toán, issuer/audience/subject, TTL, tập claim và phân phối/rotation khóa Service JWT vẫn **PENDING DECISION** tại Q03/Q11a, kể cả bên phát Service JWT Gateway. Không coi đề xuất RS256/5 phút, các định danh mẫu, kid hoặc phương án public key/JWKS dành cho Service JWT là đã chốt. Cấu hình [user access token Q14](#access-token-key-pair) đã được xác nhận riêng sau đó; không cache introspection giữ nguyên.

<a id="otp-email-delivery"></a>

## Gửi email OTP — Q06 chốt hành vi ngày 2026-10-31

Dùng SMTP của tài khoản email dự án, gửi đồng bộ với timeout hữu hạn. Nhà cung cấp/tài khoản, host/port, thông tin xác thực và giá trị timeout cụ thể cấu hình theo môi trường, chưa chọn nhà cung cấp cụ thể hoặc ghi secret trong docs/Git.

1. Kiểm tra giới hạn gửi; lưu hash OTP mới và vô hiệu mã cũ cùng email/purpose trong transaction, rồi commit.
2. Sau commit mới gửi email; không giữ transaction database trong lúc chờ SMTP. OTP gốc chỉ dùng để gửi, không lưu/log mã gốc.
3. Một lần cấp mã đã commit vẫn tính vào giới hạn gửi dù SMTP lỗi hoặc timeout. Không phục hồi mã cũ, không gia hạn mã mới, không tự tạo mã khác để retry; người dùng xin lại theo giới hạn Q05. Không có cơ chế tự gửi lại bền vững trong V1.

| API xin OTP | SMTP nhận thư thành công | SMTP lỗi hoặc timeout sau commit |
| --- | --- | --- |
| Đăng ký REGISTER | HTTP 200, REGISTER_CODE_SENT | HTTP 503, SERVICE_UNAVAILABLE |
| Thiết lập mật khẩu SET_PASSWORD | HTTP 200, SET_PASSWORD_CODE_SENT | HTTP 503, SERVICE_UNAVAILABLE |
| Quên mật khẩu PASSWORD_RESET | HTTP 200, PASSWORD_RESET_CODE_SENT với thông báo chung | Vẫn HTTP 200, cùng kết quả chung; ghi lỗi vận hành nội bộ |

Ngoại lệ quên mật khẩu tránh thêm dấu hiệu phân biệt email tồn tại qua lỗi gửi mail; email không tồn tại tiếp tục trả thông báo chung đã chốt. Không che lỗi database/hạ tầng ngoài bước gửi SMTP thành thành công; áp dụng HTTP/lỗi chung. Mã không bị tự vô hiệu chỉ vì SMTP lỗi/timeout, vì thư có thể đã được nhận để gửi.

SMTP nhận thư chưa bảo đảm thư đã vào Inbox. Nếu tiến trình dừng giữa commit và gửi, mã vẫn đã được cấp, người dùng phải xin lại theo giới hạn hiện hành. Chấp nhận giới hạn này trong V1; không thêm bảng outbox, hàng đợi hoặc cam kết database/email nguyên tử.

<a id="admin-status-contract"></a>

## Retry, cạnh tranh và audit trạng thái — Q10 chốt ngày 2026-10-31

- Request tiếp tục gửi trạng thái đích ACTIVE hoặc BANNED. Gửi lại trạng thái đang có trả HTTP 200 USER_STATUS_UPDATE_SUCCESS; không ghi đè thời điểm/lý do thu hồi cũ. Mở khóa không khôi phục phiên/token.
- Lệnh khóa/mở khóa cùng user khóa cùng bản ghi user và xử lý tuần tự. Trạng thái cuối theo thứ tự thực hiện trong database, không cam kết theo thời điểm client gửi. Các writer khác vẫn phải tuần tự hóa với thu hồi theo yêu cầu nhất quán; chưa chọn toàn bộ thứ tự khóa hoặc isolation level.
- Timeout không chứng minh transaction chưa commit. Admin đọc lại user bằng API tra cứu để đối soát, không tự gửi lại lệnh trạng thái cũ. Gửi lại sau đối soát là lệnh mới, không cam kết exactly-once. Ví dụ khóa đã commit nhưng mất response, rồi có lệnh mở khóa: retry lệnh khóa cũ có thể khóa lại tài khoản.
- V1 từ chối khóa đối tượng đang có role ADMIN, kể cả tự khóa, bằng HTTP 403 ACCESS_DENIED. Quy tắc không bổ sung API quản lý role hoặc cơ chế thay đổi ADMIN.
- Admin Service ghi audit yêu cầu và kết quả; User Service ghi sự kiện gồm service, actor, user đích, trạng thái trước/sau, thời gian và request ID. Không ghi token/OTP/hash/secret, không thêm bảng; không tuyên bố audit log ngoài database nguyên tử với transaction cập nhật trạng thái.

<a id="integration-error-contract"></a>

## Lỗi tích hợp bổ sung — Q09 chốt một phần ngày 2026-10-31

Bổ sung hai mã dùng envelope chung, success false và data null: HTTP 401 SERVICE_TOKEN_INVALID khi Service JWT đã cung cấp nhưng sai/hết hạn; HTTP 401 ADMIN_ACTOR_INVALID khi token quản trị viên đã cung cấp nhưng sai/hết hạn/phiên mất hiệu lực. Thiếu xác thực dùng AUTHENTICATION_REQUIRED; thiếu quyền hoặc khóa ADMIN bị cấm dùng ACCESS_DENIED. SMTP tạm lỗi dùng SERVICE_UNAVAILABLE với ngoại lệ PASSWORD_RESET ở [Q06](#otp-email-delivery).

Thứ tự kiểm tra API nội bộ theo [Q03](#internal-service-auth). Giữ nguyên body lỗi/header introspection riêng, không áp hai mã mới cho introspection và không đặt thứ tự ưu tiên cho mọi tổ hợp validation. Danh mục/ánh xạ duy trì tại [HTTP/lỗi](api/http-errors.md). Q09 chỉ còn tình huống/mã/chi tiết ngoài phần đã xác nhận; cấu hình Service JWT Q03/Q11a tiếp tục PENDING DECISION, user access token Q14 đã chốt riêng.
