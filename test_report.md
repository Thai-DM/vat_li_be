# Báo Cáo Tổng Kết Nghiệm Thu Backend Toàn Diện (Sprint 8)
## Kiến Trúc Hệ Thống, Toàn Bộ Phân Hệ, Công Thức Chấm Điểm & Kiểm Thử Đồng Thời

- **Hệ thống**: Học phần Vật lý 1 (Physics 1 LMS & Virtual Lab)
- **Kiến trúc**: Clean Micro-Enterprise RESTful API — Spring Boot 3.5.13 + PostgreSQL Neon Cloud + JPA Hibernate
- **Phạm vi triển khai**: 100% Backend Core System
- **Kết quả nghiệm thu**: **187/187 Tests PASS (100% BUILD SUCCESS)** trên toàn bộ **22 Phân hệ & Test Suites**

---

## I. Tổng Quan Kiến Trúc & 10 Phân Hệ Nghiệp Vụ Đã Xây Dựng

Hệ thống được thiết kế theo kiến trúc chuẩn Clean Micro-Enterprise Layered Architecture trên nền tảng Spring Boot 3.5.x, Java 17/24 và cơ sở dữ liệu quan hệ PostgreSQL (triển khai trên Neon Serverless Cloud). Toàn bộ 10 phân hệ đã được xây dựng và kiểm thử hoàn chỉnh:

| STT | Phân Hệ Nghiệp Vụ | Vai Trò & Chức Năng Cốt Lõi Đã Xây Dựng | Endpoints / Tiêu Chuẩn Kỹ Thuật |
|:---:|---|---|---|
| 01 | **Quản Trị Định Danh & Xác Thực (IAM & RBAC)** | Quản lý vòng đời người dùng, phân quyền RBAC 4 vai trò (`ADMIN`, `INSTRUCTOR`, `TA`, `STUDENT`). Cấp phát và xoay vòng JWT Access Token (15 phút) và Refresh Token (7 ngày). Quên mật khẩu qua email token URL-safe 15 phút, One-Time-Use. Bộ lọc RateLimitFilter chống dò mật khẩu. | `/api/v1/users/**`<br>BCrypt hashing, JWT Bearer, RateLimit 10 req/min |
| 02 | **Tổ Chức Đào Tạo & Quản Lý Lớp Học** | Quản lý học kỳ (`Semester`) với cơ chế kích hoạt học kỳ hiện tại Atomic Update. Quản lý lớp học môn Vật lý 1, phân công Giảng viên chính, bổ nhiệm Trợ giảng (`ClassStaff`), ghi danh sinh viên (`Enrollment`) và kiểm soát sĩ số tối đa. | `/api/v1/classes/**`<br>`/api/v1/semesters/**`<br>Kiểm soát sĩ số & Phân quyền lớp |
| 03 | **Khung Chương Trình & Học Liệu Vật Lý 1** | Cây cấu trúc chương mục kiến thức Vật lý 1 (Động học, Động lực học, Công - Năng lượng, Va chạm, Chuyển động quay, Dao động cơ) có chỉ số `order_index`. Upload tài liệu bài giảng an toàn, kiểm tra Magic bytes (PDF, DOCX, PPTX), phê duyệt học liệu. | `/api/v1/topics/**`<br>`/api/v1/materials/**`<br>Magic bytes validation, Static CDN |
| 04 | **Ngân Hàng Câu Hỏi & Bóc Tách Đề Thi** | Quản lý kho câu hỏi trắc nghiệm MCQ (đơn lựa chọn, đa lựa chọn), phân loại độ khó theo thang đo Bloom (Nhận biết, Thông hiểu, Vận dụng, Vận dụng cao). Module bóc tách tự động đề thi từ file PDF bằng Apache PDFBox. | `/api/v1/questions/**`<br>Bloom taxonomy, Regex parser PDFBox |
| 05 | **Khảo Thí & Thi Trực Tuyến (Exam Engine)** | Tổ chức các kỳ thi: Luyện tập (`Practice`), Đánh giá nhanh (`Quiz`), Giữa kỳ (`Midterm`), Cuối kỳ (`Final`). Phân phối đề thi, lưu câu trả lời thời gian thực, tự động chấm điểm khi nộp bài, phạt nộp muộn, khóa bi quan chống submit trùng lặp. | `/api/v1/exams/**`<br>Pessimistic Write Lock, Auto-grading |
| 06 | **Thí Nghiệm Ảo 3D (3D Virtual Lab)** | Tích hợp 04 bài thí nghiệm ảo 3D mô phỏng hiện tượng Vật lý 1. Giao bài thực hành theo lớp, sinh viên nộp báo cáo số liệu thực nghiệm & đồ thị. Giảng viên chấm điểm theo Rubric 4 tiêu chí chuẩn hóa thang 10. Chốt điểm bất biến. | `/api/v1/experiments/**`<br>Seed 4 bài Lab, Rubric JSONB, Khóa điểm bất biến |
| 07 | **Trợ Giảng Thông Minh Socratic AI** | Mô hình AI hướng dẫn học tập theo phương pháp gợi mở Socratic, không làm bài hộ sinh viên. Cơ chế từ chối (`Refusal`) đối với câu hỏi gian lận hoặc lạc đề. Tự động trích dẫn tài liệu giáo trình (`Citations`). Cô lập quyền truy cập IDOR. | `/api/v1/ai/**`<br>Socratic dialogue, Refusal guardrails, Citations |
| 08 | **Phân Tích Khảo Thí Cổ Điển (CTT Analytics)** | Bộ chỉ số đánh giá học thuật CTT: Độ khó $p$-value, Độ phân biệt Kelley 27% ($DI$), Tỷ lệ sai sót theo chủ đề, Phân tích ngộ nhận qua Top 5 phương án nhiễu, Thống kê lỗ hổng AI Gaps. Tự động tổng hợp dữ liệu định kỳ bằng Cron Job Idempotent. | `/api/v1/analytics/**`<br>Kelley 27% DI, Topic Error Rate, Idempotent Cron 01:00 AM |
| 09 | **Hồ Sơ Minh Chứng & Bảng Điều Khiển** | Tự động tập hợp và lưu vết toàn bộ minh chứng học tập của sinh viên (Lịch sử làm bài thi, nhật ký thực hành ảo, tương tác Socratic AI). Dashboard cá nhân hóa và dashboard lớp học tổng hợp tiến độ thời gian thực. | `/api/v1/evidence/**`<br>`/api/v1/dashboard/**`<br>Snapshot JSONB, Audit trace |
| 10 | **Giám Sát Vận Hành & Nhật Ký Kiểm Toán (AOP)** | Bộ ghi nhật ký hoạt động người dùng bất đồng bộ (`@Async` ActivityLog) không ảnh hưởng độ trễ API. Ghi vết kiểm toán (`AuditLog`) lưu toàn bộ lịch sử thay đổi nhạy cảm (điểm số, trạng thái lớp, học kỳ). Endpoint Actuator Health & Metrics an toàn. | `/actuator/health`, `/actuator/metrics`<br>Spring AOP, Async Logging, JSONB Old/New |

