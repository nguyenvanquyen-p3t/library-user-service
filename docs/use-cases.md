# Luồng nghiệp vụ UC01–UC12

Đối chiếu toàn bộ DacTaUseCase.docx ngày 2026-10-22; đã chuyển luồng, ngoại lệ và kết quả sang Markdown để đọc độc lập với Word. SHA-256 của bản nguồn: `EEA8FFC0062CD7DC4C451A3E82F506A30DE218CA480969462145FD0FFB3A4100`. UC02 đã triển khai ngày 2026-11-10, UC03–UC04 ngày 2026-11-11, UC06 ngày 2026-11-12; các UC khác là thiết kế, kết hợp nguồn với [quyết định đã chốt](confirmed-decisions.md). Không cần file Word hoặc lịch sử chat để sử dụng tài liệu này.

Các mục kết quả mô tả nhánh thành công, không áp dụng cho nhánh bị từ chối. Client là Web/Android; các bước gọi API tự động không yêu cầu người dùng thao tác trực tiếp. Chính sách xem [sổ quyết định](confirmed-decisions.md), transaction xem [requirements](requirements.md), request/response xem [16 API hiện hành](api/current-contract.md) và [API phiên](api/sessions.md). Các điểm chưa đủ để code được ghi tại [open-decisions](open-decisions.md), không ngầm coi đề xuất là quyết định. Liên kết được cập nhật ngày 2026-10-24; không đối chiếu lại Word trong đợt này.

## Quy tắc chung

