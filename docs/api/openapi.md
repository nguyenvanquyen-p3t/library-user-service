# OpenAPI và Swagger UI

Ngày 2026-11-01, chủ dự án yêu cầu xây dựng OpenAPI và Swagger UI. Ngày 2026-11-10 đã triển khai [bảo mật người dùng/login UC02](../user-security.md); **login/refresh/logout, GET/PATCH hồ sơ UC06 và đổi mật khẩu UC07 IMPLEMENTED, 14 operation khác DESIGN_ONLY (cập nhật ngày 2026-11-13)**. Không thay migration hoặc tự chốt các quyết định còn mở.

## Đặc tả và trạng thái

[user-service.yaml](../../backend/src/main/resources/openapi/user-service.yaml) là bản OpenAPI **3.0.3**, phiên bản hợp đồng `0.1.0-design`. File mô tả đủ 20 operation theo [bản đồ API](README.md), giữ operationId/schema/validation/ví dụ và lỗi. Login/refresh/logout, GET/PATCH hồ sơ UC06 và đổi mật khẩu UC07 có `x-implementation-status: IMPLEMENTED`, 14 operation khác `DESIGN_ONLY`; x-design-source giữ liên kết Markdown. Response login/refresh/logout có Cache-Control no-store; hai API UC03–UC04 áp dụng cả response lỗi. Controller login/refresh/logout, hồ sơ và đổi mật khẩu mang `@Hidden` để YAML không bị springdoc ghi đè; test trong context thật kiểm tra đủ 20 operation và trạng thái.

[OpenApiConfiguration](../../backend/src/main/java/com/library/userservice/config/OpenApiConfiguration.java) đọc YAML từ classpath thành bean `OpenAPI`. springdoc phục vụ cùng hợp đồng ở JSON/YAML và Swagger UI. Không tạo controller giả cho các operation thiết kế. Khi triển khai controller sau này, phải đối chiếu hợp đồng này; tránh để annotation và YAML mô tả khác nhau hoặc làm mất nhãn trạng thái thực tế.

19 operation dùng envelope; introspection dùng form `application/x-www-form-urlencoded` và JSON riêng. Ba response Google phân biệt bằng `oneOf`, hai cờ và `messageCode`; required và nullable được mô tả riêng. Schema request từ chối trường lạ, PATCH hồ sơ cần ít nhất một trường. Các giới hạn username/email/fullName áp dụng **sau chuẩn hóa**, nên mô tả trong `description` thay vì áp sai độ dài lên chuỗi thô; xác nhận mật khẩu và kiểm tra quyền/phiên cũng cần xử lý phía server khi triển khai.

## Mở tài liệu

Chạy ứng dụng từ `backend/` theo [hướng dẫn cục bộ](../local-development.md). Khởi động ứng dụng bình thường sẽ kết nối PostgreSQL và chạy Flyway; **V6 yêu cầu RefreshTokens hoàn toàn rỗng**, không xóa/thu hồi token để vượt điều kiện.

```powershell
.\mvnw.cmd spring-boot:run
```

| Địa chỉ mặc định | Nội dung |
| --- | --- |
| `http://localhost:5001/swagger-ui.html` | Chuyển hướng tới Swagger UI |
| `http://localhost:5001/v3/api-docs` | OpenAPI JSON |
| `http://localhost:5001/v3/api-docs.yaml` | OpenAPI YAML |
| `http://localhost:5001/v3/api-docs/swagger-config` | Cấu hình Swagger UI |

Swagger UI tắt `Try it out` bằng `supported-submit-methods: []`, không lưu thông tin Authorize và không gửi đặc tả tới validator bên ngoài. Token trong ví dụ là dữ liệu giả. User JWT đã hoạt động trong nền tảng bảo mật; Authorize không làm operation DESIGN_ONLY trở thành endpoint đã triển khai.

[DocumentationSecurityConfiguration](../../backend/src/main/java/com/library/userservice/config/DocumentationSecurityConfiguration.java) cho phép GET tài liệu và tài nguyên `/swagger-ui/**`. ApiSecurityConfiguration mở chính xác path login/refresh/logout (mọi method, bỏ qua Bearer cũ) và bảo vệ các request còn lại bằng user Bearer. Runtime stateless, đã tắt Basic/form login/logout mặc định/CSRF và default user; không mở công khai `/api/v1/**`. Ba endpoint nội bộ vẫn chưa triển khai; xem [security hiện hành](../user-security.md).

