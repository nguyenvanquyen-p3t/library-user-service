# Quy ước và nguồn tham chiếu tài liệu

Ngày tra cứu: 2026-10-17. Bộ docs dùng Markdown trong Git để review cùng mã nguồn. Không có tuyên bố rằng toàn bộ cấu trúc này là một tiêu chuẩn bắt buộc do OpenAI ban hành.

## Hướng dẫn áp dụng

- [OpenAI — Iterating Development Workflows with Codex](https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex): AGENTS.md là quy ước tùy chọn được Codex nhận biết. Repo dùng file ngắn để ghi phạm vi, lệnh kiểm tra và đường dẫn docs; không sao chép toàn bộ tài liệu vào context agent.
- [Diátaxis](https://diataxis.fr/): phân biệt hướng dẫn thực hành, tài liệu tra cứu, giải thích và tutorial. Repo hiện tập trung vào chạy dự án, tra cứu API/schema và giải thích kiến trúc. Tutorial gọi API sẽ bổ sung khi endpoint hoạt động.
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html): dùng cho hợp đồng HTTP có thể kiểm tra bằng máy khi request/response/security đã được chốt. Trang latest trả về v3.2.1 tại lần tra cứu; cần kiểm tra tương thích công cụ trước khi chọn phiên bản cho repo.

OpenAI và OpenAPI là hai nguồn khác nhau. Hướng dẫn coding agent không thay thế đặc tả HTTP hoặc quyết định nghiệp vụ của nhóm.

## Tình trạng hợp đồng API

Danh mục nguồn giữ 16 API Excel; [thiết kế phiên](api/sessions.md) thêm 4 API, tổng 20. Hiện chỉ migration và docs, chưa code API. Quyết định mới nằm trong [quyết định đã xác nhận](confirmed-decisions.md); Gateway dùng Service JWT, refresh token vẫn trong JSON cho Web/Android. Chưa tạo OpenAPI hoàn chỉnh vì chi tiết Service JWT, validation và nhiều response lỗi còn PENDING DECISION. API quản lý role ngoài phạm vi.

Sau khi xác nhận, bổ sung hợp đồng OpenAPI với operationId ổn định, schema input/output, trường required/null, security từng operation, các phản hồi lỗi và ví dụ; validate bằng công cụ hỗ trợ phiên bản đã chọn. Giữ contract test và tài liệu cùng cập nhật khi API đổi.

## Mức chi tiết của đặc tả Word và docs

Chủ dự án xác nhận ngày 2026-10-22: `DacTaUseCase.docx` chỉ mô tả chuỗi tương tác giữa Actor và hệ thống, điều kiện và kết quả nghiệp vụ. Chi tiết transaction, rollback, khóa dữ liệu, TTL, giới hạn OTP và chính sách mật khẩu được lưu trong docs; không yêu cầu lặp lại trong Word.

Khi rà soát Word, không coi thiếu chi tiết kỹ thuật đã có trong docs là lỗi đặc tả. Chỉ yêu cầu làm rõ khi luồng tương tác hoặc kết quả nghiệp vụ mâu thuẫn, thiếu hoặc chưa được chốt. Ranh giới transaction được ghi tại [yêu cầu nghiệp vụ](requirements.md); quy tắc này không thay đổi yêu cầu nhất quán dữ liệu khi triển khai.

## Quy tắc bảo trì

Bản bàn giao ngày 2026-10-22 đã chuyển nội dung nghiệp vụ UC01–UC12 từ Word sang [use-cases.md](use-cases.md): Actor, điều kiện, luồng chính, ngoại lệ và kết quả. Hash bản nguồn được ghi tại đó để nhận biết phiên bản, không yêu cầu giữ Word trong repository. Không lưu bản chép nguyên văn thứ hai; cập nhật luồng tại use-cases, chính sách tại confirmed-decisions/requirements, hợp đồng HTTP tại api và câu hỏi tại open-decisions. [Handoff](handoff.md) là điểm vào cho chat mới; [bản đồ API](api/README.md) liên kết 12 UC với 20 operation.

Khi sửa yêu cầu, cập nhật các tài liệu liên quan trong cùng thay đổi và ghi rõ quyết định nào thay thế đề xuất cũ. Nếu phát hiện mâu thuẫn chưa có quyết định của chủ dự án, ghi PENDING DECISION; không âm thầm chọn một nguồn. Chỉ đánh dấu đã triển khai khi đã đối chiếu code và kiểm tra thực tế; kết quả test lịch sử không đại diện cho lần làm việc mới.

Mỗi tài liệu ghi rõ nguồn và trạng thái. Hành vi đang chạy lấy từ code/config/migration; tài liệu thiết kế thể hiện ý định, không ghi đè hiện trạng. Yêu cầu mới từ chủ dự án có thể thay đổi thiết kế cũ nhưng cần ghi rõ khác biệt. Câu hỏi chưa trả lời nằm trong open-decisions, không được lấp bằng giả định nghiệp vụ.

Ví dụ chỉ dùng dữ liệu giả, không chép secret, password thực, token thực hoặc dữ liệu người dùng. Nếu source thay đổi, cập nhật danh mục và đối chiếu lại các ô nguồn; không chỉnh ví dụ để âm thầm thay đổi yêu cầu.