Theo [quyết định ngày 2026-11-10](confirmed-decisions.md#deferred-service-auth), khi triển khai các luồng dưới đây, các service mặc định tin tưởng nhau; xác thực/quyền service được bổ sung sau khi hoàn tất tất cả chức năng. Mô tả Service JWT trong quy tắc chung là thiết kế đích cho bước sau. Xác thực người dùng, kiểm tra phiên, quyền dữ liệu và actor ADMIN vẫn giữ nguyên.

Request được bảo vệ cần JWT hợp lệ, user ACTIVE, phiên từ sid thuộc user từ sub, chưa hết hạn/chưa thu hồi. Gateway dùng Service JWT riêng gọi introspection; service nghiệp vụ tiếp tục kiểm tra quyền dữ liệu. UC03/UC04 dùng refresh token theo điều kiện riêng, không đòi access token còn hạn. Thu hồi phiên khiến access token bị từ chối ở lần kiểm tra tiếp theo dù JWT chưa hết hạn. Không cam kết hủy request đã vượt kiểm tra trước commit.

OTP phải đúng email/purpose, còn hạn, chưa dùng/chưa bị thay thế và chưa vượt giới hạn thử. Mật khẩu áp dụng thống nhất chính sách đã chốt. Chi tiết thời gian, số lần thử, hash, transaction và lưu giữ dữ liệu nằm trong docs kỹ thuật, không phải bước tương tác bắt buộc phải lặp trong Word.

Bổ sung đã xác nhận ngày 2026-10-31 — [gửi OTP qua SMTP](confirmed-decisions.md#otp-email-delivery): lưu hash mã/vô hiệu mã cũ và commit trước khi gửi đồng bộ. SMTP lỗi/timeout ở UC01/UC09 báo lỗi 503; riêng xin mã UC05 giữ 200/thông báo chung, ghi lỗi vận hành nội bộ. Lỗi database vẫn trả lỗi; mã đã cấp vẫn tính giới hạn gửi, không tự cấp mã retry hoặc phục hồi mã cũ. Không đối chiếu lại Word trong đợt cập nhật này.

## UC01 Đăng ký bằng email

**Actor:** người đăng ký làm độc giả. **Ưu tiên:** cao. Có email nhận mã; luồng thành công dành cho người chưa có tài khoản.

1. Người dùng mở đăng ký, nhập username, email, mật khẩu, họ tên, số điện thoại và nhấn đăng ký.
2. Client xin mã với username/email; hệ thống kiểm tra chưa được sử dụng, tạo OTP REGISTER và gửi email. Chưa tạo user hay phiên.
3. Client hiển thị nhập mã; người dùng nhập OTP và gửi thông tin hoàn tất đăng ký.
4. Hệ thống kiểm tra mã và thông tin/mật khẩu theo chính sách; tạo user ACTIVE, gán READER, đánh dấu mã đã dùng và thông báo thành công.

**Ngoại lệ:** username trùng → nhập tên khác; email đã có → thông báo và kết thúc; gửi email lỗi → báo thử lại; OTP sai → tính số lần thử, cho nhập lại nếu còn được phép; hết hạn → xin mã mới; thông tin/mật khẩu không hợp lệ → chỉ rõ trường cần sửa và gửi lại.

**Kết quả:** tài khoản có mật khẩu, đăng nhập được bằng username/email. Không tự cấp token hoặc tạo phiên; đăng nhập riêng bằng UC02. OTP chỉ gắn email + REGISTER: đổi username không làm mất hiệu lực mã; đổi email phải xin mã mới. Client giữ thông tin form để gửi hoàn tất, không giả định server đã lưu mật khẩu tạm. Chủ dự án không yêu cầu bổ sung luồng riêng cho đăng ký Local đồng thời hoặc kiểm tra trùng lần hai trong đặc tả; UNIQUE database vẫn giữ nguyên.

## UC02 Đăng nhập Local

**Đã triển khai ngày 2026-11-10:** [UC02 và nền tảng bảo mật](user-security.md); UC03–UC04 được bổ sung ngày 2026-11-11, UC06 được bổ sung ngày 2026-11-12, 8 use case khác vẫn thiết kế. Metadata thiết bị null; các nhánh hướng dẫn Google/UC09 chưa có endpoint tương ứng.

**Actor:** READER/LIBRARIAN/ADMIN. **Ưu tiên:** cao. Nhánh thành công cần tài khoản ACTIVE đã có mật khẩu; đây là điều kiện được hệ thống kiểm tra, vẫn có nhánh từ chối tương ứng.

1. Người dùng mở đăng nhập và gửi username/email cùng mật khẩu.
2. Hệ thống tìm tài khoản, kiểm tra có mật khẩu, mật khẩu đúng và trạng thái ACTIVE, lấy các role.
3. Hệ thống tạo phiên mới, cấp access token chứa sid và refresh token liên kết phiên; client chuyển người dùng vào hệ thống.

**Ngoại lệ:** không có tài khoản hoặc sai mật khẩu → thông báo chung; chưa có mật khẩu → hướng dẫn đăng nhập Google rồi dùng UC09; BANNED → từ chối. **Kết quả:** phiên mới và cặp token, server chỉ lưu hash refresh token.

## UC03 Làm mới token

**Đã triển khai ngày 2026-11-11:** [implementation](user-security.md#refresh-và-logout-uc03uc04). Ưu tiên lỗi token → user → phiên; không gia hạn phiên hoặc cập nhật LastSeenAt.

**Actor:** người đã đăng nhập, client thực hiện tự động. **Ưu tiên:** cao. Client giữ refresh token; thành công đòi token, user và phiên còn hiệu lực.

1. Khi access token hết/sắp hết hạn, client gửi refresh token.
2. Hệ thống kiểm tra token, user ACTIVE và phiên đúng chủ sở hữu/còn hiệu lực.
3. Hệ thống rotation: cấp cặp token mới cùng sid, thu hồi token cũ và lưu liên kết thay thế. Mỗi token chỉ rotation thành công một lần.
4. Client nhận cặp token mới để tiếp tục sử dụng phiên; không tạo phiên mới hoặc gia hạn session.

**Ngoại lệ:** token không tồn tại/hết hạn → đăng nhập lại; token đã thu hồi (kể cả đã rotation) → từ chối cấp token mới; user BANNED, phiên thu hồi/hết hạn → từ chối. Dùng lại token đã rotation không làm thu hồi session/token thay thế. **Kết quả:** cùng phiên, token cũ không dùng để refresh lần nữa.

## UC04 Đăng xuất phiên hiện tại

**Đã triển khai ngày 2026-11-11:** khóa user và thu hồi đúng phiên/token trong transaction; [implementation](user-security.md#refresh-và-logout-uc03uc04).

**Actor:** người đã đăng nhập, client giữ refresh token của phiên. **Ưu tiên:** cao.

1. Người dùng chọn đăng xuất; client gửi refresh token.
2. Hệ thống xác định và thu hồi phiên cùng các refresh token còn hiệu lực của phiên.
3. Client xóa token, đưa người dùng về đăng nhập.

**Ngoại lệ:** token cũ/thu hồi/hết hạn nhưng còn xác định được phiên → vẫn thu hồi phiên nếu còn hiệu lực; phiên đã thu hồi/hết hạn → thành công, không ghi đè thời điểm/lý do cũ. Token không tồn tại/không xác định được phiên → kết quả chung, chỉ bảo đảm xóa token client. Database/transaction lỗi → không báo thu hồi server thành công; client có thể thoát nhưng thông báo chưa xác nhận được thu hồi server.

**Kết quả:** thu hồi server thành công thì cả phiên và token liên quan không sử dụng được; các phiên khác giữ nguyên. Chỉ xóa token client không phải bằng chứng vừa thu hồi được phiên server. Cơ chế này khác từ chối token cũ ở UC03.

## UC05 Quên và đặt lại mật khẩu

**Actor:** người có tài khoản đã từng đặt mật khẩu và truy cập được email đăng ký. **Ưu tiên:** cao; không yêu cầu đang đăng nhập.

1. Người dùng chọn quên mật khẩu, nhập email; hệ thống tìm tài khoản và gửi OTP PASSWORD_RESET.
2. Người dùng nhập OTP, mật khẩu mới và xác nhận; client gửi chung trong một request hoàn tất. Hệ thống xác minh OTP cho đúng email/purpose và kiểm tra mật khẩu khớp, hợp lệ trước khi lưu.
3. Hệ thống cập nhật mật khẩu, dùng mã một lần, thu hồi tất cả phiên/refresh token liên quan, thông báo thành công. Người dùng đăng nhập lại.

**Đã xác nhận ngày 2026-10-25 — [Q13](confirmed-decisions.md#password-otp-flow):** xác minh OTP trong request hoàn tất, không có API xác minh riêng hoặc reset ticket. Quyết định này thay cách chia bước trong Word đã đối chiếu ngày 2026-10-22; đợt này không cập nhật hoặc đối chiếu lại Word.

**Ngoại lệ:** email không tồn tại → thông báo chung; sai mã → tăng số lần thử, mã bị chặn sau lần sai thứ 5; mã hết hạn/đã dùng/đã thay thế/vượt giới hạn → từ chối, xin mã mới theo chính sách; mật khẩu sai chính sách → sửa lại; tài khoản chưa có mật khẩu → đăng nhập Google và dùng UC09. **Kết quả:** mật khẩu mới, mã không dùng lại, mọi phiên cũ mất hiệu lực. Reset không phải mở khóa BANNED.

## UC06 Xem và cập nhật hồ sơ

**Đã triển khai ngày 2026-11-12:** [GET/PATCH hồ sơ](user-profile.md). PATCH khóa user rồi kiểm tra lại user/phiên; mất hiệu lực trong lúc chờ trả 401 SESSION_INACTIVE. Không cập nhật LastSeenAt hoặc phiên/token.

**Actor:** người đã đăng nhập ACTIVE, phiên hợp lệ. **Ưu tiên:** trung bình.

1. Người dùng mở thông tin cá nhân; hệ thống xác định user từ danh tính đã xác minh và hiển thị hồ sơ của họ.
2. Người dùng có thể chỉ xem hoặc sửa fullName/phoneNumber rồi xác nhận.
3. Hệ thống kiểm tra dữ liệu theo [schema đã chốt](api/json-schema.md#profile-patch), lưu và thông báo thành công. Bỏ qua trường để giữ nguyên; phoneNumber null để xóa; fullName null/trắng, số điện thoại rỗng, body rỗng hoặc trường ngoài danh sách bị từ chối.

**Ngoại lệ:** token hết hạn → UC03 nếu còn refresh được, nếu không thì đăng nhập lại; danh tính/phiên không hợp lệ → từ chối; dữ liệu sai → không lưu, cho sửa lại. **Kết quả:** nhánh xem không thay đổi hồ sơ; nhánh sửa lưu thông tin, UpdatedAt do trigger cập nhật khi dữ liệu thực sự đổi. Không sửa password, role, status, username hoặc email qua API này.

## UC07 Đổi mật khẩu

**Actor:** người ACTIVE đã đăng nhập, có mật khẩu và biết mật khẩu hiện tại. **Ưu tiên:** cao.

1. Người dùng mở đổi mật khẩu, gửi mật khẩu hiện tại, mật khẩu mới và xác nhận.
2. Hệ thống xác định đúng user, kiểm tra mật khẩu hiện tại, chính sách mật khẩu mới và xác nhận khớp.
3. Hệ thống đổi mật khẩu, thu hồi toàn bộ phiên/refresh token kể cả phiên hiện tại; client xóa token, chuyển về đăng nhập.

**Ngoại lệ:** mật khẩu hiện tại sai → từ chối; mật khẩu mới không hợp lệ hoặc xác nhận khác → nhập lại; chưa có mật khẩu → UC09. **Kết quả:** phải đăng nhập lại; Google identity không bị thay đổi bởi chức năng đổi mật khẩu.

## UC08 Đăng nhập và đăng ký Google

**Actor:** người dùng và Google Identity Provider. **Ưu tiên:** cao. Có tài khoản Google, chức năng Google Sign-In và cấu hình OIDC hoạt động.

1. Người dùng chọn Continue with Google, chọn tài khoản và đồng ý cung cấp thông tin cần thiết.
2. Client gửi Google credential/ID Token; server xác minh rồi lấy sub, email, trạng thái email đã xác minh và tên.
3. Hệ thống tìm identity theo GOOGLE + sub. Nếu đã tồn tại, lấy user, kiểm tra ACTIVE, tạo phiên mới và cấp token đăng nhập.

**A1 — Identity chưa có, email Local đã có:** dùng email Google đã xác minh để tự liên kết identity vào user hiện có; không hỏi lại mật khẩu Local, không tạo user mới hoặc thay role/mật khẩu. Chỉ user ACTIVE được cấp phiên/token. Tài khoản trở thành Hybrid. Google client ID được phép còn Q07; không tin email do client tự khai.

**A2 — Người mới:** trả registrationToken và màn hình hoàn tất tài khoản, chưa có phiên đăng nhập. Email từ Google, không nhập mật khẩu, gợi ý username có thể sửa, họ tên gợi ý từ Google. Theo [schema response chốt ngày 2026-10-25](api/json-schema.md#google-responses), fullName/suggestedUserName luôn xuất hiện, có thể null khi thiếu gợi ý phù hợp; người dùng điền tên nếu thiếu. Gợi ý username không giữ chỗ hoặc bảo đảm còn trống. Request hoàn tất mang username/fullName/phoneNumber, fullName vẫn bắt buộc hợp lệ và không null. Server xác minh registrationToken, kiểm tra identity chưa tồn tại và username chưa được dùng, tạo user ACTIVE với PasswordHash NULL, identity GOOGLE, READER và phiên/token. Identity đã tồn tại ở bước hoàn tất → không cấp thêm phiên/token, chuyển sang đăng nhập Google. Sai dữ liệu/username → sửa trong TTL còn lại; token hết hạn → xác minh Google lại. Sau commit mất response → đăng nhập Google, không cấp lại token bằng registrationToken.

**Bổ sung đã xác nhận ngày 2026-10-26:** email trong registrationToken đã được tài khoản khác sử dụng trước khi hoàn tất → HTTP 409 EMAIL_ALREADY_EXISTS, rollback đăng ký và hướng người dùng quay lại đăng nhập Google theo luồng đã chốt; không tự liên kết/cấp token ở request hoàn tất bị từ chối. Xem [danh mục mã phản hồi](confirmed-decisions.md#response-code-contract).

**Ngoại lệ khác:** Google token không hợp lệ → không tạo/liên kết user; người dùng hủy xác thực Google → kết thúc không thay đổi database; user BANNED → không cấp token. **Kết quả:** ba nhánh hoàn tất thành công đều có phiên mới; dừng ở màn hình hoàn tất đăng ký chưa phải đăng nhập thành công. Giữ Google identity theo sub, không dùng email thay khóa định danh. Giới hạn chống đăng ký lặp dựa trên identity xem quyết định Q08.

## UC09 Thiết lập mật khẩu lần đầu

**Actor:** người đăng nhập bằng Google, ACTIVE, chưa có mật khẩu. **Ưu tiên:** cao.

1. Người dùng mở phương thức đăng nhập/bảo mật và chọn thiết lập mật khẩu.
2. Hệ thống lấy email từ tài khoản đang xác thực và gửi OTP SET_PASSWORD.
3. Người dùng nhập mã, mật khẩu mới và xác nhận; client gửi chung trong một request hoàn tất có xác thực. Hệ thống kiểm tra mã đúng email/purpose còn hiệu lực, mật khẩu hợp lệ và khớp xác nhận trước khi lưu.
4. Hệ thống đặt mật khẩu, đánh dấu mã đã dùng, thu hồi toàn bộ phiên/refresh token. Client xóa token, thông báo thành công và chuyển về đăng nhập.

**Đã xác nhận ngày 2026-10-25 — [Q13](confirmed-decisions.md#password-otp-flow):** xác minh OTP trong request hoàn tất, không có API xác minh riêng hoặc token/ticket xác minh.

**Ngoại lệ:** gửi mail lỗi → báo lỗi, cho yêu cầu lại; OTP sai → tính số lần thử, chặn sau lần thứ 5; hết hạn → xin mã mới; mật khẩu sai/không khớp → sửa; đã có mật khẩu → UC07. Các kiểm tra mã đã dùng/thay thế cũng áp dụng theo chính sách chung.

**Kết quả:** Google-only thành Hybrid, giữ identity Google, đăng nhập lại được bằng Google hoặc username/email + mật khẩu. Không nhận email tùy ý của tài khoản khác để đặt mật khẩu.

## UC10 Xem danh sách phiên

**Actor:** người ACTIVE đã đăng nhập, phiên hợp lệ. **Ưu tiên:** trung bình.

1. Người dùng mở Bảo mật tài khoản → Quản lý phiên đăng nhập.
2. Hệ thống lấy các phiên của chính người dùng chưa thu hồi/chưa hết hạn, trả ID, thời điểm tạo/hết hạn/hoạt động gần nhất và metadata thiết bị nếu có.
3. Server xác định isCurrent từ sid đã xác minh; client hiển thị danh sách, đánh dấu phiên hiện tại.

**Ngoại lệ:** danh tính/phiên không hợp lệ → xác thực lại; không có kết quả phù hợp hoặc trang vượt kết quả → danh sách trống; tải lỗi → báo và cho thử lại. **Kết quả:** chỉ xem, không thu hồi phiên; không lộ token/hash. Metadata có thể thiếu, không phải bằng chứng danh tính. [Phân trang/sắp xếp đã chốt ngày 2026-10-26](api/sessions.md#session-list); không tự đưa phiên hiện tại lên đầu, phiên này có thể nằm ở trang khác. lastSeenAt là hoạt động đã ghi nhận, không khẳng định đang online.

## UC11 Đăng xuất một phiên khác

**Actor:** người đã đăng nhập với phiên hợp lệ. **Ưu tiên:** cao.

1. Người dùng mở danh sách UC10; client đánh dấu phiên hiện tại, không có nút/ô chọn thu hồi cho phiên đó.
2. Người dùng chọn phiên khác; client gửi sessionId.
3. Server xác minh user và sid của phiên gọi; kiểm tra phiên đích thuộc user và khác sid.
4. Hệ thống thu hồi phiên đích cùng token liên quan, báo thành công; client cập nhật danh sách, phiên hiện tại tiếp tục sử dụng.

**Ngoại lệ:** không có phiên/không thuộc user → từ chối không tiết lộ dữ liệu người khác; request cố chọn phiên hiện tại → từ chối không thay đổi phiên/token; phiên đích của mình đã thu hồi hoặc hết hạn → thành công nếu phiên gọi còn hợp lệ, giữ nguyên thời điểm/lý do cũ và không cập nhật lại phiên hết hạn theo [xác nhận ngày 2026-10-26](confirmed-decisions.md#session-response-contract); phiên gọi/token không hợp lệ → từ chối; transaction lỗi → không thu hồi một phần hoặc báo thành công.

**Kết quả:** chỉ phiên đích mất hiệu lực; phiên hiện tại và các phiên khác không ảnh hưởng. Đăng xuất bản thân dùng UC04. Đây là lựa chọn chủ dự án đã nêu và Word hiện tại thể hiện, thay đề xuất API cũ cho chọn phiên hiện tại.

## UC12 Đăng xuất tất cả phiên khác

**Actor:** người ACTIVE đang có phiên hợp lệ. **Ưu tiên:** cao.

1. Người dùng chọn đăng xuất tất cả phiên khác trong quản lý phiên; hệ thống yêu cầu xác nhận, người dùng xác nhận.
2. Server xác minh user và sid, thu hồi các phiên khác còn hiệu lực cùng refresh token liên quan, giữ phiên hiện tại.
3. Hệ thống trả kết quả với revokedCount đếm số phiên còn hiệu lực được chính thao tác này thu hồi thành công, client cập nhật danh sách; không đếm phiên hiện tại, phiên đã thu hồi/hết hạn hoặc số refresh token. [Schema và cách đếm chốt ngày 2026-10-26](api/sessions.md#session-revoke-others).

**Ngoại lệ:** không có phiên cần thu hồi hoặc gọi lại khi chưa có phiên mới → HTTP 200 với revokedCount 0; access token/phiên gọi không hợp lệ → từ chối; không hoàn tất transaction → báo lỗi, không báo thành công hoặc trả số đếm một phần. **Kết quả:** các phiên khác đã xử lý mất hiệu lực, phiên hiện tại giữ nguyên. Không cấm đăng nhập tạo phiên mới sau thao tác; không lấy currentSessionId tùy ý từ client.