---

## II. Toàn Bộ Thuật Toán, Quy Trình & Công Thức Chấm Điểm

### 1. Thuật Toán Chấm Điểm Thi Trắc Nghiệm (Exam Grading Engine)

- **Quy tắc khớp đáp án**:
  - Gọi $S_{\text{student}}$ là tập hợp các phương án do thí sinh lựa chọn cho câu hỏi $j$.
  - Gọi $S_{\text{correct}}$ là tập hợp các phương án đúng được định nghĩa trong `QuestionOption` (`is_correct = true`).
  - Điều kiện chấm đúng:
    $$\text{IsCorrect}_j = \begin{cases} \text{true} & \text{nếu } S_{\text{student}} = S_{\text{correct}} \land S_{\text{correct}} \neq \emptyset \\ \text{false} & \text{ngược lại} \end{cases}$$

- **Điểm số từng câu hỏi có trọng số**:
  $$Score_j = \begin{cases} w_j & \text{nếu } \text{IsCorrect}_j = \text{true} \\ 0.0 & \text{nếu } \text{IsCorrect}_j = \text{false} \end{cases}$$
  *(Trong đó $w_j$ là trọng số điểm `score_weight` của câu hỏi trong `ExamQuestion`, mặc định là 1.0).*

- **Tổng điểm thô (Raw Score)**:
  $$TotalScore = \sum_{j=1}^{Q} Score_j \quad (Q \text{ là tổng số câu hỏi trong bài thi})$$

- **Quy đổi thang điểm 10 chuẩn mực (Scaled Score on 10-Point Scale)**:
  $$Score_{10} = \left(\frac{\sum_{j=1}^{Q} Score_j}{\sum_{j=1}^{Q} w_j}\right) \times 10.0$$
  *Điểm số được làm tròn chính xác 2 chữ số thập phân bằng `BigDecimal.setScale(2, RoundingMode.HALF_UP)`.*

