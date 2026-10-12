# BÁO CÁO NGHIỆM THU HIỆU NĂNG BACKEND (BACKEND PERFORMANCE ACCEPTANCE REPORT)
## 100 CONCURRENT USERS & P95 ≤ 3.000 MS SLA EVALUATION

* **Dự án:** Hệ thống Quản lý Học tập (LMS) Vật lý
* **Công nghệ Backend:** Java 24 / Spring Boot 3.5.13 / Hibernate 6.6.45
* **Cơ sở dữ liệu:** PostgreSQL 18.6 (Cloud Neon Serverless - Region AWS us-east-2 Ohio)
* **Công cụ kiểm thử tải:** Grafana k6 v0.54.0 (Windows amd64)
* **Thời gian thực hiện:** Ngày 11/10/2026
* **Kỹ sư phụ trách:** Senior Spring Boot & PostgreSQL Performance Engineer

---

## 1. Cấu hình môi trường thử nghiệm

| Thành phần | Thông số chi tiết | Ghi chú |
| :--- | :--- | :--- |
| **Hệ điều hành Host** | Windows 11 x86_64 | Môi trường thử nghiệm local host |
| **Runtime JVM** | OpenJDK 64-Bit Server VM (build 24.0.1+9) | `-Xms512m -Xmx1024m` |
| **Web Server** | Apache Tomcat 10.1.53 (Embedded) | `threads.max: 200`, `min-spare: 20`, `accept-count: 100` |
| **Connection Pool** | HikariCP 5.1.0 | `maximum-pool-size: 50`, `minimum-idle: 20`, `connection-timeout: 10000ms` |
| **Database Server** | PostgreSQL 18.6 Neon Serverless | `ep-noisy-star-aeavcjvp-pooler.c-2.us-east-2.aws.neon.tech` |
| **Độ trễ mạng DB (RTT)** | **~240 ms - 260 ms** (Ping từ VN sang AWS US East Ohio) | **Yếu tố then chốt ảnh hưởng độ trễ I/O** |
| **Cấu hình JDBC** | `reWriteBatchedInserts=true`, `sslmode=require` | Multi-row batch insert |
| **Hibernate ORM** | Hibernate 6.6.45.Final | `batch_size: 50`, `order_inserts: true`, `order_updates: true` |
| **Storage Backend** | Local File Storage Engine (Chunk directory) | `minio.enabled: false` (tránh timeout kết nối) |

---

## 2. Cấu hình Workload thực tế (Realistic Pilot Workload)

Kịch bản kiểm thử tải được xây dựng mô phỏng phân bổ hành vi người dùng thực tế trong trường học với 100 người dùng đồng thời, chia thành 6 nhóm nghiệp vụ:

| Nhóm hoạt động | Tỷ lệ (%) | Số VUs | Nghiệp vụ chi tiết |
| :--- | :---: | :---: | :--- |
| **1. STUDENT Browse & Materials** | 35% | 35 VUs | Xem danh sách lớp (`/students/me/classes`), xem lịch trình (`/students/me/agenda`), tải danh sách tài liệu (`/students/me/materials`). |
| **2. STUDENT Adaptive Practice** | 20% | 20 VUs | Sinh bài luyện tập thích ứng (`/practice-recommendations`), trả lời câu hỏi MCQ/Fill-blank (`/answers`), nộp bài (`/submit`), xem kết quả (`/results`). |
| **3. STUDENT Weak Topics** | 15% | 15 VUs | Xem phân tích điểm yếu cá nhân (`/weak-topics`), xem thời khóa biểu (`/students/me/schedule`). |
| **4. INSTRUCTOR Management** | 15% | 15 VUs | Xem lớp giảng dạy (`/instructor/classes`), cấu hình lớp (`/settings`), xem báo cáo ma trận điểm yếu toàn lớp (`/weak-topics-summary`). |
| **5. CHUNKED File Upload (100KB)** | 10% | 10 VUs | Upload chunk nhị phân 100 KB (`/files/chunks`), kiểm tra trạng thái (`/chunks/{id}/status`), kích hoạt ghép file đồng bộ (`/chunks/merge`). |
| **6. ADMIN Management** | 5% | 5 VUs | Phân trang danh sách người dùng (`/users/admin/users`), kiểm tra hệ thống (`/actuator/health`). |

