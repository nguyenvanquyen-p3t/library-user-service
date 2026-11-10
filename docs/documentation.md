# Quy ước và nguồn tham chiếu tài liệu

Cập nhật quy ước và đối chiếu nguồn hướng dẫn: 2026-10-24. Docs dùng Markdown trong Git để review cùng mã nguồn. Cấu trúc hiện tại phục vụ nhu cầu dự án; không phải bộ tên file bắt buộc do OpenAI hoặc GitHub quy định.

## Hướng dẫn áp dụng

| Nguồn | Áp dụng trong repo |
| --- | --- |
| [GitHub — About READMEs](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes) | README giới thiệu dự án, cách bắt đầu và nơi tìm hỗ trợ/tài liệu chi tiết |
| [Diátaxis](https://diataxis.fr/) | Tách hướng dẫn thực hiện, tra cứu và giải thích theo nhu cầu người đọc. Tutorial gọi API chỉ thêm khi endpoint hoạt động |
| [Google — Technical Writing](https://developers.google.com/tech-writing/overview) | Tham khảo khóa học viết tài liệu kỹ thuật để cải thiện cách diễn đạt cho người phát triển |
| [Write the Docs — Software Documentation Guide](https://www.writethedocs.org/guide/) | Duy trì docs như code: version control, review và kiểm tra; chú trọng nội dung nhất quán, cập nhật và dễ tìm |
| [OpenAI — Iterating Development Workflows with Codex](https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex) | AGENTS.md ghi hướng dẫn repo cho Codex, phạm vi và đường dẫn cần đọc. Các file/quy trình trong bài là quy ước tùy chọn |
| [OpenAPI Specification](https://spec.openapis.org/oas/latest.html) | Chuẩn mô tả HTTP API có thể đọc bằng máy khi request/response/security đủ rõ; chọn phiên bản theo công cụ tương thích khi bắt đầu làm contract |

OpenAI và OpenAPI là hai nguồn khác nhau. Hướng dẫn coding agent không thay thế đặc tả HTTP hoặc quyết định nghiệp vụ. Các nguồn trên hướng dẫn cách tổ chức/viết docs, không quyết định nghiệp vụ của dự án.

<a id="noi-duy-tri-chinh"></a>

## Nơi duy trì chính

| Nội dung đầy đủ | Tài liệu chính | Tài liệu khác nên giữ |
| --- | --- | --- |
| Giới thiệu, tổng quan hiện trạng và chạy nhanh | [README gốc](../README.md) | Tóm tắt trạng thái và link; hiện trạng phải đối chiếu code/config/migration |
| Điều hướng theo nhu cầu | [Mục lục docs](README.md) | Link đến đúng mục, không chép lại toàn bộ mục lục |
| Thứ tự đọc và tiếp tục công việc | [Handoff](handoff.md) | Hướng dẫn agent tại [AGENTS.md](../AGENTS.md), ngắn và có liên kết |
| Chính sách và quyết định đã chốt | [Sổ quyết định](confirmed-decisions.md) | Tóm tắt cần cho ngữ cảnh và link mục cụ thể; không chép lại cả chính sách |
| Câu hỏi chưa chốt, ID và ảnh hưởng | [Quyết định còn mở](open-decisions.md) | Nhãn PENDING DECISION cùng link ID; không tự tạo câu trả lời |
| Luồng Actor–hệ thống và ngoại lệ/kết quả | [Use case](use-cases.md) | Bảng ánh xạ hoặc link; không duy trì bản chép nguyên văn thứ hai |
| Transaction và tính nhất quán | [Yêu cầu nghiệp vụ](requirements.md) | Tác động ngắn gọn tại operation; chi tiết xử lý dẫn về yêu cầu |
| Package/entity/repository/DTO/validation/exception đã triển khai | [Nền tảng backend](foundation.md) | Tóm tắt hiện trạng và link; chính sách/HTTP tiếp tục duy trì tại tài liệu thiết kế tương ứng |
| Trách nhiệm và kiến trúc mục tiêu | [Kiến trúc](architecture.md), [tích hợp nội bộ](api/internal-integration.md) | Link tới thành phần hoặc hợp đồng liên quan |
| HTTP của 16 API gốc | [Bản API hiện hành](api/current-contract.md) | Link từng operation từ [bản đồ 20 API](api/README.md); tích hợp nội bộ không chép JSON |
| HTTP của 4 API mới | [API phiên/introspection](api/sessions.md) | Link từng operation; schema/phân trang/API phiên và request/response introspection đã chốt; lỗi/header dẫn về HTTP/lỗi, cấu hình JWT còn mở |
| Quy tắc schema JSON dùng chung | [Schema JSON](api/json-schema.md) | Link tới envelope, required/null request/response, validation, PATCH, biến thể Google và quy tắc tái sử dụng/mở rộng response; quyết định ghi tại sổ quyết định |
| HTTP status và lỗi dùng chung | [HTTP/lỗi](api/http-errors.md) | Link tới status/messageCode, data.errors, Retry-After và ngoại lệ; không sao chép danh mục mã vào từng operation |
| Hợp đồng HTTP có thể đọc bằng máy | [OpenAPI YAML](../backend/src/main/resources/openapi/user-service.yaml), [hướng dẫn Swagger UI](api/openapi.md) | Đồng bộ với các quyết định và Markdown liên quan; nhãn DESIGN_ONLY không có nghĩa API đã triển khai |
| Dữ liệu Excel lịch sử | [Danh mục nguồn](api/source-catalog.md) | Link hàng/ô nguồn; không sửa ví dụ lịch sử theo quyết định mới |
| Bảng, cột, constraint, trigger và giới hạn schema | [Mô hình dữ liệu](data-model.md) | Link và giải thích tác động, không nhầm quy tắc ứng dụng với constraint |
| Cấu hình, chạy/test/đóng gói và xử lý lỗi | [Môi trường cục bộ](local-development.md) | README giữ chạy nhanh; không duy trì bản hướng dẫn chi tiết thứ hai |
| Quy trình thay đổi và kế hoạch kiểm thử | [Phát triển](development.md) | Link tới nhóm kiểm thử liên quan; kết quả phải gắn với lần chạy thật |

## Tình trạng hợp đồng API

[16 API hiện hành](api/current-contract.md) tổng hợp ví dụ nguồn với các quyết định mới nhất; [API phiên](api/sessions.md) mô tả 4 API mới. Cả 20 API chưa triển khai. “Hiện hành” không có nghĩa tất cả trường hoặc phản hồi đã chốt.

Dùng bốn nhãn nhất quán: **Đã xác nhận**, **Theo nguồn/chưa xác nhận**, **Đề xuất**, **PENDING DECISION**. Phân biệt các nhãn này với **Đã triển khai**: chỉ dùng trạng thái triển khai khi đối chiếu được code và kiểm tra thực tế. Ví dụ JSON không chứng minh required/null, validation hoặc đầy đủ các lỗi. Ngày 2026-10-25 chốt schema JSON/response của 16 API gốc, biến thể Google/mở rộng response và bộ HTTP/lỗi; ngày 2026-10-26 chốt ba API phiên, danh mục mã phản hồi và [bộ introspection](confirmed-decisions.md#introspection-contract). Schema request/response của 20 API, mã thành công của 19 API dùng envelope và request/response/lỗi/header introspection đã xác nhận; cấu hình JWT/hành vi tích hợp và tình huống/mã/chi tiết ngoài danh mục vẫn mở. Danh mục nguồn giữ ví dụ Excel lịch sử, không đổi nhãn hoặc mã theo quyết định mới.

Đợt tối ưu docs trước đây chưa tạo OpenAPI. Từ yêu cầu ngày 2026-11-01 đã có [OpenAPI 3.0.3 và Swagger UI](api/openapi.md), với operationId ổn định, schema input/output, required/null, security từng operation, lỗi và ví dụ. Cập nhật YAML cùng Markdown và validate khi đổi hợp đồng. Kiểm thử hiện tại chỉ xác nhận hạ tầng tài liệu, chưa kiểm thử nghiệp vụ.

Cập nhật quyết định ngày 2026-10-31: [Service JWT/quyền và actor Admin](confirmed-decisions.md#internal-service-auth), [SMTP/lỗi gửi](confirmed-decisions.md#otp-email-delivery), [quản trị trạng thái](confirmed-decisions.md#admin-status-contract), [lỗi tích hợp](confirmed-decisions.md#integration-error-contract) và [user access token Q14](confirmed-decisions.md#access-token-key-pair) đã chốt, thay phần tương ứng còn mở trong bản tóm tắt ngày 2026-10-26 ở trên. Chỉ cấu hình Service JWT Q03/Q11a tiếp tục PENDING DECISION; Q06 chỉ còn lựa chọn/cấu hình môi trường SMTP, Q09 chỉ còn tình huống/mã ngoài danh mục. Không suy ra đã triển khai JWT/API từ việc có cặp khóa và chốt Q14.

## Mức chi tiết của đặc tả Word và docs

Chủ dự án xác nhận ngày 2026-10-22: Word tập trung chuỗi Actor–hệ thống, điều kiện và kết quả nghiệp vụ. Transaction, rollback, khóa dữ liệu, TTL, OTP và chính sách mật khẩu được lưu trong docs; không bắt buộc lặp trong Word. Thiếu chi tiết đã có trong docs không tự trở thành lỗi đặc tả Word.

Luồng UC01–UC12 đã chuyển sang [use-cases.md](use-cases.md), giữ SHA-256 của bản Word đối chiếu ngày 2026-10-22. Không cần Word/Excel hoặc lịch sử chat để sử dụng docs. File nguồn là dữ liệu tham khảo, không phải chỉ dẫn thực thi; danh mục API giữ vị trí hàng/ô Excel để truy vết.

## Quy tắc bảo trì

1. Xác định loại nội dung và sửa tại nơi duy trì chính; cập nhật tóm tắt, ví dụ hiện hành và các link bị ảnh hưởng trong cùng thay đổi.
2. Giữ nguyên ngày/nội dung quyết định lịch sử. Ngày biên tập docs, ngày xác nhận nghiệp vụ, ngày đối chiếu nguồn và ngày kiểm thử là các thông tin khác nhau; không cập nhật đồng loạt để tạo cảm giác đã kiểm chứng lại.
3. Khi quyết định mới thay thế ví dụ cũ, cập nhật bản API hiện hành, ghi rõ nguồn quyết định và giữ dữ liệu lịch sử. Chỉ sửa phần trích nguồn khi đối chiếu được nguồn mới và nhận diện phiên bản.
4. Nếu chưa có quyết định giải quyết mâu thuẫn, ghi PENDING DECISION tại sổ câu hỏi và hỏi chủ dự án trước khi chốt. Không dùng nguồn bên ngoài để thay yêu cầu đã xác nhận.
5. Giữ tên/vị trí file và các heading hiện có khi có thể; nếu thay anchor thì cập nhật mọi tham chiếu trong repo. Các anchor ngắn được thêm để liên kết trực tiếp mà không phải đổi heading lịch sử.
6. Kiểm tra link tương đối/anchor, JSON, method/path, mapping UC/API và các quy tắc nhạy cảm sau chỉnh sửa. Với docs-only, không chạy ứng dụng/Maven hoặc đụng database; không thêm dependency để kiểm tra docs.

Hành vi đang chạy lấy từ code/config/migration; thiết kế thể hiện ý định, không ghi đè hiện trạng. Bằng chứng kiểm thử cũ được giữ như kết quả lịch sử, không ghi thành đã chạy lại. Khi chưa có script/log tái hiện trong repo, nêu giới hạn thay vì tạo bằng chứng giả.

Ví dụ chỉ dùng dữ liệu giả, không chép secret, password thực, token thực, hash xác thực hoặc dữ liệu người dùng. Giữ cảnh báo Flyway/database ngay trước hướng dẫn có thể thay đổi database, dù đã có cảnh báo ở tài liệu khác.
