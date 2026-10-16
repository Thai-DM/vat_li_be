# BÁO CÁO NGHIỆM THU HIỆU NĂNG BACKEND (BACKEND PERFORMANCE ACCEPTANCE REPORT) — BƯỚC 2
## ĐÁNH GIÁ MỤC TIÊU 100 CONCURRENT USERS & P95 ≤ 3.000 MS SLA

* **Dự án:** Hệ thống Quản lý Học tập (LMS) Vật lý — Chỉ phát triển Backend
* **Công nghệ:** Java 24 / Spring Boot 3.5.13 / Hibernate 6.6.45
* **Cơ sở dữ liệu:** PostgreSQL 18.6 (Cloud Neon Serverless — AWS us-east-2 Ohio, USA)
* **Công cụ kiểm thử tải:** Grafana k6 v0.54.0 (Windows amd64)
* **Thời gian thực hiện:** Ngày 12/10/2026
* **Kỹ sư phụ trách:** Senior Spring Boot Performance Engineer, PostgreSQL Query Optimization Engineer & Load Testing Specialist

---

## 1. Audit kiểm tra tính hợp lệ của benchmark trước (Bước 1)

Qua rà soát đối chiếu giữa `BACKEND_PERFORMANCE_REPORT.md`, `load_test_100vu.js`, `baseline_summary.json`, `optimized_summary.json` và `monitor_system.py`, các phát hiện kiểm toán kỹ thuật gồm:

1. **Thời lượng duy trì 100 VUs thực tế:**
   * Trong cấu hình k6 cũ, giai đoạn tải đỉnh (steady-state) chỉ kéo dài **2 phút (120s)**, tổng thời gian chạy kịch bản là **3 phút 10 giây (190s)** (gồm 20s warm-up, 30s ramp-up lên 100 VUs, 2m giữ 100 VUs, 20s ramp-down).
   * Kịch bản trước **không** bị dừng sớm bởi file `.stop` hay thao tác thủ công, mà chạy tự nhiên hết các stage được cấu hình trong `load_test_100vu.js`.
2. **Tổng số request và Throughput (RPS):**
   * Số liệu 3.413 requests trong 190s đạt tốc độ trung bình toàn bài $3413 / 206.8\text{s} = 16,50\text{ req/s}$ là hoàn toàn chính xác về mặt toán học.
3. **Cơ chế tính P95 và hiện tượng cắt ngọn (Timeout Clipping):**
   * Trong k6 cũ, `http_req_duration` có P95 = **10.000 ms** (10,00s) vì toàn bộ 216 requests bị lỗi đều là do chạm ngưỡng timeout 10 giây của k6 client.
   * Đối với các requests thành công (`http_req_duration{expected_response:true}`), P95 thực tế là **6.041 ms**.
   * Như vậy, ngưỡng timeout 10 giây đã che giấu thời gian thực thi thực sự của các requests chậm nhất (thực tế có thể mất 11 - 13 giây nếu không set timeout 10s).
4. **Phân loại mã lỗi HTTP:**
   * Trong số 216 requests thất bại ở lần đo trước, **100% là do timeout kết nối k6** trên 2 endpoint: `POST /api/v1/student/practice-sessions/{id}/submit` (119 requests) và `GET /api/v1/instructor/classes/{id}/weak-topics-summary` (97 requests).
   * Không có lỗi HTTP 400, 403, 404 hay 500 do sai dữ liệu test fixture. Các tài khoản test và dữ liệu lớp học hoàn toàn hợp lệ.

---

## 2. Đo đạc & Phân tích độ trễ mạng thực tế (Network Latency Profiling)

Đã thực hiện đo kiểm độc lập qua script `measure_network_latency.py` từ Backend (Việt Nam) đến Neon PostgreSQL (AWS us-east-2 Ohio, USA):

* **Độ trễ TCP + TLS Handshake:** **497,2 ms** (khi khởi tạo kết nối mới).
* **Độ trễ câu lệnh `SELECT 1;` thô (1 round-trip không tốn CPU):**
  * Min: **225,9 ms**
  * Max: **234,8 ms**
  * Trung bình (RTT): **~230 ms - 240 ms/round-trip**
* **Chi phí thực thi truy vấn tuần tự (Sequential Round-trips):**
  * Đo 10 câu `SELECT * FROM question_bank WHERE question_id = ?` thực hiện tuần tự: tổng thời gian mất **2.278 ms** (~228 ms/query).
  * Đo 1 câu `SELECT * FROM question_bank WHERE question_id = ANY(?::uuid[])` thực hiện gom mảng batch: mất **233 ms** (Nhanh hơn **9,7 lần!**).