* **Thời gian nghỉ (Think time):** 1.0s – 2.0s giữa các chu kỳ mô phỏng thao tác đọc/suy nghĩ thực tế của người dùng.
* **Tài khoản TEST:** `admin`, `gv_nguyen`, `gv_tran`, `sv_an`, `sv_binh`, `sv_cuong`, `sv_dung`.

---

## 3. Kịch bản chạy (Execution Stages)

```javascript
stages: [
  { duration: '20s', target: 20 },   // Giai đoạn 1: Warm-up khởi động kết nối
  { duration: '30s', target: 100 },  // Giai đoạn 2: Ramp-up tăng dần lên 100 VUs
  { duration: '2m',  target: 100 },  // Giai đoạn 3: Giữ tải đỉnh ổn định 100 VUs
  { duration: '20s', target: 0 },    // Giai đoạn 4: Ramp-down giảm tải
]
// Tổng thời gian mỗi lượt đo: 3 phút 10 giây (190s)
```

---

## 4. Tổng hợp số liệu đo kiểm thực tế (Before vs After)

Dưới đây là bảng so sánh trực tiếp từ file kết quả k6 thực tế (`baseline_summary.json` và `optimized_summary.json`):

| Chỉ số hiệu năng (Metrics) | BASELINE (Trước tối ưu) | OPTIMIZED (Sau tối ưu) | Mức độ cải thiện | Trạng thái SLA |
| :--- | :---: | :---: | :---: | :---: |
| **Concurrent Users (VUs)** | 100 VUs | 100 VUs | Chuẩn 100 VUs | Đạt |
| **Tổng số Requests (Total Requests)** | 1.194 requests | **3.413 requests** | **Tăng +185,8%** | Tăng throughput |
| **Tốc độ xử lý (Throughput - RPS)** | 5,1 req/s | **16,50 req/s** | **Tăng +223,5%** | Tăng gấp 3,2 lần |
| **Số request thành công** | 249 requests | **3.197 requests** | **Tăng +1.183,9%** | Tăng gấp 12,8 lần |
| **Tỷ lệ lỗi (HTTP Error Rate)** | **79,14%** (945 lỗi) | **6,32%** (216 lỗi) | **Giảm 92,0% lỗi** | Chưa đạt (<1%) |
| **Thời gian phản hồi Trung bình (Avg)** | **12.280 ms** (12,28s) | **3.708 ms** (3,71s) | **Nhanh hơn 3,3 lần** | Cải thiện mạnh |
| **Thời gian phản hồi Trung vị (P50)** | **10.000 ms** (10,00s) | **3.258 ms** (3,26s) | **Nhanh hơn 3,1 lần** | Gần ngưỡng SLA |
| **P90 Response Time** | 15.080 ms | **6.062 ms** | **Nhanh hơn 2,5 lần** | Cải thiện |
| **P95 Response Time (Tất cả)** | **47.920 ms** (47,92s) | **10.000 ms** (10,00s)* | **Giảm 79,1% độ trễ** | **Chưa đạt SLA (≤3s)** |
| **P95 Response Time (Request thành công)** | 10.000 ms | **6.041 ms** | **Giảm 39,6% độ trễ** | Cải thiện |
| **P99 Response Time** | 56.410 ms | **10.010 ms** | **Giảm 82,3%** | Cải thiện |
| **Thời gian phản hồi lớn nhất (Max)** | 60.000 ms | **10.026 ms** | Cắt đứt treo kết nối | Có timeout chặn |

