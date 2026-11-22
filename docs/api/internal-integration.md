# Tích hợp nội bộ

**Hiện trạng ngày 2026-11-22.** [Quên và đặt lại mật khẩu UC05](../user-password-reset.md) đã triển khai. Tổng **16 IMPLEMENTED/4 DESIGN_ONLY**. Các số liệu theo mốc trước là lịch sử.

Trạng thái: **GET user nội bộ và introspection đã triển khai ngày 2026-11-15; PATCH trạng thái đã triển khai ngày 2026-11-17**, tại mốc đó tổng 12 IMPLEMENTED/8 DESIGN_ONLY. Gateway và Service JWT chưa triển khai. Cập nhật cách trình bày: 2026-10-24. Nguồn: Excel Sheet1 hàng 18–19 và [quyết định đã xác nhận](../confirmed-decisions.md). Tài liệu này duy trì trách nhiệm và điều kiện tích hợp; request/response nằm tại bản API hiện hành.

**Đã xác nhận bổ sung ngày 2026-10-31:** [Q03](../confirmed-decisions.md#internal-service-auth) dùng Service JWT riêng cho Admin/Borrow, quyền từng service và header chứng minh actor Admin; [Q10](../confirmed-decisions.md#admin-status-contract) chốt retry/cạnh tranh/audit trạng thái. Gateway dùng Service JWT gọi [introspection](sessions.md#introspection-endpoint-rfc-7662). Cấu hình Service JWT tiếp tục **PENDING DECISION** tại [Q03](../open-decisions.md#q03)/[Q11a](../open-decisions.md#q11a). [User access token Q14](../confirmed-decisions.md#access-token-key-pair) đã chốt riêng; không dùng user token thay danh tính service.

## Giai đoạn chức năng: mặc định tin tưởng service

Theo [quyết định ngày 2026-11-10](../confirmed-decisions.md#deferred-service-auth), khi triển khai chức năng, Admin/Borrow/Gateway mặc định tin tưởng nhau. Hai API nội bộ và introspection chưa yêu cầu Service JWT hoặc xác minh danh tính/quyền service; không cần token service giả hay cấu hình bật riêng dev/test. Xác thực service sẽ được bổ sung sau khi hoàn tất tất cả chức năng.

| Operation | Điều kiện trong giai đoạn chức năng |
| --- | --- |
| GET user nội bộ | Không yêu cầu thông tin xác thực service; vẫn kiểm tra request và xử lý user không tồn tại |
| PATCH trạng thái | Không yêu cầu thông tin xác thực service; vẫn bắt buộc X-Admin-Access-Token, actor ACTIVE, phiên hợp lệ và role ADMIN hiện tại |
| POST introspection | Không yêu cầu thông tin xác thực service; vẫn xác minh user access token từ form, trạng thái user/phiên và roles hiện tại |

Admin/Borrow/Gateway vẫn có trách nhiệm nghiệp vụ như thiết kế; giai đoạn này chưa chứng minh danh tính service gọi hoặc thực thi quyền theo service. Thông tin service trong audit chỉ là ngữ cảnh chưa xác thực. Các kiểm tra actor, cấm khóa ADMIN, transaction/thu hồi phiên và lỗi nghiệp vụ/hạ tầng giữ nguyên. Đợt ghi nhận quyết định ban đầu chỉ đổi docs; ngày 2026-11-15 đã triển khai introspection và GET user nội bộ với chain riêng mặc định tin tưởng service.

## Xác thực và phân quyền service

Phần Service JWT/quyền service dưới đây là **thiết kế đích cho bước sau khi hoàn tất chức năng**. Trong giai đoạn chức năng, bỏ qua hai bước danh tính service/quyền service; kiểm tra actor Admin khi cần rồi xử lý nghiệp vụ. Giữ header X-Admin-Access-Token và xác minh actor ngay khi triển khai API trạng thái.

Admin/Borrow tự ký Service JWT bằng khóa riêng được User Service tin cậy, gửi trong `Authorization: Bearer <service-jwt>`. Chưa chốt thuật toán, issuer/audience/subject, TTL, tập claim hoặc cách phân phối/rotation khóa; không mặc định RS256/5 phút hoặc các giá trị mẫu trong đề xuất đã hoãn.

| Service | Quyền đã xác nhận |
| --- | --- |
| Borrow | users:read |
| Admin | users:read, users:status:write |
| Gateway | tokens:introspect |

User Service giới hạn quyền theo service đã được tin cậy, không cho tự nâng quyền bằng scope trong JWT. Kiểm tra API nội bộ theo thứ tự danh tính service → quyền service → actor Admin khi cần → nghiệp vụ; trả lỗi ở bước đầu tiên không đạt. Cách biểu diễn claim quyền trong JWT vẫn chưa chốt. Introspection giữ lỗi/header riêng.

## Tra cứu user

**IMPLEMENTED ngày 2026-11-15:** [GET /api/v1/internal/users/{id}](current-contract.md#internal-user-get) phục vụ Borrow/Admin. Runtime mặc định tin tưởng service gọi theo quyết định ngày 2026-11-10. ServiceJwt/users:read trong OpenAPI là hợp đồng đích của bước bảo mật service.

`internal.web.InternalUserController` nhận id integer dương/int32, trả ApiResponse<InternalUserSummary> với USER_GET_SUCCESS. DTO đủ năm trường id/userName/fullName/status/roles; roles hiện tại từ database theo tên tăng dần, mảng rỗng trả []. User không tồn tại dùng 404 USER_NOT_FOUND. ID không dương dùng 400 VALIDATION_ERROR/OUT_OF_RANGE; sai kiểu hoặc vượt int32 dùng TYPE_MISMATCH.

`internal.service.InternalUserService.getUser` dùng READ_COMMITTED/readOnly, tái sử dụng UserRepository.findById và RoleRepository.findNamesByUserId. Dựng DTO trong transaction, chỉ trả thành công sau khi transaction hoàn tất. Hai truy vấn theo READ_COMMITTED. Thao tác đọc không khóa user hoặc cập nhật dữ liệu.

Chain stateless riêng khớp đúng mẫu /api/v1/internal/users/{id}, bỏ qua Authorization, kể cả header sai/hết hạn/lặp. Danh tính trong header/query/cookie không tham gia chọn user. Cache-Control no-store áp dụng cả thành công/lỗi. Method ghi tới đúng path dùng lỗi 405 nền tảng; từ ngày 2026-11-17, path /status dùng chain actor Admin riêng, các path con khác tiếp tục được chain user Bearer bảo vệ. Lỗi phụ thuộc tạm thời dùng 503 SERVICE_UNAVAILABLE; lỗi ngoài dự kiến dùng 500 INTERNAL_ERROR.

InternalUserServiceTests kiểm tra mapping/404/lỗi repository; InternalUserHttpTests kiểm tra HTTP/filter/validation/header và ranh giới matcher. InternalUserIntegrationTests chỉ bật khi system property chọn rõ UserDB_test, dùng RSA tạm, fixture theo ID, xác nhận transaction đọc thật, roles hiện tại, ACTIVE/BANNED và dữ liệu trước/sau. Kết quả chạy thực tế tại [development](../development.md#kiểm-thử-hiện-có).

API trả cả user BANNED để service gọi tự kiểm tra nghiệp vụ. HTTP 200 chỉ xác nhận tra cứu thành công, không bảo đảm đủ điều kiện mượn. Borrow Service chịu trách nhiệm kiểm tra điều kiện nghiệp vụ từ status/roles và dữ liệu thuộc phạm vi của mình.

## Cập nhật trạng thái

<a id="cap-nhat-trang-thai-implementation"></a>

### Implementation ngày 2026-11-17

`InternalUserStatusController`/`InternalUserStatusService` đã triển khai PATCH trạng thái. Runtime có chain riêng cho đúng /api/v1/internal/users/{id}/status, bỏ qua Authorization và yêu cầu X-Admin-Access-Token chứa JWT nguyên giá trị, không tiền tố Bearer, không trim; thiếu header trả 401 AUTHENTICATION_REQUIRED, header rỗng/lặp/JWT sai hoặc phiên không hợp lệ trả 401 ADMIN_ACTOR_INVALID. Token/phiên actor hợp lệ nhưng user không ACTIVE hoặc thiếu ADMIN hiện tại trả 403 ACCESS_DENIED. Lỗi database tạm thời vẫn 503, lỗi ngoài dự kiến 500. Mọi response có Cache-Control no-store.

AdminActorAuthenticator xác minh JWT bằng nền tảng hiện có, kiểm tra user tồn tại, phiên đúng owner/chưa thu hồi/chưa hết hạn rồi ACTIVE/ADMIN. Transaction ghi READ_COMMITTED khóa actor và user đích theo ID tăng dần, mỗi ID một lần; lấy now sau đủ khóa và kiểm tra lại JWT/actor/phiên/ADMIN bằng dữ liệu trong transaction. Actor hợp lệ rồi mới trả 404 USER_NOT_FOUND nếu thiếu user đích. Cấm BANNED cho đối tượng ADMIN kể cả tự khóa/trạng thái đã BANNED.

Trạng thái trùng trả 200 USER_STATUS_UPDATE_SUCCESS/data gồm id/status, không ghi user/phiên/token. ACTIVE → BANNED cập nhật status và thu hồi mọi phiên/token chưa thu hồi/chưa hết hạn cùng now/transaction, dùng truy vấn revokeActiveByUser hiện có; token còn hiệu lực của phiên đã kết thúc vẫn được thu hồi. Phiên ghi ACCOUNT_BANNED. BANNED → ACTIVE chỉ đổi trạng thái. Giữ lịch sử, liên kết rotation, metadata, LastSeenAt và dữ liệu ngoài phạm vi. UpdatedAt do trigger V5 quản lý. Chỉ thành công sau commit; lỗi ghi/thu hồi/dựng kết quả/commit rollback.

UserStatusAudit ghi log sau commit, kể cả thao tác cùng trạng thái: actor, target, before/after, thời gian UTC và requestId UUID server tạo. service=UNVERIFIED_INTERNAL_CALLER, serviceAuthenticated=false; không coi thông tin này là danh tính Admin Service đã xác thực. Không ghi JWT/hash/secret; lỗi log không thay kết quả commit. Không có outbox/bảng audit hoặc bảo đảm log nguyên tử/bền vững; Admin Service vẫn chịu trách nhiệm audit yêu cầu/kết quả và đối soát timeout.

InternalUserStatusServiceTests/HttpTests/IntegrationTests kiểm tra hợp đồng, actor, rollback cả COMMIT, cạnh tranh khóa/login/refresh và actor mất hiệu lực lúc chờ khóa. Nhóm tích hợp chỉ chạy trên UserDB_test, RSA tạm, dọn fixture theo ID. Kết quả thực tế tại [development](../development.md#kiểm-thử-hiện-có). Giữ Service JWT/users:status:write trong hợp đồng đích cho bước bảo mật service sau.

**Thiết kế đích sau khi bổ sung xác thực service:** [PATCH /api/v1/internal/users/{id}/status](current-contract.md#internal-user-status) yêu cầu Service JWT của Admin Service với quyền users:status:write và access token quản trị viên trong `X-Admin-Access-Token`. User Service xác minh user token, phiên, user ACTIVE và role ADMIN hiện tại từ database; không nhận actorId/role tự khai thay bằng chứng này. Admin Service khởi xướng và điều phối.

Chính sách [khóa/mở khóa](../confirmed-decisions.md#account-lifecycle) đã chốt. Ghi trạng thái và thu hồi phiên/token khi khóa phải cùng [transaction](../requirements.md#ranh-giới-transaction); mở khóa không phục hồi phiên/token cũ. Theo thiết kế introspection, access token của phiên bị thu hồi bị từ chối ở lần kiểm tra sau commit, không cam kết hủy request đã qua kiểm tra.

Theo [Q10 đã chốt](../confirmed-decisions.md#admin-status-contract), gửi lại trạng thái đang có trả 200 USER_STATUS_UPDATE_SUCCESS, không ghi đè thời điểm/lý do thu hồi cũ. Lệnh khóa/mở khóa cùng user khóa cùng bản ghi và xử lý tuần tự theo database. V1 cấm khóa đối tượng có role ADMIN, kể cả tự khóa, trả 403 ACCESS_DENIED.

Timeout chưa xác định được kết quả commit: Admin đọc lại user để đối soát, không tự retry lệnh trạng thái cũ; gửi lại sau đối soát là lệnh mới. Admin ghi audit yêu cầu/kết quả; User ghi sự kiện gồm service, actor, user đích, trạng thái trước/sau, thời gian và request ID, không ghi dữ liệu xác thực. Không thêm bảng hoặc cam kết audit log ngoài database nguyên tử với transaction.

## Quản lý role ngoài phạm vi V1

[Phạm vi role](../confirmed-decisions.md#roles) giữ nguyên: database hỗ trợ nhiều role; V1 không có API gán/thêm/gỡ/thay thế role. Không mở rộng API trạng thái hoặc API hồ sơ để xử lý role.

## Điều kiện hoàn tất hợp đồng

| Nhóm vấn đề | Cần chốt/đáp ứng trước triển khai |
| --- | --- |
| Danh tính và quyền | Giai đoạn chức năng mặc định tin tưởng service, vẫn xác minh actor theo Q14; chốt [Q03](../open-decisions.md#q03)/[Q11a](../open-decisions.md#q11a) trước bước bổ sung Service JWT sau khi hoàn tất chức năng |
| HTTP | Áp dụng [schema JSON](json-schema.md) và [HTTP/lỗi](http-errors.md), gồm lỗi service/actor ngày 2026-10-31; Q09 chỉ còn tình huống/mã ngoài danh mục |
| Retry và cạnh tranh | [Q10 đã chốt](../confirmed-decisions.md#admin-status-contract): trạng thái đích, tuần tự hóa theo user, đối soát timeout, audit và cấm khóa ADMIN; không bảo đảm exactly-once |
| Dữ liệu | Các writer trạng thái/login/refresh/password/thu hồi phải giữ [tính nhất quán](../requirements.md#tính-nhất-quán-khi-có-request-đồng-thời); không ghi trực tiếp UserDB từ service khác |

Kế hoạch kiểm thử khi được yêu cầu code chức năng: gọi nội bộ không có Service JWT, actor Admin thiếu/sai/không đủ quyền vẫn bị từ chối, user không tồn tại, tra cứu BANNED, khóa/mở khóa lặp, cạnh tranh khóa với login/refresh, không phục hồi phiên/token và introspection sau thu hồi. Kiểm thử danh tính/quyền service và chặn caller không được phép được thực hiện ở bước bổ sung Service JWT sau khi hoàn tất chức năng. Kiểm thử API quản lý role thuộc phiên bản mở rộng. Các ca GET user nội bộ đã có kiểm thử ngày 2026-11-15. PATCH trạng thái đã có kiểm thử ngày 2026-11-17; các ca Service JWT tiếp tục là kế hoạch.