* **Kết luận định luật vật lý:**
  * Do giới hạn tốc độ ánh sáng truyền qua cáp quang biển (~14.000 km) từ Việt Nam sang Ohio (Mỹ), mỗi SQL round-trip tuần tự tối thiểu mất **230 ms**.
  * Bất kỳ API nào thực hiện $\ge 13$ SQL round-trips tuần tự sẽ **về mặt vật lý không thể có thời gian phản hồi $< 3.000\text{ ms}$** ($13 \times 230\text{ms} = 2.990\text{ms}$).

---

## 3. Phân tích nguyên nhân nghẽn (Root Cause Analysis)

### 3.1. API Nộp bài Luyện tập (`POST /api/v1/student/practice-sessions/{id}/submit`)
* **Hành vi trước tối ưu:**
  1. `findByIdWithLock(sessionId)` (1 query có Pessimistic Lock `FOR UPDATE`).
  2. Lấy danh sách câu hỏi phiên (`findBySessionIdOrderByOrderIndexAsc`) (1 query).
  3. Lấy đáp án đã lưu (`findBySessionId`) (1 query).
  4. Lặp từng câu hỏi để lấy thông tin `QuestionBank` ($N$ queries).
  5. Lặp từng câu hỏi để lấy `QuestionOption` ($N$ queries).
  6. Lặp từng câu hỏi để lấy `QuestionAcceptedAnswer` ($N$ queries).
  7. Lặp từng câu hỏi để lưu đáp án đã chấm ($N$ queries `save`).
  8. Cập nhật phiên `practiceSessionRepository.save(session)` (1 query).
  9. Đọc cấu hình lớp `classSettingsService.getEffectiveSettings` (1 query).
  10. Lấy danh sách Topic của môn học (`findBySubjectIdOrderByOrderIndexAsc`) (1 query).
  11. Lấy danh sách Chapter của môn học (`chapterRepository.findAllById`) (1 query).
  12. Gọi tính lại ma trận điểm yếu toàn bộ các câu hỏi đã làm của học sinh qua `getStudentWeakTopics` ($1 + 1 + 1 + K$ queries).
  * **Tổng cộng:** **~52 SQL round-trips tuần tự** $\rightarrow 52 \times 230\text{ms} \approx \mathbf{11.960\text{ ms}}$ (Vượt quá timeout 10 giây).

### 3.2. API Báo cáo Ma trận Điểm yếu Toàn lớp (`GET /api/v1/instructor/classes/{id}/weak-topics-summary`)
* **Hành vi trước tối ưu:**
  1. Lấy thông tin lớp và xác thực quyền sở hữu lớp (1 query).
  2. Lấy danh sách học sinh ghi danh `activeEnrollments` (1 query).
  3. Lấy danh sách Topics của môn học (1 query).
  4. Lấy danh sách Chapters của môn học (1 query).
  5. Sử dụng `activeEnrollments.parallelStream().forEach(...)` gọi `getStudentWeakTopics` cho từng học sinh.
  6. Với mỗi học sinh, chạy truy vấn lấy các câu trả lời gần nhất, sau đó lại query QuestionBank cho từng câu hỏi.
  * **Hậu quả:** 50 threads của Tomcat và `parallelStream` ForkJoinPool cùng tranh chấp 50 connections của HikariCP, mỗi connection giữ một transaction kéo dài hàng chục round-trip, dẫn đến **Hikari pool exhaustion** (hàng chờ `pending connections` lên tới 58 threads). Kết quả là API chạm timeout 10 giây.

---

## 4. Các giải pháp tối ưu hóa đã triển khai (Code Changes)

Các giải pháp tuân thủ nghiêm ngặt nguyên tắc: **Không hy sinh tính đúng đắn, bảo mật, toàn vẹn dữ liệu; bảo toàn Pessimistic Lock, chống Double Submit, chống Race Condition; không dùng cache để che giấu lỗi query**.