## Xác thực trong hợp đồng

**Cập nhật ngày 2026-11-10:** theo [quyết định hoãn xác thực service](../confirmed-decisions.md#deferred-service-auth), YAML giữ scheme/quyền/lỗi Service JWT của hợp đồng đích sau khi hoàn tất chức năng. Giai đoạn chức năng mặc định tin tưởng service; API trạng thái tương lai vẫn xác minh actor Admin và introspection tương lai vẫn xác minh user token/phiên. ServiceJwt + AdminAccessToken chỉ áp dụng đầy đủ ở bước bảo mật service sau. Login đã triển khai không đồng nghĩa đã có ba operation nội bộ.

| Scheme | Ý nghĩa |
| --- | --- |
| `UserAccessToken` | User JWT RS256 trong Authorization Bearer, theo Q14; user ACTIVE và phiên hợp lệ |
| `ServiceJwt` | Service JWT riêng cho Admin/Borrow/Gateway; quyền của từng operation ghi tại `x-required-service-permission` |
| `AdminAccessToken` | Header `X-Admin-Access-Token`, dùng **đồng thời** ServiceJwt khi cập nhật trạng thái; actor ACTIVE, phiên hợp lệ và có ADMIN hiện tại từ database |

Refresh/logout dùng refresh token trong JSON, không yêu cầu user access token. Introspection dùng Service JWT ở Authorization và user access token ở trường form `token`; không dùng chung hai danh tính. Các quyền service đã chốt không được mô hình hóa thành OAuth2 scopes vì dự án chưa chọn OAuth2 flow hoặc claim quyền.

Cấu hình Service JWT **PENDING DECISION Q03/Q11a**: thuật toán, issuer/audience/subject, TTL, tập claim/cách biểu diễn quyền và khóa, cùng bên phát token Gateway. OpenAPI không điền các giá trị mẫu thành quyết định. [Sổ câu hỏi](../open-decisions.md) tiếp tục duy trì các mục mở; không cần chốt chúng chỉ để hiển thị hợp đồng.

## Bảo trì và kiểm tra

YAML là nơi duy trì đặc tả có thể đọc bằng máy; [hợp đồng hiện hành](current-contract.md), [API phiên](sessions.md), [schema JSON](json-schema.md) và [HTTP/lỗi](http-errors.md) duy trì giải thích và nguồn quyết định. Cập nhật đồng thời các phần bị ảnh hưởng, giữ đúng 20 operation và không thêm API role. Giữ nguyên lịch sử xác nhận trong sổ quyết định.

Chạy riêng kiểm thử hạ tầng tài liệu từ `backend/`:

```powershell
.\mvnw.cmd -Dtest=ApiDocumentationTests test
```

[ApiDocumentationTests](../../backend/src/test/java/com/library/userservice/documentation/ApiDocumentationTests.java) dùng HTTP trên cổng ngẫu nhiên, nạp cấu hình Swagger UI thực tế, bỏ import `.env` và loại auto-configuration datasource/JPA/Flyway. Kiểm tra JSON/YAML, đủ 20 operation và security tương ứng, introspection, UI/tài nguyên/cấu hình tắt thực thi, cùng việc các đường dẫn ngoài tài liệu yêu cầu xác thực. Đây là kiểm thử hạ tầng tài liệu, không xác nhận nghiệp vụ hoặc migration. Không chạy toàn bộ `test` nếu chưa chuẩn bị database kiểm thử theo hướng dẫn dự án.

Trong lần xây dựng 2026-11-01 đã build bằng Java 25/Maven Wrapper và kiểm tra cấu trúc/tham chiếu đặc tả bằng `openapi-spec-validator`. Công cụ Python phục vụ kiểm tra nằm trong `backend/target/`, không thêm vào dependency ứng dụng. Maven Wrapper có sửa kiểm tra `Target` rỗng của thư mục `.m2` để chạy được trên thư mục thường của Windows.

Phiên bản `springdoc-openapi-starter-webmvc-ui` được ghim tại `springdoc.version` trong [pom.xml](../../backend/pom.xml). Tham khảo [ma trận tương thích springdoc](https://springdoc.org/faq.html), [hướng dẫn starter](https://springdoc.org/getting-started.html) và [các thuộc tính cấu hình](https://springdoc.org/properties.html).