- **Khấu trừ điểm nộp muộn (Late Penalty Policy)**:
  Nếu thời điểm nộp bài $t_{\text{submit}} > t_{\text{end\_time}}$ và bài thi cho phép nộp muộn với tỷ lệ khấu trừ $P\%$ (`late_penalty_percent`):
  $$Score_{\text{final}} = \max\left(0.0, Score_{10} \times \left(1 - \frac{P}{100}\right)\right)$$

- **Quản lý số lượt làm bài (Multi-attempt vs Strict Single Attempt)**:
  - **Kỳ thi Luyện tập (`PRACTICE`)**: Sinh viên được làm nhiều lượt (tối đa $K$ lượt quy định tại `exam.max_attempts` trong `system_settings`, mặc định $K = 10$). Điểm tổng kết lấy theo điểm cao nhất:
    $$Score_{\text{practice}} = \max\left(Score^{(1)}, Score^{(2)}, \dots, Score^{(k)}\right)$$
    *Chống mở lượt song song*: Khi đang có 1 lượt trạng thái `IN_PROGRESS`, hệ thống từ chối mở lượt mới (HTTP 409 Conflict).
  - **Kỳ thi Chính thức (`MIDTERM`, `FINAL`, `QUIZ`)**: Khóa cứng duy nhất 1 lượt (`attempt_number = 1`). Mọi nỗ lực mở lượt thi thứ 2 bị từ chối triệt để bằng HTTP 409 Conflict (*"Attempt already exists for this exam"*).

- **Bảo vệ Concurrency bằng Khóa Bi Quan (Pessimistic Write Lock)**:
  Thực thi câu lệnh SQL: `SELECT * FROM exam_attempts WHERE attempt_id = ? FOR UPDATE`. Luồng đầu tiên chiếm lock chuyển trạng thái sang `GRADED`; mọi luồng gửi đồng thời hoặc click lặp (Double click) đều bị từ chối bằng HTTP 400 Bad Request (*"Attempt is already submitted"*), triệt tiêu hoàn toàn lỗi Race Condition.

---

### 2. Tiêu Chí Rubric & Chấm Điểm 04 Bài Thí Nghiệm Ảo 3D (Virtual Lab Rubrics)

Mỗi bài thí nghiệm ảo 3D được đánh giá theo khung Rubric chuẩn mực thang điểm 10.0:
$$Score_{\text{lab}} = \sum_{i=1}^{M} CriteriaScore_i \quad \text{với} \quad \sum_{i=1}^{M} MaxScore_i = 10.0$$