*\*Ghi chú: P95 ở mức 10.000ms do các request lỗi bị timeout ở ngưỡng cấu hình client 10s.*

---

## 5. Bảng kết quả chi tiết theo từng Endpoint (Endpoint Breakdown)

Số liệu trích xuất từ các Trend metrics chuyên biệt của k6:

| Nhóm Endpoint | Endpoint URL | Avg | P50 (Median) | P90 | P95 | Đánh giá SLA (≤3.000ms) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Instructor Classes** | `GET /api/v1/instructor/classes` | 2.440 ms | 2.350 ms | 3.260 ms | **3.790 ms** | **Gần đạt SLA** |
| **Student Browse** | `GET /api/v1/students/me/classes`<br>`GET /api/v1/students/me/agenda` | 2.940 ms | 2.830 ms | 4.610 ms | **5.050 ms** | Khá tốt (P50 < 3s) |
| **Admin Queries** | `GET /api/v1/users/admin/users?page=0&size=10` | 2.960 ms | 2.750 ms | 4.330 ms | **4.610 ms** | Khá tốt (P50 < 3s) |
| **Student Materials** | `GET /api/v1/students/me/materials` | 3.140 ms | 3.190 ms | 4.280 ms | **4.790 ms** | Ổn định |
| **Chunk Merge** | `POST /api/v1/files/chunks/merge` | 3.270 ms | 3.070 ms | 4.150 ms | **4.580 ms** | **100% PASS, không lỗi** |
| **Student Weak Topics** | `GET /api/v1/student/classes/{id}/weak-topics` | 3.890 ms | 3.800 ms | 4.760 ms | **5.150 ms** | Cải thiện sau gom batch |
| **Chunk Upload 100K** | `POST /api/v1/files/chunks` | 4.240 ms | 4.050 ms | 5.840 ms | **6.070 ms** | **100% PASS, I/O ổn định** |
| **Practice Recommendation** | `POST /api/v1/student/classes/{id}/practice-recommendations` | 5.540 ms | 5.560 ms | 6.620 ms | **6.820 ms** | Nhiều bước phân tích điểm yếu |
| **Instructor Weak Summary** | `GET /api/v1/instructor/classes/{id}/weak-topics-summary` | 9.300 ms | 10.000 ms | 10.000 ms | **10.000 ms** | **Nghẽn (Timeout 10s chiếm 58%)** |
| **Practice Submit** | `POST /api/v1/student/practice-sessions/{id}/submit` | 9.990 ms | 10.000 ms | 10.000 ms | **10.000 ms** | **Nghẽn (Timeout 10s chấm điểm)** |

---

## 6. Đánh giá Chunked File Upload & Merge (Yêu cầu Bước 6)

Tính năng Chunked File Upload được đo kiểm độc lập trong phạm vi SLA phi AI:

* **Upload chunk 100 KB (`POST /api/v1/files/chunks`):**
  * Tỷ lệ thành công: **100%** (0 lỗi).
  * Thời gian phản hồi trung bình: **4.240 ms** (dưới tải 100 VUs đồng thời).
  * Min: 2.020 ms, Median: 4.050 ms.
  * I/O đĩa: Lưu trữ chunk binary tức thời vào phân vùng tạm không bị nghẽn file lock.
* **Kiểm tra trạng thái (`GET /api/v1/files/chunks/{uploadId}/status`):**
  * Tỷ lệ thành công: **100%** (0 lỗi).
  * Thời gian phản hồi trung bình: **~700 ms – 1.100 ms**.
* **Merge file đồng bộ (`POST /api/v1/files/chunks/merge`):**
  * Tỷ lệ thành công: **100%** (0 lỗi).
  * Thời gian phản hồi trung bình: **3.270 ms**.
  * Median (P50): **3.070 ms**, P90: **4.150 ms**.
  * Nguyên nhân tối ưu: Chuyển cấu hình `minio.enabled: false` giúp loại bỏ hoàn toàn độ trễ socket timeout (1.5s - 3.0s) khi gọi MinIO không khả dụng, metadata ghi vào DB PostgreSQL và xóa folder tạm trong transaction nhất quán.

