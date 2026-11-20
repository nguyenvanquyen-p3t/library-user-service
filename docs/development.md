# Hướng dẫn phát triển và kiểm thử

Biên tập ban đầu ngày 2026-10-24; bổ sung hạ tầng tài liệu ngày 2026-11-01. Chủ dự án đã yêu cầu [OpenAPI/Swagger UI](api/openapi.md), gồm kiểm thử tài liệu tách database. Các bước code và kiểm thử API nghiệp vụ bên dưới chỉ áp dụng khi chủ dự án yêu cầu triển khai phần tương ứng.

## Bắt đầu

Theo [README dự án](../README.md) để bắt đầu nhanh hoặc [hướng dẫn môi trường cục bộ](local-development.md) để cấu hình, đóng gói và xử lý lỗi. Cần JDK 25, PostgreSQL 15+ và `backend/.env`. Lệnh Maven phải chạy từ `backend/` để import `.env` đúng vị trí. Không lưu mật khẩu thực vào docs hoặc commit `.env`.

## Quy trình thay đổi

1. Đọc [kiến trúc](architecture.md), [quyết định đã xác nhận](confirmed-decisions.md) và use case liên quan. Phạm vi 7 bảng/20 API; không có API role, blacklist hoặc Redis. Đã có migration, docs, OpenAPI/Swagger, [nền tảng backend](foundation.md) và [bảo mật/login UC02](user-security.md); Ngày 2026-11-11 thêm refresh/logout UC03–UC04; GET/PATCH hồ sơ UC06 bổ sung ngày 2026-11-12; [Đổi mật khẩu UC07](user-password.md) bổ sung ngày 2026-11-13; ngày 2026-11-14 thêm [quản lý phiên UC10–UC12](user-sessions.md); ngày 2026-11-15 thêm [introspection](api/sessions.md#introspect); sau đó cùng ngày thêm GET user nội bộ; ngày 2026-11-17 thêm PATCH trạng thái. Ngày 2026-11-20 thêm [UC01](user-registration.md). Sáu operation chờ yêu cầu triển khai. Hỏi chủ dự án trước khi giải quyết yêu cầu chưa rõ.
2. Tra cứu operation tại [bản đồ API](api/README.md) và bản hiện hành được liên kết; danh mục nguồn chỉ để truy vết Excel. Chốt phần request/response/auth/lỗi liên quan còn PENDING DECISION trước khi tích hợp; riêng cấu hình Service JWT Q03/Q11a chốt ở bước sau khi hoàn tất chức năng theo quyết định ngày 2026-11-10.
3. Khi được yêu cầu triển khai, viết code và kiểm thử hành vi; thêm migration mới nếu cần thay schema đã được chia sẻ. Với yêu cầu docs-only, chỉ sửa và kiểm tra tài liệu.
4. Cập nhật trạng thái docs theo phần thực sự hoàn thành. Ghi rõ kiểm thử đã chạy và giới hạn môi trường trong PR.

Đã có package theo chức năng, entity/repository, DTO và [nền tảng bảo mật/login UC02](user-security.md) ngày 2026-11-10. Controller/service login, refresh và logout đã triển khai; UC03–UC04 bổ sung ngày 2026-11-11. [GET/PATCH hồ sơ UC06](user-profile.md) đã triển khai ngày 2026-11-12. Các phần tiếp theo giữ transaction tách khỏi xử lý HTTP, không trả entity chứa hash và khóa cùng bản ghi user để tuần tự với login. Quy tắc nền tảng được duy trì tại [foundation.md](foundation.md).

## Kiểm thử hiện có

**Kết quả ngày 2026-11-20 — đăng ký Local UC01.** Toàn bộ suite **186 test** thành công, 0 lỗi và 0 bỏ qua. Bổ sung 11 test gồm 2 unit HMAC, 3 HTTP và 6 tích hợp UC01. Dùng Java 25.0.4.1, Maven Wrapper, PostgreSQL 18.4 tạm với `UserDB_test` và SMTP giả lập qua loopback. Kiểm tra commit trước SMTP, mã cũ mất hiệu lực khi gửi lỗi, biên giới hạn gửi và TTL, lần nhập sai thứ 5, yêu cầu đồng thời, rollback khi trùng username hoặc lỗi COMMIT và đăng nhập qua UC02 sau đăng ký. OpenAPI hợp lệ với 20 operation, gồm **14 IMPLEMENTED/6 DESIGN_ONLY**. Log suite tại `backend/target/uc01-full.log`.

**Kết quả ngày 2026-11-17 — PATCH trạng thái user nội bộ:** nhóm tập trung **17 test** (5 service, 5 HTTP, 7 tích hợp) và toàn bộ suite **175 test** đều thành công, không lỗi hoặc bỏ qua. Dùng Java 25.0.4.1/Maven Wrapper, PostgreSQL 18.4 tạm biệt lập tại `backend/target/status-postgres`, chỉ loopback cổng 55839, database UserDB_test, tài khoản status_fixture; bỏ import .env và dùng RSA tạm. Flyway V1–V6 và Hibernate validate chạy thành công; giữ nguyên migration, database và khóa dự án.

Đã kiểm tra JWT nguyên giá trị/header actor riêng, bỏ qua Authorization trong giai đoạn hoãn Service JWT, lỗi actor/validation/HTTP/hạ tầng, ACTIVE/BANNED và cấm khóa ADMIN. Test tích hợp kiểm tra thu hồi chỉ bản ghi còn hiệu lực, giữ lịch sử/rotation/identity/roles, không phục hồi sau mở khóa, introspection inactive sau khóa, rollback cả lỗi COMMIT; login/refresh cạnh tranh khóa ở cả hai thứ tự, lệnh trạng thái tuần tự và hai ADMIN thao tác chéo. Actor mất hiệu lực trong lúc chờ khóa thật bị từ chối, gồm thu hồi phiên, hết hạn phiên tại biên, JWT hết dung sai, mất ADMIN và BANNED. Audit được quan sát sau commit bằng transaction đọc độc lập; lỗi log không đổi kết quả thành công.

OpenAPI hợp lệ với 20 operation, **12 IMPLEMENTED/8 DESIGN_ONLY**; 210 ví dụ và 22 ca schema đạt kết quả mong đợi. Đã kiểm tra Markdown, liên kết/anchor và 84 block JSON. Log tại `backend/target/status-focused.log`, `backend/target/status-integration.log`, `backend/target/status-full.log`; script kiểm tra tại `backend/target/check_status_docs.py`. Lượt đầu Maven trong sandbox không đọc được cache; lần chạy thành công dùng cache hiện hữu ngoài sandbox. Một lượt toàn bộ suite phát hiện lỗi suy luận kiểu trong test audit mới bổ sung; đã sửa khai báo kiểu, lượt 175 test thành công đã kiểm tra lại. Validator cũng dùng thư viện hiện hữu ngoài sandbox, không cài dependency mới.

Lệnh toàn bộ suite từ backend/ (PostgreSQL tạm dùng trust trên loopback, tài khoản fixture không có mật khẩu):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55839/UserDB_test" "-Dspring.datasource.username=status_fixture" "-Dspring.datasource.password=" test
```

Các kết quả theo ngày bên dưới là lịch sử, không phải lần chạy lại sau PATCH trạng thái.

Sau suite ngày 2026-11-17 đã đối soát Users/UserRoles/UserIdentities/UserSessions/RefreshTokens/VerificationCodes đều rỗng, đủ sáu migration thành công ở V6; PostgreSQL tạm đã dừng.

Ngày 2026-11-15 bổ sung InternalUserServiceTests, InternalUserHttpTests và InternalUserIntegrationTests cho [GET user nội bộ](api/internal-integration.md#tra-cứu-user). Nhóm tích hợp chỉ bật khi system property chọn rõ UserDB_test; dùng khóa tạm và dọn fixture theo ID.

Ngày 2026-11-14 thêm UserSessionServiceTests, UserSessionHttpTests và UserSessionIntegrationTests cho [UC10–UC12](user-sessions.md). Test tích hợp chỉ bật với UserDB_test; dùng RSA tạm, fixture theo ID và chờ khóa thực.

Ngày 2026-11-13 thêm ChangePasswordServiceTests, ChangePasswordHttpTests và ChangePasswordIntegrationTests cho [UC07](user-password.md). Test tích hợp chỉ bật khi system property chọn rõ UserDB_test; dùng khóa tạm, dọn fixture theo ID, kiểm tra rollback cả COMMIT và cạnh tranh chờ khóa user thực.

Ngày 2026-11-12 bổ sung UserProfileServiceTests, UserProfileHttpTests và UserProfileIntegrationTests; kiểm tra UC06 và xác thực lại user/phiên sau khóa user. Nhóm tích hợp chỉ bật khi chọn rõ UserDB_test, dùng khóa tạm và dọn đúng fixture theo ID.

Ngày 2026-11-11 bổ sung `RefreshLogoutServiceTests` (Clock/biên thời gian/thứ tự lỗi), mở rộng `UserSecurityHttpTests` và thêm `RefreshLogoutIntegrationTests` (rotation/logout, rollback tại COMMIT và cạnh tranh có kiểm soát). Test tích hợp chỉ bật khi system property chọn rõ UserDB_test, dùng khóa tạm và dọn đúng ID fixture.

Đã bổ sung `UserSecurityTests` (Argon2id/JWT/khóa và biên thời gian), `UserSecurityHttpTests` (filter chain thật với repository giả lập, không database) và `LoginIntegrationTests` (HTTP/login trên PostgreSQL, rollback lỗi lưu/ký/COMMIT, cạnh tranh khóa user, OpenAPI trong context thật). Nhóm không database dùng lệnh `.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests,UserSecurityTests,UserSecurityHttpTests,RefreshLogoutServiceTests,UserProfileServiceTests,UserProfileHttpTests,ChangePasswordServiceTests,ChangePasswordHttpTests,UserSessionServiceTests,UserSessionHttpTests,IntrospectionServiceTests,IntrospectionHttpTests,InternalUserServiceTests,InternalUserHttpTests,InternalUserStatusServiceTests,InternalUserStatusHttpTests" test` từ `backend/`.

Test context/persistence/login dùng khóa RSA ngẫu nhiên trong thư mục tạm, không dùng khóa dự án. `LoginIntegrationTests` chỉ bật khi system property chọn rõ `UserDB_test`; test có commit thật và dọn đúng user fixture bằng ID sau mỗi ca, khác với rollback fixture của test persistence. Không thêm database test tự động hoặc thay V1–V6. Kết quả chạy mới được ghi bên dưới; kết quả 2026-11-03 tiếp tục là lịch sử nền tảng trước UC02.

`FoundationTests` kiểm tra DTO/validation/exception bằng mapper ứng dụng và controller chỉ trong test; `ApiDocumentationTests` kiểm tra hạ tầng Swagger. Hai lớp này không đọc `.env`, không dùng datasource/Flyway. Chạy từ `backend/`:

```powershell
.\mvnw.cmd "-Dtest=FoundationTests,ApiDocumentationTests" test
```

`PersistenceFoundationTests` và `UserServiceApplicationTests.contextLoads` tải Spring context cùng datasource/Flyway. Test persistence chỉ bật khi system property chọn rõ `UserDB_test`; fixture rollback. UC01 dùng SMTP giả lập trên loopback. Login đã có test endpoint riêng. Database test phải chuẩn bị trước; không chạy migration trên database dùng chung để kiểm tra docs. Xem [giới hạn rollback](foundation.md#kiểm-thử).

**Kết quả ngày 2026-11-03:** đã chạy toàn bộ 27 test bằng JDK 25/Maven Wrapper: 17 nền tảng, 5 persistence, 4 tài liệu và 1 context; không lỗi hoặc bỏ qua. Do `UserDB_test` tại cổng 5432 chưa tồn tại, kiểm thử dùng PostgreSQL 18.4 tạm biệt lập trong `backend/target/`, cổng riêng trên loopback và database có tên `UserDB_test`; bỏ import `.env`, dùng tài khoản fixture. Flyway V1–V6 và Hibernate validate đã chạy trên instance tạm; không dùng/thay đổi database dự án. Instance tạm được dừng sau kiểm thử. Đây là kết quả nền tảng, không phải xác nhận 20 API nghiệp vụ đã hoạt động.

**Kết quả ngày 2026-11-10 — UC02:** chạy toàn bộ **45 test**, không lỗi hoặc bỏ qua: 17 nền tảng, 5 persistence, 4 tài liệu, 8 crypto/biên phiên, 4 HTTP security, 6 login tích hợp và 1 context. Dùng JDK 25/Maven Wrapper, PostgreSQL 18.4 tạm tại loopback cổng 55432, dữ liệu `backend/target/uc02-postgres`, database `UserDB_test`, bỏ import `.env` và dùng tài khoản fixture. V1–V6 chạy thành công trên database mới, Hibernate validate 7 entity; lần cuối xác nhận schema vẫn V6. Test chứng minh rollback cả lỗi COMMIT database, login đồng thời và chờ khóa user rồi kiểm tra trạng thái mới. Khóa RSA của test tự sinh trong thư mục tạm; không đọc/thay khóa dự án. Không dùng database dự án. OpenAPI được kiểm tra bằng openapi-spec-validator; 23 Markdown/610 link và 84 block JSON hợp lệ, đủ 20 operation với 1 IMPLEMENTED/19 DESIGN_ONLY. Không coi kết quả này là kiểm thử 19 API còn lại.

**Kết quả ngày 2026-11-11 — UC03–UC04:** đã chạy nhóm không database **43 test** và toàn bộ suite **64 test**, không lỗi hoặc bỏ qua. Toàn bộ suite gồm 17 nền tảng, 5 persistence, 4 tài liệu, 8 crypto/biên phiên, 5 HTTP security, 9 service refresh/logout, 6 login tích hợp, 9 refresh/logout tích hợp và 1 context. Dùng Java 25/Maven Wrapper, PostgreSQL 18.4 tạm biệt lập trong `backend/target/uc0304-postgres`, loopback cổng 55433, database UserDB_test, tài khoản fixture và bỏ import `.env`. Flyway V1–V6 chạy trên database mới, Hibernate validate 7 entity; giữ nguyên migration/database dự án. Đã chứng minh rotation cùng sid, logout bằng token cũ, rollback cả lỗi COMMIT, hai refresh chỉ một thành công và refresh/logout tuần tự ở cả hai thứ tự có chờ khóa thực. Khóa test tự sinh; fixture dọn theo ID. Kiểm tra cuối không còn user/session/refresh token fixture, đủ 6 migration thành công ở V6; PostgreSQL tạm đã dừng. OpenAPI đã validate, 23 Markdown/622 link và 84 block JSON hợp lệ; 3 IMPLEMENTED/17 DESIGN_ONLY. Kết quả 2026-11-03/10 ở trên tiếp tục là lịch sử, không thay thế test 17 operation còn lại.

Lệnh toàn bộ suite ngày 2026-11-11 đã dùng từ `backend/` (credentials chỉ là fixture của instance tạm):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55433/UserDB_test" "-Dspring.datasource.username=uc0304_fixture" "-Dspring.datasource.password=uc0304-test-only" test
```

**Kết quả ngày 2026-11-12 — UC06:** nhóm không database **55 test** và toàn bộ suite **84 test** đều thành công, không lỗi hoặc bỏ qua. Bổ sung 7 service, 5 HTTP và 8 tích hợp UC06 trên 64 test hiện hữu. Dùng Java 25.0.4.1/Maven Wrapper, PostgreSQL 18.4 tạm biệt lập trong `backend/target/uc06-postgres`, chỉ loopback cổng 55434, database UserDB_test, tài khoản fixture và bỏ import `.env`. Flyway V1–V6 chạy trên database mới, Hibernate validate; giữ nguyên migration, database và khóa dự án. Các ca mới chứng minh ownership/principal, PATCH presence/null, role/provider hiện tại, trigger UpdatedAt, không sửa phiên/token, rollback lỗi lưu/dựng DTO/COMMIT, hai PATCH chờ khóa thực và kiểm tra lại user/phiên sau khi chờ (BANNED, thu hồi, hết hạn tại biên Clock). Trước khi dừng PostgreSQL tạm đã xác nhận không còn user/session/refresh token fixture và đủ 6 migration thành công ở V6. PostgreSQL tạm đã dừng. OpenAPI hợp lệ với 20 operation, đúng 5 IMPLEMENTED/15 DESIGN_ONLY; 210 ví dụ và 22 ca schema hợp lệ theo mong đợi; kiểm tra 24 Markdown, 651 link và 84 block JSON thành công. Các kết quả ngày trước giữ nguyên là lịch sử; 15 operation còn thiết kế chưa được kiểm thử nghiệp vụ.

Lệnh toàn bộ suite UC06 từ `backend/` (cluster test tạm dùng trust trên loopback, tài khoản fixture không có mật khẩu):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55434/UserDB_test" "-Dspring.datasource.username=uc06_fixture" "-Dspring.datasource.password=" test
```

**Kết quả ngày 2026-11-13 — UC07:** nhóm không database **66 test** và toàn bộ suite **103 test** đều thành công, không lỗi hoặc bỏ qua. Bổ sung 6 service, 5 HTTP và 8 tích hợp UC07 trên 84 test hiện hữu. Dùng Java 25.0.4.1/Maven Wrapper, PostgreSQL 18.4 tạm biệt lập trong `backend/target/uc07-postgres`, chỉ loopback cổng 55435, database UserDB_test và tài khoản uc07_fixture; bỏ import .env, dùng RSA tạm. Flyway V1–V6 chạy trên database mới, Hibernate validate; không thay migrations, database hoặc khóa dự án. Đã kiểm tra mật khẩu Unicode/khoảng trắng và cùng mật khẩu, thu hồi đúng mọi phiên/token còn hiệu lực, giữ lịch sử đã kết thúc/rotation/Google identity/roles/LastSeenAt, rollback lỗi hash/lưu/thu hồi/COMMIT, hai UC07 đồng thời, login/refresh ở cả hai thứ tự và kiểm tra lại user/phiên sau chờ khóa thật. Trước khi dừng PostgreSQL tạm đã xác nhận Users/UserSessions/RefreshTokens đều không còn fixture và đủ sáu migration thành công ở V6. PostgreSQL tạm đã dừng.

OpenAPI hợp lệ với 20 operation, đúng 6 IMPLEMENTED/14 DESIGN_ONLY; 210 ví dụ và 22 ca schema đạt kết quả mong đợi. Kiểm tra 25 Markdown, 680 link và 84 block JSON thành công. Các kết quả theo ngày trước giữ nguyên là lịch sử; 14 operation còn thiết kế chưa được kiểm thử nghiệp vụ.

Lệnh toàn bộ suite UC07 từ `backend/` (cluster test tạm dùng trust trên loopback, tài khoản fixture không có mật khẩu):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55435/UserDB_test" "-Dspring.datasource.username=uc07_fixture" "-Dspring.datasource.password=" test
```

**Kết quả ngày 2026-11-14 — UC10–UC12:** nhóm không database **80 test** và toàn bộ suite **127 test** đều thành công, không lỗi hoặc bỏ qua. Bổ sung 9 service, 5 HTTP và 10 tích hợp trên 103 test hiện hữu. Dùng Java 25.0.4.1/Maven Wrapper, PostgreSQL 18.4 tạm biệt lập tại `backend/target/uc1012-postgres`, chỉ loopback cổng 55836, database UserDB_test, tài khoản uc1012_fixture; bỏ import .env, dùng RSA tạm. Cổng dự kiến 55436 thuộc dải Windows dành riêng nên không dùng được; đã chọn 55836 sau khi kiểm tra dải cổng. Flyway V1–V6 chạy trên database mới, Hibernate validate; không sửa migration/database/khóa dự án.

Đã kiểm tra ownership, metadata/null, phân trang/UUID và trang rất lớn, snapshot count/trang khi có thu hồi xen kẽ, UC11 không chọn phiên hiện tại, UC12 giữ phiên hiện tại/đếm phiên, bảo toàn lịch sử token/rotation/LastSeenAt, rollback cả lỗi COMMIT, thu hồi lặp và cạnh tranh refresh/login ở cả hai thứ tự. Các writer kiểm tra lại caller sau khi thực sự chờ khóa, kể cả hết hạn tại biên Clock; pg_stat_activity xác nhận chờ khóa thật. Sau kiểm thử đã đối soát Users/UserSessions/RefreshTokens đều không còn fixture và đủ sáu migration thành công ở V6; PostgreSQL tạm đã dừng.

OpenAPI hợp lệ với 20 operation, đúng **9 IMPLEMENTED/11 DESIGN_ONLY**; 210 ví dụ và 22 ca schema đạt kết quả mong đợi. Kiểm tra 26 Markdown, 719 link và 84 block JSON thành công. Kiểm tra lại tài liệu/context thật sau đồng bộ trạng thái có 10 test thành công; không coi kết quả này là kiểm thử 11 operation còn thiết kế.

Lệnh toàn bộ suite UC10–UC12 từ backend/ (cluster test tạm dùng trust trên loopback, tài khoản fixture không có mật khẩu):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55836/UserDB_test" "-Dspring.datasource.username=uc1012_fixture" "-Dspring.datasource.password=" test
```

**Kết quả ngày 2026-11-15 — introspection:** đã chạy nhóm tập trung không database **19 test** (4 service introspection, 7 HTTP introspection và 8 user security); nhóm tích hợp/tài liệu **11 test** (7 introspection tích hợp và 4 Swagger); toàn bộ suite **145 test**, đều thành công, không lỗi hoặc bỏ qua. So với 127 test hiện hữu, bổ sung 18 test introspection. Dùng Java 25.0.4.1/Maven Wrapper và PostgreSQL 18.4 tạm biệt lập tại backend/target/introspection-postgres, chỉ loopback cổng 55837, database UserDB_test, tài khoản introspection_fixture; bỏ import .env và dùng RSA tạm. Flyway V1–V6 chạy trên database mới, Hibernate validate; không sửa migration, database hoặc khóa dự án.

Đã kiểm tra form body độc lập query/cookie, từ chối tham số lặp/trường lạ/encoding sai, không trim token, bỏ qua Authorization trong giai đoạn hoãn Service JWT, active/inactive và lỗi/header riêng (gồm OPTIONS/HEAD), lỗi hạ tầng không trở thành inactive. Test tích hợp xác nhận transaction đọc thật, biên JWT 30 giây và hạn phiên tuyệt đối, ownership, user BANNED/không tồn tại, roles hiện tại và dữ liệu không đổi; access token cũ vẫn active sau rotation, inactive sau logout/đổi mật khẩu/UC11/UC12 commit. Kiểm thử ban đầu phát hiện Nimbus chấp nhận khoảng trắng bao quanh JWT và servlet dựng POST form từ parameter map có query; implementation đã xử lý cả hai và các lần chạy thành công ở trên đã kiểm tra lại. Trước khi dừng PostgreSQL tạm đã đối soát Users/UserSessions/RefreshTokens đều không còn fixture và đủ sáu migration thành công ở V6. PostgreSQL tạm đã dừng.

OpenAPI hợp lệ với 20 operation, đúng **10 IMPLEMENTED/10 DESIGN_ONLY**; 210 ví dụ và 22 ca schema đạt kết quả mong đợi. Kiểm tra 26 Markdown, liên kết/anchor và 84 block JSON thành công. Giữ schema/quyền/lỗi 401 Service JWT là hợp đồng đích, mô tả rõ chưa áp dụng runtime; Gateway và hai API user nội bộ chưa triển khai. Các số liệu/lệnh theo ngày trước giữ nguyên là lịch sử.

Lệnh toàn bộ suite introspection từ backend/ (cluster test tạm dùng trust trên loopback, tài khoản fixture không có mật khẩu):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55837/UserDB_test" "-Dspring.datasource.username=introspection_fixture" "-Dspring.datasource.password=" test
```

Log kiểm thử và script kiểm tra của các lần chạy nằm trong `backend/target/`, không được Git theo dõi. Lượt đầu Maven UC07, UC10–UC12 và introspection trong sandbox không đọc được dependency cache nên chưa chạy test; các lần thành công dùng quyền truy cập cache hiện hữu. Khởi động PostgreSQL tạm và công cụ validate Python của đợt introspection cũng cần quyền ngoài sandbox; đã thực hiện thành công, không dùng database dự án hoặc cài dependency mới. Lỗi thử nghiệm trước khi sửa không được tính là lần kiểm thử thành công.

**Kết quả tiếp ngày 2026-11-15 — GET user nội bộ:** nhóm tập trung **29 test** và toàn bộ suite **158 test** thành công, không lỗi hoặc bỏ qua. Bổ sung 13 test gồm 3 service, 6 HTTP và 4 tích hợp trên 145 test hiện hữu. Dùng Java 25.0.4.1/Maven Wrapper và PostgreSQL 18.4 tạm biệt lập tại `backend/target/internal-user-get-postgres`, chỉ loopback cổng 55838, database UserDB_test, tài khoản internal_user_fixture; bỏ import .env, dùng khóa RSA tạm từ UserSecurityTestKeys. Flyway V1–V6 chạy trên database mới, Hibernate validate.

Đã kiểm tra summary đúng năm trường, ACTIVE/BANNED, roles hiện tại/sắp xếp/mảng rỗng, ID dương/int32, 404, lỗi 405/500/503 và cache header. HTTP chứng minh bỏ qua Authorization kể cả sai/lặp trong giai đoạn tin tưởng service; path /status/path con và API user vẫn được bảo vệ. Test tích hợp xác nhận transaction readOnly/READ_COMMITTED thật bằng trạng thái Spring và SHOW của PostgreSQL, dữ liệu user/roles/identity/session/token trước và sau thao tác đọc. Sau suite đã đối soát Users/UserRoles/UserIdentities/UserSessions/RefreshTokens/VerificationCodes đều rỗng và đủ sáu migration thành công ở V6; PostgreSQL tạm đã dừng.

OpenAPI hợp lệ với 20 operation, đúng **11 IMPLEMENTED/9 DESIGN_ONLY**; 210 ví dụ và 22 ca schema đạt kết quả mong đợi. Kiểm tra 26 Markdown, liên kết/anchor và 84 block JSON thành công. ServiceJwt/users:read và lỗi 401/403 của GET nội bộ tiếp tục mô tả hợp đồng đích. Lần chạy đầu phát hiện thiết lập mock gọi lại stub đang ném lỗi, cách gọi repository proxy trong test transaction và kỳ vọng cũ đúng 10 operation ở test context; đã sửa test, các lần thành công ở trên kiểm tra lại. Maven/validator dùng quyền truy cập cache hiện hữu ngoài sandbox; khởi động/dừng PostgreSQL tạm cũng cần quyền ngoài sandbox. Migration, database và khóa dự án được giữ nguyên trong đợt này.

Lệnh toàn bộ suite GET user nội bộ từ backend/ (cluster test tạm dùng trust trên loopback, tài khoản fixture không có mật khẩu):

```powershell
.\mvnw.cmd -o "-Dspring.config.import=" "-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55838/UserDB_test" "-Dspring.datasource.username=internal_user_fixture" "-Dspring.datasource.password=" test
```

Log tại `backend/target/internal-user-focused-integration.log` và `backend/target/internal-user-full.log`; script kiểm tra trong `backend/target/`. Các kết quả theo ngày trước tiếp tục là lịch sử.

## Kế hoạch kiểm thử khi triển khai

Instance PostgreSQL tạm của đợt UC02 ngày 2026-11-10 đã dừng sau kiểm thử; trước khi dừng đã xác nhận không còn user/session/refresh token fixture và đủ 6 migration thành công. Các mục chưa triển khai trong kế hoạch dưới đây dành cho đợt tiếp theo.

**Thứ tự ngày 2026-11-10:** [hoàn tất chức năng trước, bổ sung xác thực service sau](confirmed-decisions.md#deferred-service-auth). Trong giai đoạn chức năng, các service mặc định tin tưởng nhau; kiểm thử hai API nội bộ/introspection không yêu cầu Service JWT và vẫn kiểm tra user/phiên, ownership, actor ADMIN khi cần. Các ca danh tính/quyền service, Service JWT thiếu/sai/hết hạn và chặn caller không được phép trong kế hoạch dưới đây dành cho bước bổ sung xác thực service sau. Không đánh dấu đã hoàn tất bảo mật service từ kết quả test nghiệp vụ. Giữ lớp xác thực service tách khỏi logic nghiệp vụ/transaction để bổ sung sau.

| Nhóm | Hành vi cần chứng minh |
| --- | --- |
| Đăng ký/mã xác minh | Trùng sau chuẩn hóa, sai purpose, mã sai/hết hạn/đã dùng, cạnh tranh dùng cùng mã, hạn thử/gửi lại đã thống nhất |
| Đăng nhập | Local có/không password, ACTIVE/BANNED, lỗi thông tin đăng nhập, Google token sai và các nhánh liên kết |
| Refresh/logout | Rotation một lần, hai request cùng token, token hết hạn/thu hồi, logout lặp lại, khóa cạnh tranh refresh |
| Hồ sơ/mật khẩu | Chỉ sửa trường cho phép, không sửa user khác, đối chiếu password, thu hồi mọi phiên/refresh token theo luồng, giữ identity Google |
| API nội bộ | Xác thực service, phân quyền từng thao tác, chặn client trực tiếp, không lộ hash/token, user BANNED vẫn được tra cứu |
| Database | Migration từ DB rỗng và phiên bản trước, chuẩn hóa/unique, FK token, trigger UpdatedAt, rollback transaction khi lỗi |
| Hợp đồng | Schema request/response, HTTP status/messageCode và các biến thể Google đúng hợp đồng được xác nhận |

Khi triển khai, kiểm thử JWT RS256, Service JWT Gateway độc lập, quyền sở hữu phiên, UC11 từ chối phiên hiện tại, UC12 giữ phiên hiện tại và introspection sau thu hồi. Mở khóa không khôi phục phiên/token cũ. Kiểm thử tại mốc [session hết hạn](confirmed-decisions.md#session-lifetime) dù token còn hạn riêng, rotation không gia hạn session và LastSeenAt không quyết định hết hạn. Cơ chế kiểm tra phiên đã chốt; cấu hình Service JWT còn Q11a. Các ca cho endpoint chưa triển khai vẫn là kế hoạch tương lai; test login UC02 và nền tảng user security đã có, không thay thế test các API còn lại.

Đây là kế hoạch, không phải kết quả đã pass. Kiểm thử về constraint/transaction cần PostgreSQL phù hợp thay vì giả định database in-memory có cùng hành vi.

## Khi sửa tài liệu

Theo [nơi duy trì chính và quy ước bảo trì](documentation.md#noi-duy-tri-chinh), kiểm tra link tương đối/anchor, JSON, method/path, mapping UC/API và trạng thái ví dụ. Đối chiếu phiên bản/cấu hình với repo khi sửa hướng dẫn chạy. Không chạy ứng dụng, Maven hoặc chạm database chỉ để sửa Markdown. Khi được yêu cầu sửa schema/code, chạy kiểm thử phù hợp và chỉ ghi kết quả đã thực hiện; thay đổi thiết kế API trong Markdown không phải bằng chứng endpoint đã được kiểm thử.