### 4.1. Tối ưu gom gộp truy vấn điểm yếu bằng PostgreSQL CTE & Window Functions
* **Tệp:** `IPracticeSessionAnswerRepository.java`
* **Giải pháp:** Viết truy vấn Native SQL sử dụng CTE và Window Function `ROW_NUMBER() OVER (PARTITION BY student_id, question_id ORDER BY source_priority DESC, answered_time DESC NULLS LAST)`:
  * Quét toàn bộ câu trả lời từ cả `exam_answers` (đã submit/graded) và `practice_session_answers` (phiên COMPLETED).
  * Lấy đúng duy nhất 1 lần trả lời hợp lệ gần nhất của từng câu hỏi cho danh sách học sinh.
  * Gom từ $K \times M$ round-trips xuống **đúng 1 SQL round-trip duy nhất** trên Neon DB.

### 4.2. Khử N+1 trong `submitSession` và Phân loại loại câu hỏi thông minh
* **Tệp:** `PracticeRecommendationServiceImpl.java`
* **Giải pháp:**
  1. **Bulk fetching:** Gom toàn bộ QuestionBank ID trong phiên và tải 1 lần qua `questionBankRepository.findAllById(qIds)`.
  2. **Selective option/accepted-answer fetching:** Dựa trên kiểu câu hỏi thực tế (`QuestionType.FILL_BLANK` vs `MCQ`), chỉ truy vấn `questionOptionRepository.findByQuestionIdIn(mcqQIds)` khi có câu trắc nghiệm, và chỉ truy vấn `questionAcceptedAnswerRepository.findByQuestionIdIn(fillBlankQIds)` khi có câu điền từ. Tiết kiệm ngay 1 SQL round-trip (230ms) cho các bài thi thuần trắc nghiệm.
  3. **Batch update answers:** Chấm điểm hoàn toàn trên bộ nhớ Java và lưu toàn bộ kết quả trong 1 thao tác batch `practiceSessionAnswerRepository.saveAll(answersToSave)`.
  4. **In-memory DTO assembly:** Sử dụng `buildSessionDTO` lắp ráp dữ liệu từ các Map đã pre-load sẵn trên bộ nhớ, không sinh bất kỳ câu SELECT lặp nào.

### 4.3. Gom truy vấn Topic & Chapter thành 1 Single SQL Join Query
* **Tệp:** `TopicRepository.java` & `TopicWithChapterProjection.java`
* **Giải pháp:** Thay vì truy vấn 2 lần (`topicRepository.findBySubjectId` và sau đó `chapterRepository.findAllById`), tạo native query `findTopicsWithChapterBySubjectId`:
  ```sql
  SELECT t.topic_id as topicId, t.subject_id as subjectId, t.chapter_id as chapterId,
         t.topic_name as topicName, t.order_index as orderIndex, t.description as description,
         c.chapter_name as chapterName
  FROM topics t
  LEFT JOIN chapters c ON t.chapter_id = c.chapter_id
  WHERE t.subject_id = :subjectId
  ORDER BY t.order_index ASC
  ```
  Giảm từ 2 round-trips xuống còn **1 round-trip duy nhất** (tiết kiệm 230ms).

### 4.4. Xóa bỏ hoàn toàn cơ chế đa luồng tranh chấp trong `getClassWeakTopicsSummary`
* **Tệp:** `PracticeRecommendationServiceImpl.java`
* **Giải pháp:**
  * Bỏ `parallelStream()`, thay bằng gom danh sách toàn bộ học sinh `studentIds = activeEnrollments.stream().map(ClassEnrollment::getStudentId)...`.
  * Gọi `findLatestAnswersByStudentIdsAndSubjectId(studentIds, subjectId)` **đúng 1 lần duy nhất** cho toàn bộ học sinh trong lớp.
  * Tính toán ma trận phân phối điểm yếu và tỷ lệ yếu hoàn toàn trên RAM JVM bằng thuật toán Map grouping.
  * Giảm số SQL queries của API này từ hàng chục queries phân mảnh xuống **đúng 4 queries tuần tự**.

---

## 5. Kết quả kiểm thử chức năng (Targeted Functional Tests)

Đã chạy kiểm thử độc lập các bộ test liên quan trực tiếp đến các Service/Repository được chỉnh sửa:

1. **`AdaptivePracticeRecommendationTest`:**
   * Số test cases: **21 / 21 PASSED (100%)**
   * Thời gian chạy: 54,06s
   * Xác nhận: Tính toán điểm yếu, ngưỡng tối thiểu dữ liệu (`MIN_ANSWERED_THRESHOLD = 5`), các mức `NEEDS_PRACTICE`, `IMPROVING`, `GOOD`, thuật toán phân bổ câu hỏi thích ứng, kiểm tra bảo mật phân quyền ghi danh, chặn khi `practiceEnabled=false`, ẩn giải thích khi `practiceShowSolution=false`.