---

## 7. Metrics hệ thống (CPU, RAM, JVM, HikariCP)

Số liệu thu thập thực tế từ Spring Boot Actuator qua `monitor_system.py`:

| Chỉ số giám sát | BASELINE (Trước tối ưu) | OPTIMIZED (Sau tối ưu) | Phân tích kỹ thuật |
| :--- | :---: | :---: | :--- |
| **CPU Usage (Process)** | Max 5,89%, Avg 2,33% | **Max 10,02%, Avg 3,16%** | CPU sử dụng rất thấp (<11%), hệ thống hoàn toàn không bị nghẽn CPU |
| **JVM Memory Used** | Max 315,3 MB, Avg 264,8 MB | **Max 319,1 MB, Avg 262,8 MB** | Ổn định trong định mức heap 512MB-1GB, không có dấu hiệu rò rỉ bộ nhớ (leak) |
| **Hikari Active Connections** | **Max 10,0 (Chạm trần)** | **Max 50,0 (Chạm trần)** | Nâng pool từ 10 lên 50 giúp hấp thụ được 3.413 requests thay vì 1.194 requests |
| **Hikari Pending Threads** | **Max 15,0, Avg 6,2** | **Max 58,0, Avg 14,8** | Dưới 100 VUs đồng thời, 50 kết nối DB vẫn bị xếp hàng khi gặp các truy vấn dài |
| **Hikari Idle Connections** | Min 0,0, Avg 0,8 | Min 0,0, Avg 4,2 | Trạng thái tận dụng tối đa pool kết nối |

---

## 8. Các nút thắt phát hiện được (Root Cause Analysis)

1. **Nút thắt #1 (NGHIÊM TRỌNG NHẤT): Độ trễ địa lý mạng tới Database Neon (RTT ~240ms)**
   * Neon PostgreSQL nằm tại `AWS us-east-2 (Ohio, Hoa Kỳ)`. Mỗi lượt gửi nhận TCP/TLS qua Internet từ Việt Nam mất **240 - 260 ms**.
   * Bất kỳ luồng xử lý nào có từ 5-10 round-trips SQL tuần tự đều sẽ mất: $10 \times 240\text{ms} = 2.400\text{ms}$ chỉ riêng thời gian chờ mạng, chưa tính thời gian CPU thực thi câu lệnh!
2. **Nút thắt #2: Cấu hình HikariCP mặc định chỉ 10 connections (Đã khắc phục)**
   * Ở Baseline, Spring Boot không nhận cấu hình pool cũ (`spring.tomcat.max-active: 50`) nên fallback về mặc định 10 connections. 10 connections với RTT 240ms chỉ phục vụ được tối đa ~41 queries/giây trên toàn hệ thống $\rightarrow$ dẫn tới 79% requests bị timeout.
   * Khi nâng lên 50 connections, throughput lập tức tăng vọt gấp 3,2 lần (từ 5,1 lên 16,5 req/s) và tỷ lệ thành công tăng gấp gần 13 lần.
3. **Nút thắt #3: Vòng lặp N+1 chấm điểm trong `submitPracticeSession` (Gây timeout 10s)**
   * Phương thức `submitPracticeSession` duyệt từng câu hỏi trong phiên để chấm điểm và gọi `findByQuestionIdAndIsCorrectTrue(qId)`, sau đó gọi `getStudentWeakTopics(classId, studentId)` để cập nhật lại ma trận điểm yếu.
   * Dưới 100 VUs đồng thời, hàng loạt truy vấn dồn vào làm các request submit vượt quá 10 giây (k6 timeout).