| Bài Lab Chuẩn | Tiêu Chí Đánh Giá (Rubric Criteria) | Điểm Tối Đa | Ý Nghĩa Vật Lý & Đo Lường |
|---|---|:---:|---|
| **Bài 1: Khảo sát rơi tự do & xác định gia tốc $g$**<br>($s = \frac{1}{2}gt^2$) | **TC1.** Thao tác cài đặt & thu thập số liệu | 3.0 điểm | Bố trí cổng quang điện, đo khoảng cách $s$, đo thời gian rơi $t$ |
| | **TC2.** Xử lý số liệu, vẽ đồ thị $s = f(t^2)$ | 3.0 điểm | Vẽ đồ thị tuyến tính thực nghiệm, xác định hệ số góc |
| | **TC3.** Xác định gia tốc $g$ và tính sai số | 2.0 điểm | Tính $g = 2 \times \text{slope}$, tính sai số tuyệt đối $\Delta g$ và sai số tỉ đối $\varepsilon$ |
| | **TC4.** Phân tích nguyên nhân sai số | 2.0 điểm | Đánh giá ảnh hưởng của sức cản không khí, độ trễ cảm biến quang |
| **Bài 2: Con lắc đơn xác định gia tốc $g$**<br>($T^2 = \frac{4\pi^2}{g}L$) | **TC1.** Thiết lập chiều dài $L$ & kích thích góc nhỏ | 2.5 điểm | Đo chiều dài dây treo $L$, đảm bảo góc lệch ban đầu $\alpha_0 \le 10^\circ$ |
| | **TC2.** Đo chu kỳ 20 dao động toàn phần | 2.5 điểm | Đo thời gian $20T$, tính chu kỳ trung bình và sai số đồng hồ |
| | **TC3.** Hồi quy tuyến tính $T^2$ theo $L$, tính $g$ | 3.0 điểm | Hồi quy tuyến tính đường thẳng, xác định gia tốc trọng trường $g$ |
| | **TC4.** Phân tích điều kiện dao động điều hòa | 2.0 điểm | Biện luận điều kiện góc nhỏ và ảnh hưởng khối lượng dây treo |
| **Bài 3: Mô-men quán tính đĩa tròn**<br>($M = I\beta$, $I_{\text{lt}} = \frac{1}{2}MR^2$) | **TC1.** Căn chỉnh hệ đĩa quay và quả nặng | 2.5 điểm | Căn chỉnh cân bằng đĩa quay, quấn dây qua ròng rọc không trượt |
| | **TC2.** Thu thập gia tốc góc $\beta$ theo tải trọng | 2.5 điểm | Đo thời gian quay, tính vận tốc góc $\omega$ và gia tốc góc $\beta$ |
| | **TC3.** Xác định mô-men quán tính $I$ thực nghiệm | 3.0 điểm | Tính $I_{\text{tn}} = M_{\text{lực}} / \beta$, đối chiếu với mô-men lý thuyết $I_{\text{lt}}$ |
| | **TC4.** Đánh giá ảnh hưởng của ma sát ổ trục | 2.0 điểm | Xác định mô-men cản ma sát và tính toán độ tiêu hao cơ năng |
| **Bài 4: Va chạm đệm không khí**<br>(Bảo toàn động lượng & động năng) | **TC1.** Cân bằng băng đệm khí, triệt tiêu ma sát | 2.5 điểm | Cân bằng mức ni-vô, điều chỉnh áp suất luồng khí nâng xe trượt |
| | **TC2.** Đo vận tốc xe trước & sau va chạm | 2.5 điểm | Ghi nhận tín hiệu cổng quang, xác định vận tốc $v_1, v_2, v_1', v_2'$ |
| | **TC3.** Tính độ bảo toàn động lượng & động năng | 3.0 điểm | Kiểm chứng: $\Delta p = \vert p_{\text{sau}} - p_{\text{trước}}\vert$, phân tích va chạm mềm/đàn hồi |
| | **TC4.** Kết luận về tính chất hệ kín | 2.0 điểm | Biện luận điều kiện hệ cô lập và độ suy giảm động năng do biến dạng |

- **Cơ chế Khóa Điểm Bất Biến (Rubric Confirmation Workflow)**:
  1. Trợ giảng/Giảng viên chấm điểm thành phần qua `POST /submissions/{id}/scores` (lưu bảng `experiment_scores`).
  2. Giảng viên phụ trách thực hiện xác nhận chính thức qua `POST /submissions/{id}/confirmation` (chuyển `status = CONFIRMED` và ghi bản ghi độc quyền vào bảng `experiment_confirmations`).
  3. Sau khi `CONFIRMED`, mọi thao tác sửa điểm, chấm lại đều bị hệ thống chặn triệt để bằng HTTP 400 Bad Request (*"Bài nộp đã được xác nhận kết quả trước đó, không thể sửa đổi"*).

---

### 3. Công Thức Đánh Giá Học Thuật Theo Lý Thuyết Khảo Thí Cổ Điển (CTT Analytics)

1. **Độ khó của câu hỏi trắc nghiệm ($p$-value / Correct Rate)**:
   $$p = \frac{R}{N}$$
   *(Trong đó: $R$ là số thí sinh trả lời đúng câu hỏi; $N$ là tổng số lượt làm bài của câu hỏi đó `times_used`).*
   - $p < 0.25$: Câu hỏi rất khó.
   - $0.25 \le p \le 0.75$: Câu hỏi có độ khó chuẩn mực tối ưu (Đạt yêu cầu sư phạm).
   - $p > 0.75$: Câu hỏi rất dễ.