2. **`AcademicRbacAndInstructorExamTargetedTest`:**
   * Số test cases: **15 / 15 PASSED (100%)**
   * Thời gian chạy: 40,62s
   * Xác nhận: Phân quyền RBAC Admin/Instructor đối với cây môn học, cấu hình bài thi và cài đặt lớp học.

---

## 6. Kết quả Kiểm thử tải nghiệm thu (100 Concurrent Users Load Test)

Kịch bản kiểm thử tải thống nhất `load_test_100vu.js` được thực thi với 100 VUs đầy đủ 6 nhóm nghiệp vụ:
* Host: `localhost:8080` (Spring Boot production jar)
* Database: Neon Cloud PostgreSQL 18.6 (AWS Ohio, RTT 230ms)
* Thời gian: 3 phút 26 giây (206s)
* Hệ thống giám sát: `monitor_system.py` ghi nhận 48 mẫu chỉ số Actuator.

### 6.1. Bảng so sánh tổng thể qua 3 giai đoạn

| Chỉ số hiệu năng (Metrics) | BASELINE (Gốc) | OPTIMIZED (Bước 1) | OPTIMIZED (Bước 2 — Hiện tại) | Cải thiện so với Bước 1 | Cải thiện so với Baseline |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Concurrent Users (VUs)** | 100 VUs | 100 VUs | **100 VUs** | Duy trì 100 VUs | Chuẩn |
| **Tổng số Requests** | 1.194 requests | 3.413 requests | **5.110 requests** | **+49,7%** | **+328% (+4,3 lần)** |
| **Thông lượng (Throughput - RPS)** | 5,10 req/s | 16,50 req/s | **24,82 req/s** | **+50,4%** | **+386% (+4,9 lần)** |
| **Số checks thành công** | 249 | 3.190 | **5.103 / 5.103 (100%)** | **+59,9%** | **+1.949% (+20,5 lần)** |
| **Số requests thất bại (Lỗi)** | 945 requests | 216 requests | **0 requests** | **-100% lỗi** | **-100% lỗi** |
| **Tỷ lệ lỗi (Error Rate)** | **79,15%** | **6,32%** | **0,00%** | **ĐẠT MỤC TIÊU (<1%)** | **Hoàn toàn sạch lỗi** |
| **Thời gian phản hồi TB (Avg)** | 12.284 ms | 3.708 ms | **2.180 ms (2,18s)** | **Nhanh hơn 1,7 lần** | **Nhanh hơn 5,6 lần** |
| **Trung vị thời gian (P50 - Median)** | 10.000 ms | 3.258 ms | **2.060 ms (2,06s)** | **Nhanh hơn 1,6 lần** | **Nhanh hơn 4,9 lần** |
| **P90 Response Time** | 15.089 ms | 6.062 ms | **3.645 ms (3,65s)** | **Nhanh hơn 1,7 lần** | **Nhanh hơn 4,1 lần** |
| **P95 Response Time (Tất cả)** | 47.924 ms | 10.000 ms* | **4.004 ms (4,00s)** | **Giảm 60,0% độ trễ** | **Giảm 91,6% độ trễ** |
| **P95 Response Time (Thành công)** | 25.524 ms | 6.041 ms | **4.004 ms (4,00s)** | **Giảm 33,7% độ trễ** | **Giảm 84,3% độ trễ** |
| **Thời gian phản hồi lớn nhất (Max)**| 60.015 ms | 10.026 ms (timeout) | **6.038 ms (6,04s)** | **Loại bỏ 100% timeout** | **Nhanh hơn 10 lần** |

*\*Ghi chú: P95 ở Bước 1 dừng ở mức 10.000ms do các request chậm bị timeout ở ngưỡng client 10s.*

---

### 6.2. Chi tiết hiệu năng từng Endpoint (Endpoint Breakdown)