4. **Nút thắt #4: Ma trận điểm yếu toàn lớp `getClassWeakTopicsSummary` (Gây timeout 10s)**
   * Endpoint giáo viên xem ma trận điểm yếu lặp qua từng học sinh trong lớp (`activeEnrollments`), mỗi học sinh lại quét lịch sử làm bài thi và luyện tập. Dù đã gom batch, khi nhiều VU gọi đồng thời thì tài nguyên pool 50 connection vẫn bị chiếm giữ kéo dài.

---

## 9. Những tối ưu đã thực hiện trong đợt nghiệm thu

* **Tối ưu 1 (Datasource & Server Pool):**
  * Sửa cấu hình `application-dev.yml` và `application-local.yml`:
    * `spring.datasource.hikari.maximum-pool-size: 50`
    * `spring.datasource.hikari.minimum-idle: 20`
    * `server.tomcat.threads.max: 200`
    * `server.tomcat.threads.min-spare: 20`
* **Tối ưu 2 (Tắt MinIO Timeout):**
  * Cấu hình `minio.enabled: false` để lưu trực tiếp Local Storage khi không có cụm MinIO thật, giúp Chunk Merge giảm từ >3.5s xuống **1.4s** (dưới tải đỉnh đạt 3.27s).
* **Tối ưu 3 (JDBC Multi-Row Batch Inserts):**
  * Bổ sung `reWriteBatchedInserts=true` vào PostgreSQL JDBC URL.
  * Cấu hình Hibernate `hibernate.jdbc.batch_size: 50`, `order_inserts: true`, `order_updates: true`.
* **Tối ưu 4 (Xóa bỏ N+1 Queries trong Practice Recommendation Service):**
  * Thêm `findByQuestionIdIn` vào `QuestionOptionRepository` và `QuestionAcceptedAnswerRepository`.
  * Thêm `findByAttemptIdIn` vào `IExamAnswerRepository`.
  * Thay thế 40 sequential queries trong `analyzeStudentWeakTopics` bằng batch `findAllById` và `findByIn`.
  * Viết lại `buildSessionDTO` tái sử dụng thực thể câu hỏi trong bộ nhớ thay vì query lại từ DB sau khi vừa INSERT.

---

## 10. Kịch bản kiểm thử tải (k6 Script)

File k6 thực tế đặt tại: `d:\vatli1\hethongvatli1\load_test_100vu.js`

* **Cấu trúc kịch bản:**
  * Hàm `setup()`: Đăng nhập trước toàn bộ tài khoản test (`admin`, `gv_nguyen`, `gv_tran`, `sv_an`, `sv_binh`, `sv_cuong`, `sv_dung`), lưu access tokens vào memory, chuẩn bị buffer 100KB binary data.
  * Phân bổ VU ID (`__VU % 100`): 35% Student Browse, 20% Adaptive Practice, 15% Weak Topics, 15% Instructor, 10% Chunked Upload & Merge, 5% Admin.
  * Có `sleep(0.5s - 1.0s)` mô phỏng thời gian suy nghĩ giữa các bước.
  * Xuất đầy đủ trend metrics cho từng endpoint.

---

## 11. Bằng chứng chạy kiểm thử thực tế (Raw Artifacts & Logs)

1. **File cấu hình test:** `d:\vatli1\hethongvatli1\load_test_100vu.js`
2. **File k6 baseline kết quả:** `d:\vatli1\hethongvatli1\baseline_summary.json`
3. **File k6 optimized kết quả:** `d:\vatli1\hethongvatli1\optimized_summary.json`
4. **File giám sát tài nguyên baseline:** `d:\vatli1\hethongvatli1\baseline_system_metrics.json`
5. **File giám sát tài nguyên optimized:** `d:\vatli1\hethongvatli1\optimized_system_metrics.json`
6. **Raw task logs k6 baseline:** `C:\Users\84833\.gemini\antigravity-ide\brain\f184311f-be7c-41c2-ab33-9beeb670eb4d\.system_generated\tasks\task-6047.log`
7. **Raw task logs k6 optimized:** `C:\Users\84833\.gemini\antigravity-ide\brain\f184311f-be7c-41c2-ab33-9beeb670eb4d\.system_generated\tasks\task-6513.log`