2. **Độ phân biệt câu hỏi theo Quy tắc 27% của Kelley (Discrimination Index - $DI$)**:
   Trong mỗi đợt thi (`Exam`), sắp xếp toàn bộ danh sách thí sinh theo tổng điểm bài thi (`TotalScore`) giảm dần. Chia thành 2 nhóm đối sánh với kích thước mẫu: $k = \max(1, \text{round}(N_{\text{exam}} \times 0.27))$:
   - **Nhóm Điểm Cao (Top Group)**: Gồm $k$ thí sinh có tổng điểm cao nhất.
   - **Nhóm Điểm Thấp (Bottom Group)**: Gồm $k$ thí sinh có tổng điểm thấp nhất.
   
   Công thức tính độ phân biệt:
   $$DI = p_{\text{top}} - p_{\text{bottom}} = \frac{R_{\text{top}}}{k} - \frac{R_{\text{bottom}}}{k}$$
   *(Giá trị $DI$ luôn nằm trong đoạn $[-1.0, 1.0]$. Nếu câu hỏi xuất hiện trên nhiều đợt thi, $DI$ là trung bình cộng).*

   Phân loại nhãn chất lượng (`Quality Label`) tự động trong hệ thống:
   - $DI \ge 0.35$: `EXCELLENT` (Khả năng phân loại sinh viên xuất sắc).
   - $0.20 \le DI < 0.35$: `GOOD` (Khả năng phân loại tốt, sử dụng bình thường).
   - $0.10 \le DI < 0.20$: `FAIR` (Khả năng phân loại yếu, cần xem xét cải tiến phương án nhiễu).
   - $DI < 0.10$: `POOR` (Kém, không phân loại được hoặc phân loại ngược, cần loại khỏi ngân hàng đề).

3. **Điểm trung bình và Tỷ lệ sai sót theo Chủ đề (Topic Difficulty & Error Rate)**:
   $$\text{AvgScore}_{\text{topic}} = \left(\frac{\sum R_{\text{topic}}}{\sum N_{\text{topic}}}\right) \times 100\%$$
   $$\text{ErrorRate}_{\text{topic}} = 1.0 - \frac{\text{AvgScore}_{\text{topic}}}{100.0} = \frac{\sum W_{\text{topic}}}{\sum N_{\text{topic}}}$$
   *(Trong đó $W_{\text{topic}}$ là tổng số câu trả lời sai thuộc chủ đề đó).*

4. **Phân tích Ngộ nhận qua Phương án Nhiễu (Distractor Misconception Analysis)**:
   Hệ thống đếm tần suất sinh viên chọn từng phương án sai (A, B, C, D) và trích xuất Top 5 phương án sai phổ biến nhất (`common_wrong_options_json`):
   ```json
   [
     {"optionId": "d14f2e51-...", "count": 45},
     {"optionId": "e25a3c62-...", "count": 28}
   ]
   ```
   Giúp giảng viên nắm bắt chính xác các "bẫy tư duy" và ngộ nhận khái niệm vật lý thường gặp để bổ trợ kịp thời.

5. **Phân tích Lỗ hổng Kiến thức qua Trợ giảng AI (AI Topic Gaps)**:
   Thống kê số lần AI từ chối giải hộ (`AiRefusal`) hoặc các câu hỏi sinh viên truy vấn liên tục theo từng chủ đề. Giúp chỉ ra những chương mục kiến thức sinh viên còn yếu và hay tìm cách gian lận hoặc thiếu tài liệu tự học.

6. **Tác Vụ Tổng Hợp Định Kỳ Tự Động & Bất Biến (Idempotent Cron Job)**:
   Class `AnalyticsCronJob` chạy tự động vào 01:00 AM mỗi ngày (`@Scheduled(cron = "0 0 1 * * *")`). Cơ chế Idempotent: Xóa sạch dữ liệu thống kê cũ của cùng chu kỳ (`period`) trước khi nạp dữ liệu mới, đảm bảo chạy lại nhiều lần vẫn cho kết quả nhất quán tuyệt đối, không gây phình to hay trùng lặp cơ sở dữ liệu.

---

## III. Đối Soát Tiến Hóa Kỹ Thuật Qua Các Phiên Bản Sprint