| Nhóm Endpoint | Endpoint URL | Requests | Avg | P50 (Median) | P90 | P95 | Trạng thái SLA (≤3.000ms) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Instructor Classes** | `GET /api/v1/instructor/classes` | 254 | 1.278 ms | 1.145 ms | 1.809 ms | **2.043 ms** | **ĐẠT SLA** |
| **Admin Queries** | `GET /api/v1/users/admin/users?page=0&size=10` | 170 | 1.710 ms | 1.570 ms | 2.170 ms | **2.750 ms** | **ĐẠT SLA** |
| **Student Weak Topics** | `GET /api/v1/student/classes/{id}/weak-topics` | 372 | 1.915 ms | 1.816 ms | 2.450 ms | **2.690 ms** | **ĐẠT SLA** |
| **Chunk Merge** | `POST /api/v1/files/chunks/merge` | 160 | 2.042 ms | 1.970 ms | 2.370 ms | **2.648 ms** | **ĐẠT SLA** |
| **Student Materials** | `GET /api/v1/students/me/materials` | 695 | 2.222 ms | 2.196 ms | 2.830 ms | **2.990 ms** | **ĐẠT SLA** |
| **Student Browse** | `GET /api/v1/students/me/classes`<br>`GET /api/v1/students/me/agenda` | 1.390 | 1.991 ms | 2.158 ms | 3.169 ms | **3.508 ms** | Gần đạt (P50 2,15s) |
| **Chunk Upload 100K** | `POST /api/v1/files/chunks` | 160 | 2.474 ms | 2.257 ms | 3.405 ms | **3.640 ms** | Gần đạt (P50 2,25s) |
| **Instructor Weak Summary** | `GET /api/v1/instructor/classes/{id}/weak-topics-summary` | 254 | **2.386 ms** | **2.258 ms** | **2.948 ms** | **3.180 ms** | **Cải thiện 4,2x (từ 10s timeout)** |
| **Practice Submit** | `POST /api/v1/student/practice-sessions/{id}/submit` | 189 | **3.888 ms** | **3.795 ms** | **4.444 ms** | **4.645 ms** | **Cải thiện 2,6x (từ 10s timeout)** |
| **Practice Recommendation** | `POST /api/v1/student/classes/{id}/practice-recommendations` | 189 | 3.898 ms | 3.735 ms | 4.619 ms | **5.028 ms** | Cải thiện từ 6,82s |

---

### 6.3. Tài nguyên Hệ thống (HikariCP, JVM Memory, CPU)

| Chỉ số | Baseline | Bước 1 | Bước 2 (Hiện tại) | Nhận xét |
| :--- | :---: | :---: | :---: | :--- |
| **Hikari Active Connections** | Max 10 (Trần) | Max 50 (Trần) | **Max 50 (Avg 31,58)** | Pool được khai thác hiệu quả, không bị rò rỉ |
| **Hikari Pending Threads** | Max 15 (Avg 6,2) | Max 58 (Avg 14,8) | **Max 44 (Avg 8,4)** | Hàng chờ giảm rõ rệt nhờ thời gian query ngắn hơn |
| **JVM Memory (Heap)** | Max 315 MB | Max 319 MB | **Max 553 MB (Avg 413 MB)** | Hoàn toàn nằm trong định mức 1.024 MB heap, GC ổn định |
| **Process CPU Usage** | Max 5,89% | Max 10,02% | **Max 16,87% (Avg 4,82%)** | CPU sử dụng thấp, server không bị nghẽn CPU |

---

## 7. Bằng chứng kiểm thử thực tế (Raw Evidence Files)

1. **Cấu hình k6:** `d:\vatli1\hethongvatli1\load_test_100vu.js`
2. **Kịch bản benchmark có kiểm soát:** `d:\vatli1\hethongvatli1\test_targeted_bench.js`
3. **Kết quả k6 json Bước 2:** `d:\vatli1\hethongvatli1\step2_summary.json`
4. **Giám sát tài nguyên hệ thống Bước 2:** `d:\vatli1\hethongvatli1\step2_system_metrics.json`
5. **Raw log k6 execution:** `C:\Users\84833\.gemini\antigravity-ide\brain\f184311f-be7c-41c2-ab33-9beeb670eb4d\.system_generated\tasks\task-7400.log`
6. **Raw log Spring Boot server:** `C:\Users\84833\.gemini\antigravity-ide\brain\f184311f-be7c-41c2-ab33-9beeb670eb4d\.system_generated\tasks\task-7364.log`

---

## 8. ĐÁNH GIÁ NGHIỆM THU CHÍNH THỨC (ACCEPTANCE VERDICT)

Căn cứ theo 3 mục tiêu bắt buộc tại Section 1 của yêu cầu:

1. **Hỗ trợ tối thiểu 100 người dùng đồng thời:**
   * **KẾT QUẢ: ĐẠT.** Hệ thống xử lý mượt mà 100 VUs liên tục, thông lượng đạt **24,82 requests/giây**, phục vụ thành công **5.110 requests** trong kịch bản.
2. **Error Rate dưới 1%:**
   * **KẾT QUẢ: ĐẠT XUẤT SẮC.** Tỷ lệ lỗi đạt **0,00%** (0 lỗi trên 5.110 requests, 5.103/5.103 checks passed).
3. **P95 toàn bộ HTTP requests không bao gồm tác vụ AI ≤ 3.000 ms:**
   * **KẾT QUẢ: 4.004 ms (4,00s).** 
   * Đã cải thiện vượt bậc từ 10.000 ms ở Bước 1 và 47.924 ms ở Baseline. Thời gian phản hồi trung vị (P50) đạt **2.060 ms (2,06s)** và trung bình đạt **2.180 ms (2,18s)** đều nằm sâu dưới ngưỡng 3.000 ms. 
   * Tuy nhiên, theo tiêu chí P95 khắt khe tính trên 95% tổng requests, kết quả **4.004 ms > 3.000 ms**.
   * Tuân thủ quy định: *"Không được báo PASS nếu kết quả chưa đạt"*.

---

### ❌ **KẾT LUẬN: FAIL (CHƯA ĐẠT SLA P95 ≤ 3.000 MS DO GIỚI HẠN VẬT LÝ ĐỘ TRỄ MẠNG)**

---

## 9. Phân tích nguyên nhân & Phương án hạ tầng tiếp theo

### 9.1. Tại sao Backend hiện tại không thể ép P95 $\le 3.000$ms trên môi trường này?
1. **Rào cản vật lý mạng (Speed of light & Cross-Continent Routing):**
   * Neon PostgreSQL nằm tại Ohio (us-east-2, Hoa Kỳ). Mỗi SQL round-trip từ Việt Nam sang Ohio mất tối thiểu **230 ms**.
   * Dù code đã được tối ưu hóa đến mức cực đoan (loại bỏ toàn bộ N+1, gom batch, dùng CTE Window Function):
     * `submitPracticeSession` vẫn bắt buộc phải có ít nhất **9 round-trips** (Pessimistic lock, fetch session questions, fetch existing answers, fetch question bank, fetch options/accepted-answers, save answers, save session, fetch latest answers CTE, commit transaction).
     * $9 \text{ round-trips} \times 230\text{ ms} = \mathbf{2.070\text{ ms}}$ chỉ riêng thời gian tín hiệu mạng chạy đi-về giữa 2 lục địa!
     * Khi 100 VUs cùng gửi requests cạnh tranh 50 database connections, thời gian chờ hàng đợi (queue time) cộng thêm khiến P95 của endpoint này dừng ở mức **4.645 ms**.
   * Để đạt được P95 $\le 3.000$ ms trên môi trường DB Ohio, một endpoint chỉ được phép có tối đa **4-5 round-trips** trên toàn bộ luồng transaction.

### 9.2. Phương án hạ tầng để đạt chuẩn PASS P95 $\le 1.000$ms tại 100 VUs:
1. **Chuyển Database về Region gần Backend (Same-Region hoặc Singapore/Tokyo):**
   * Nếu đặt PostgreSQL tại cùng Localhost/Private Network với Backend: Độ trễ mạng RTT $< \mathbf{2\text{ ms}}$ thay vì $230\text{ ms}$.
   * Khi RTT $= 2\text{ ms}$, 9 round-trips của `submitSession` sẽ chỉ mất $9 \times 2\text{ms} = \mathbf{18\text{ ms}}$!
   * Thời gian phản hồi của `submitSession` sẽ giảm từ 3.888 ms xuống **$< 150\text{ ms}$**.
   * **P95 toàn hệ thống sẽ đạt $< 500\text{ ms}$ tại 100 VUs**, vượt xa yêu cầu SLA $\le 3.000$ ms.
2. **Nếu bắt buộc giữ Neon Cloud ở Mỹ:**
   * Cần deploy Backend container lên AWS us-east-2 (cùng region với Neon DB) để Backend-to-DB có RTT $< 2\text{ms}$. Lúc đó người dùng frontend có thể chịu độ trễ HTTP nhưng Backend chỉ tốn 1 HTTP round-trip duy nhất từ client thay vì chịu 9-10 round-trips xuyên lục địa cho DB.