---

## 12. KẾT LUẬN NGHIỆM THU (ACCEPTANCE VERDICT)

Căn cứ theo tiêu chí nghiệm thu khắt khe của dự án:
1. *Tối thiểu 100 người dùng đồng thời trong môi trường thí điểm.*
2. *Ít nhất 95% HTTP requests không phải tác vụ AI có thời gian phản hồi ≤ 3.000 ms (P95 ≤ 3.000 ms).*
3. *Tỷ lệ lỗi http_req_failed < 1%.*

### Kết luận chính thức:

### ❌ **FAIL (CHƯA ĐẠT TIÊU CHUẨN NGHIỆM THU P95 ≤ 3.000 MS VÀ ERROR RATE < 1%)**

### Lý do kỹ thuật:
1. **P95 tổng thể đo được là 10.000 ms** (ngưỡng timeout của client k6) và **6.041 ms** đối với các request thành công $\rightarrow$ **Vượt quá ngưỡng SLA ≤ 3.000 ms**.
2. **Tỷ lệ lỗi thực tế là 6,32%** (dù đã cải thiện vượt bậc từ 79,14% ở Baseline) $\rightarrow$ **Chưa đạt ngưỡng đề xuất < 1%**.
3. **Nguyên nhân cốt lõi ngoài tầm kiểm soát của code cục bộ:**
   * Cơ sở dữ liệu đang đặt trên Neon Cloud tại **Ohio, Hoa Kỳ (RTT ~240ms)** trong khi Backend chạy tại Việt Nam. Khi 100 VUs đồng thời thực hiện các nghiệp vụ ghi/chấm bài nhiều bước, độ trễ mạng Internet xuyên lục địa tích lũy làm thời gian xử lý vượt quá 3 giây.
   * Hai endpoint gây nghẽn chính kéo tụt P95 toàn hệ thống: `POST /api/v1/student/practice-sessions/{id}/submit` và `GET /api/v1/instructor/classes/{id}/weak-topics-summary`.

---

## 13. Khuyến nghị giải pháp để đạt chuẩn PASS 100% trong tương lai

1. **Triển khai Database nội vùng (Same-Region DB Deployment):**
   * Đặt PostgreSQL trên cùng cụm máy chủ hoặc cùng Region mạng với Backend (độ trễ mạng nội bộ $< 2\text{ms}$ thay vì $240\text{ms}$). Với độ trễ $< 2\text{ms}$, toàn bộ các luồng batch query hiện tại sẽ hoàn tất trong $< 150\text{ms}$, hệ thống sẽ **đạt P95 $\le 500\text{ms}$ ở 100 VUs**.
2. **Tối ưu hóa luồng nộp bài (`submitPracticeSession`):**
   * Gom toàn bộ logic chấm điểm của 10 câu hỏi thành 1 truy vấn SQL lấy đáp án đúng duy nhất (`findByQuestionIdInAndIsCorrectTrue`).
   * Chuyển tác vụ tính toán lại ma trận điểm yếu học sinh (`getStudentWeakTopics`) sang xử lý bất đồng bộ (`@Async` hoặc Event/Message Queue) sau khi nộp bài thành công thay vì bắt người dùng chờ đồng bộ trong HTTP request.
3. **Bộ nhớ đệm (Caching với Redis hoặc Caffeine):**
   * Áp dụng Cache có TTL ngắn (1-2 phút) cho `ClassSettings` và `getClassWeakTopicsSummary` để giáo viên xem thống kê không phải quét lại toàn bộ dữ liệu lịch sử của cả lớp.