| Tiêu Chí Kỹ Thuật | Sprint 5 | Sprint 6 | Sprint 7 (Gốc) | Sprint 7 (Chốt) | Sprint 8 (Hiện Tại) |
|---|---|---|---|---|---|
| **Tổng Ca Kiểm Thử** | 147 Tests (16) | 164 Tests (17) | 173 Tests (20) | 176 Tests (20) | **187 Tests (22) ✓** |
| **Tài Liệu Hóa API** | Chưa chuẩn | Chưa chuẩn | Template mẫu cũ | Template mẫu cũ | **OpenAPI 3.0 / Swagger Chuẩn 20 Tags** |
| **Quên Mật Khẩu (Email)** | Chưa có | Chưa có | Chưa có | Chưa có | **Token 15p + Mail Service + Rate Limit** |
| **Bảo Vệ Race Condition** | Chưa kiểm thử | Chưa kiểm thử | Chưa kiểm thử | Schema Unique | **Pessimistic Write Lock (SELECT FOR UPDATE)** |
| **Kiểm Thử Tải (Load Test)**| Chưa có | Chưa có | Chưa có | Chưa có | **k6 Suite (250 VUs) + Guide chi tiết** |
| **Thí Nghiệm Ảo 3D** | Chưa có | CRUD Mock | CRUD Mock | Seed 04 bài lab + Rubric | **Seed 04 bài lab + Rubric JSON** |
| **Lượt Thi (Multi-attempt)**| 1 Lượt | 1 Lượt | Chặn cứng 1 lần | Practice: Đa lượt \| Thi: 1 | **Practice: Đa lượt \| Thi: 1** |
| **Tỷ Lệ Thực Nghiệm** | 147/147 PASS | 164/164 PASS | 173/173 PASS | 176/176 PASS | **187/187 PASS (100%)** |

---

## IV. Phân Tích 3 Hạng Mục Nâng Cấp Trọng Điểm

### 1. Chuẩn Hóa Tài Liệu Hóa OpenAPI 3.0 / Swagger UI
- Thay thế toàn bộ metadata boilerplate template bằng thông tin chính thức của đề tài: *"Hệ thống Quản lý Học tập & Thí nghiệm Ảo Vật lý 1 - REST API"*.
- Phân nhóm 20 Tags nghiệp vụ tiếng Việt: Quản lý Xác thực & Tài khoản, Lớp học & Sinh viên, Môn học & Chủ đề, Ngân hàng đề, Kỳ thi & Đợt làm bài, Thí nghiệm ảo 3D & Minh chứng, Trợ giảng Socratic AI, Phân tích CTT, Cấu hình hệ thống.
- Cấu hình Bearer JWT Authentication trực quan trên Swagger UI (`http://localhost:8080/swagger-ui/index.html`), hỗ trợ các bên tích hợp Frontend tra cứu schema và test endpoint tức thì.

### 2. Luồng Khôi Phục Mật Khẩu qua Email Token (Password Reset Flow)
- **Entity `PasswordResetToken`**: Lưu trữ mã token 32 ký tự URL-safe, gắn với `user_id` và `email`, thời hạn hiệu lực chính xác 15 phút, cờ `used` đảm bảo chỉ dùng 1 lần duy nhất (One-Time-Use).
- **Nguyên tắc bảo mật OWASP**: Endpoint `POST /api/v1/users/forgot-password` trả về thông điệp trung lập (*"Nếu email tồn tại trên hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi"*) để ngăn chặn việc dò tìm tài khoản hợp lệ.
- **Chống spam & brute-force**: Bổ sung `RateLimitFilter` chặn các IP gửi quá 3 yêu cầu forgot-password/phút bằng HTTP `429 Too Many Requests`.
- **Dịch vụ Email**: Tách lớp `IEmailService` và `EmailServiceImpl` ghi log định dạng chuẩn trong môi trường dev/test, sẵn sàng tích hợp SMTP máy chủ thực tế.
- **Kiểm chứng tự động**: Đạt 8/8 test cases mới trong `PasswordResetControllerTest` (PWD-01 đến PWD-08).

### 3. Xử Lý Tranh Chấp Dữ Liệu & Khóa Bi Quan (Concurrency & Locking)
- **Phát hiện điểm nghẽn Race Condition**: Khi sinh viên nộp bài dồn dập hoặc mở nhiều tab cùng click nộp bài, các request `submitAttempt` đồng thời có thể gây ghi đè điểm số hoặc chấm đúp.
- **Khóa bi quan JPA (`Pessimistic Write Lock`)**: Triển khai `@Lock(LockModeType.PESSIMISTIC_WRITE)` tại `IExamAttemptRepository.findByIdWithLock` (tương đương câu lệnh SQL `SELECT ... FOR UPDATE`). Luồng đầu tiên chiếm lock chuyển trạng thái sang `GRADED`; các luồng đến sau bị chặn ngay tại tầng kiểm tra nghiệp vụ và nhận mã `400 Bad Request` (*"Attempt is already submitted"*).
- **Kiểm chứng thực nghiệm đa luồng**: 3 ca kiểm thử trong `ExamConcurrencyIntegrationTest` (CONC-01, CONC-02, CONC-03) sử dụng `ExecutorService` và `CountDownLatch` phóng 10-15 luồng đồng thời tại cùng một mili-giây, chứng minh hệ thống không bị race condition và không bị deadlock.

---

## V. Bảng Tổng Hợp Kết Quả Thực Thi 22 Phân Hệ (187/187 PASS)

| STT | Lớp Kiểm Thử (Test Suite) | Phân Hệ / Chức Năng Chịu Trách Nhiệm | Số Tests | Kết Quả |
|:---:|---|---|:---:|:---:|
| 01 | `ActivityAndAuditLogTest` | Spring AOP ActivityLog (@Async) & AuditLog (JSONB) | 9 | 9/9 PASS |
| 02 | `ActuatorHealthTest` | Spring Boot Actuator: Health Check UP & Metrics Security | 2 | 2/2 PASS |
| 03 | `AiTutorControllerTest` | Trợ giảng Socratic AI, Rate limit, IDOR Đọc & Ghi | 10 | 10/10 PASS |
| 04 | `AnalyticsControllerTest` | Học thuật CTT: Topic diff, DI CTT, AI Gaps, Idempotent Cron | 17 | 17/17 PASS |
| 05 | `ClassControllerTest` | Quản lý Lớp học, Phân công Giảng viên & Trợ giảng | 9 | 9/9 PASS |
| 06 | `DashboardControllerTest` | Bảng điều khiển học tập cá nhân & lớp (JSONB snapshot) | 5 | 5/5 PASS |
| 07 | `EvidenceControllerTest` | Hồ sơ minh chứng học tập tự động (Thi, Lab, AI Chat) | 6 | 6/6 PASS |
| 08 | `ExamControllerTest` | Thi trắc nghiệm: Multi-attempt (Practice), Strict Single (Midterm) | 16 | 16/16 PASS |
| 09 | `ExamConcurrencyIntegrationTest` **[NEW]** | Kiểm thử Concurrency: Khóa bi quan, chống race condition nộp bài | 3 | **3/3 PASS** |
| 10 | `ExperimentControllerTest` | Thí nghiệm ảo 3D: Seed 4 bài lab chuẩn, Rubric JSON 4 tiêu chí | 11 | 11/11 PASS |
| 11 | `IdorSecurityControllerTest` | Kiểm thử cô lập tài nguyên đa chiều (Học tập, Điểm, Chat) | 15 | 15/15 PASS |
| 12 | `LearningMaterialControllerTest` | Tài liệu học tập: Upload file an toàn, Duyệt, Magic bytes | 6 | 6/6 PASS |
| 13 | `LearningProgressControllerTest` | Ghi nhận tiến độ học tập sinh viên theo Topic | 11 | 11/11 PASS |
| 14 | `PasswordResetControllerTest` **[NEW]** | Khôi phục mật khẩu: Token 15 phút, Reset password, Rate limit | 8 | **8/8 PASS** |
| 15 | `QuestionBankControllerTest` | Ngân hàng câu hỏi MCQ, Bóc tách đề thi PDF Apache PDFBox | 8 | 8/8 PASS |
| 16 | `RateLimitControllerTest` | Auth Rate Limiting: 10 req/min (/signin), 3 req/min (/signup) | 2 | 2/2 PASS |
| 17 | `SemesterControllerTest` | Quản lý Học kỳ, Kích hoạt học kỳ hiện tại Atomic Update | 7 | 7/7 PASS |
| 18 | `StudentClassControllerTest` | Sinh viên tra cứu danh sách lớp học và ghi danh | 2 | 2/2 PASS |
| 19 | `SubjectControllerTest` | Quản lý Danh mục Môn học Vật lý và phân cấp kiến thức | 6 | 6/6 PASS |
| 20 | `SystemSettingControllerTest` | Cấu hình Hệ thống Động: GET, PUT, Bulk Update & RBAC | 5 | 5/5 PASS |
| 21 | `TopicControllerTest` | Chương mục kiến thức Vật lý 1 phân cấp order_index | 2 | 2/2 PASS |
| 22 | `UserControllerTest` | IAM, JWT Token rotation, Profile & Quản trị User Admin | 27 | 27/27 PASS |
| **--** | **TỔNG CỘNG TOÀN BỘ HỆ THỐNG** | **22 Phân hệ nghiệp vụ, bảo mật & chịu tải hoàn chỉnh** | **187** | **187/187 PASS (100%)** |

---

## VI. Chi Tiết 11 Ca Kiểm Thử Mới Bổ Sung Trong Sprint 8

| Mã Test | Lớp Kiểm Thử | Kịch Bản & Phương Thức Thực Nghiệm | Kết Quả |
|:---:|---|---|:---:|
| `PWD-01` | `PasswordResetControllerTest` | Yêu cầu quên mật khẩu với email hợp lệ -> Tạo token 15 phút, gửi email và trả về 200 OK. | **PASS [NEW]** |
| `PWD-02` | `PasswordResetControllerTest` | Yêu cầu quên mật khẩu với email không tồn tại -> Trả về 200 OK thông báo an toàn, không lộ thông tin. | **PASS [NEW]** |
| `PWD-03` | `PasswordResetControllerTest` | Yêu cầu quên mật khẩu với định dạng email sai -> Trả về 400 Bad Request. | **PASS [NEW]** |
| `PWD-04` | `PasswordResetControllerTest` | Đặt lại mật khẩu với token hợp lệ -> Thành công và đăng nhập được bằng mật khẩu mới. | **PASS [NEW]** |
| `PWD-05` | `PasswordResetControllerTest` | Đặt lại mật khẩu với token đã qua sử dụng -> Bị từ chối 400 Bad Request (*"Mã token này đã được sử dụng"*). | **PASS [NEW]** |
| `PWD-06` | `PasswordResetControllerTest` | Đặt lại mật khẩu với token đã hết hạn -> Bị từ chối 400 Bad Request (*"Mã token đã hết hạn"*). | **PASS [NEW]** |
| `PWD-07` | `PasswordResetControllerTest` | Đặt lại mật khẩu với token không tồn tại -> Bị từ chối 400 Bad Request (*"Mã token không hợp lệ"*). | **PASS [NEW]** |
| `PWD-08` | `PasswordResetControllerTest` | Gửi `/forgot-password` liên tiếp quá 3 lần/phút/IP -> Lần 4 bị chặn 429 Too Many Requests. | **PASS [NEW]** |
| `CONC-01` | `ExamConcurrencyIntegrationTest` | 10 luồng đồng thời gọi `startAttempt` cho kỳ thi chính thức -> Chỉ duy nhất 1 luồng thành công, 9 luồng bị 409 Conflict. | **PASS [NEW]** |
| `CONC-02` | `ExamConcurrencyIntegrationTest` | 10 luồng đồng thời gọi `submitAttempt` cho cùng 1 attempt -> Pessimistic Lock đảm bảo chỉ 1 luồng chấm điểm, 9 luồng bị 400 Bad Request. | **PASS [NEW]** |
| `CONC-03` | `ExamConcurrencyIntegrationTest` | 15 sinh viên khác nhau đồng thời nộp bài thi -> Tất cả 15 lượt đều thành công, không bị deadlock hay timeout. | **PASS [NEW]** |

---

## VII. Kiểm Thử Tải & Tiêu Chuẩn Hiệu Năng Vận Hành (Load Test SLA)

Hệ thống đã được thiết kế sẵn bộ kiểm thử tải k6 chuyên dụng (`load_test_k6.js`) và tài liệu hướng dẫn (`load_test_guide.md`) đáp ứng các tiêu chuẩn dịch vụ (SLA):
- **Mô hình tải**: Tăng dần từ 20 VUs -> 100 VUs -> 250 VUs (giờ cao điểm nộp bài thi).
- **Độ trễ 95% request (p95 latency)**: Dưới 500ms đối với các tác vụ truy vấn và nộp bài.
- **Độ trễ 99% request (p99 latency)**: Dưới 1200ms trong thời điểm chịu tải đỉnh.
- **Tỷ lệ lỗi máy chủ (5xx)**: Dưới 1%.
- **Cơ chế Rate Limiting**: Phản hồi chính xác HTTP 429 khi người dùng vượt ngưỡng, bảo vệ tài nguyên CPU và bộ nhớ của hệ thống.

---

## VIII. Kiểm Chứng Số Học Tuyệt Đối

$$\sum = 9 + 2 + 10 + 17 + 9 + 5 + 6 + 16 + 3 + 11 + 15 + 6 + 11 + 8 + 8 + 2 + 7 + 2 + 6 + 5 + 2 + 27 = 187 \quad \checkmark$$

Toàn bộ 187 bài test đã chạy thành công 100% không có bất kỳ failure hay error nào trên Apache Maven Surefire!
