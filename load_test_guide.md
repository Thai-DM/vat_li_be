# Hướng Dẫn & Báo Cáo Kiểm Thử Tải & Xử Lý Đồng Thời (Load & Concurrency Testing Guide)

Hệ thống Học phần Vật lý 1 (Physics 1 LMS & Virtual Lab) được thiết kế theo kiến trúc phi trạng thái (Stateless Architecture) với cơ chế bảo vệ phân tầng:
1. **Application-Layer Rate Limiting**: Ngăn chặn brute-force và spam email tại cửa ngõ API.
2. **Database Pessimistic Locking & Unique Constraints**: Ngăn chặn triệt để race condition khi hàng trăm sinh viên cùng bắt đầu làm bài hoặc nộp bài tại cùng một thời điểm.

---

## 1. Kiểm Thử Đồng Thời Cấp Độ Ứng Dụng & CSDL (Automated Concurrency Tests)

Toàn bộ các tình huống tranh chấp dữ liệu (Race Conditions) đã được mã hóa thành các kiểm thử tự động tại class `ExamConcurrencyIntegrationTest.java` sử dụng `ExecutorService`, `CountDownLatch` để giải phóng đồng thời nhiều luồng tại cùng một phần nghìn giây (millisecond):

| Test Case ID | Kịch bản Tranh chấp (Concurrency Scenario) | Cơ chế Xử lý của Hệ thống | Kết quả Kỳ vọng |
|---|---|---|---|
| **CONC-01** | 10 luồng đồng thời gọi `startAttempt` cho cùng 1 sinh viên trong kỳ thi chính thức (`MIDTERM`/`FINAL`). | Ràng buộc duy nhất `(exam_id, student_id, attempt_number)` trên DB + Bắt `DataIntegrityViolationException`. | **Duy nhất 1 luồng thành công**, 9 luồng còn lại nhận mã `409 Conflict`. Số bản ghi trong CSDL chính xác là 1. |
| **CONC-02** | 10 luồng đồng thời gửi lệnh nộp bài (`submitAttempt`) cho cùng một lượt làm bài (`attempt_id`). | Khóa bi quan **Pessimistic Lock** (`SELECT ... FOR UPDATE`) qua JPA `@Lock(LockModeType.PESSIMISTIC_WRITE)` tại `IExamAttemptRepository.findByIdWithLock`. | **Chỉ luồng đầu tiên tính điểm và chuyển trạng thái sang `GRADED`**; 9 luồng tiếp theo bị từ chối bằng `400 Bad Request` ("Attempt is already submitted"). Không xảy ra lỗi chấm đúp điểm. |
| **CONC-03** | 15 sinh viên độc lập cùng nhấn nộp bài thi tại đúng thời điểm hết giờ làm bài (Exam Deadline). | Transaction Isolation + Connection Pooling (HikariCP). | **100% (15/15) sinh viên nộp bài thành công**, không bị Deadlock hay Timeout giao dịch. |

---

## 2. Kịch Bản Kiểm Thử Tải Với k6 (Load Testing Suite)

File kịch bản chuẩn hóa: [`load_test_k6.js`](file:///d:/vatli1/hethongvatli1/load_test_k6.js).

### A. Mô Hình Tải (Load Profile Stages)
- **Giai đoạn 1 (0s - 30s):** Khởi động nhẹ nhàng với 20 Virtual Users (VUs).
- **Giai đoạn 2 (30s - 1m30s):** Tải thông thường ổn định ở mức 100 VUs.
- **Giai đoạn 3 (1m30s - 2m00s):** Giả lập giờ cao điểm nộp bài thi với **250 VUs** gửi request dồn dập.
- **Giai đoạn 4 (2m00s - 2m30s):** Giảm dần tải về 0 (Ramp-down / Cooldown).

### B. Ngưỡng Tiêu Chuẩn Nghiệm Thu (SLA Thresholds)
- `http_req_duration (p95) < 500ms`: 95% số lượng request hoàn thành trong thời gian dưới nửa giây.
- `http_req_duration (p99) < 1200ms`: 99% số lượng request hoàn thành dưới 1.2 giây trong thời điểm tải đỉnh.
- `http_req_failed { status: 500 } < 1%`: Tuyệt đối không xảy ra lỗi sập máy chủ 500 Internal Server Error.
- Các mã HTTP `429 Too Many Requests` được hệ thống trả về chính xác khi số lượng request vượt ngưỡng quy định (10 lượt/phút đối với signin, 3 lượt/phút đối với signup & forgot-password).

### C. Hướng Dẫn Thực Thi
1. Cài đặt công cụ k6 (nếu chưa có):
   ```powershell
   # Cài đặt qua Windows Package Manager
   winget install k6
   # Hoặc qua Chocolatey
   choco install k6
   ```
2. Khởi động ứng dụng Spring Boot:
   ```powershell
   cd d:\vatli1\hethongvatli1
   $env:JAVA_HOME = "E:\java"
   ..\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
   ```
3. Chạy kịch bản kiểm thử tải:
   ```powershell
   # Chạy trên máy cục bộ (Localhost)
   k6 run load_test_k6.js

   # Chạy trên máy chủ Staging / Kiểm thử từ xa
   k6 run -e BASE_URL=http://192.168.1.100:8080 load_test_k6.js
   ```

---

## 3. Kết Luận Đánh Giá Khả Năng Vận Hành Backend

1. **Khả năng chịu lỗi & Chống gian lận**: Việc kết hợp Database Pessimistic Lock ở các điểm nhạy cảm (bắt đầu thi, nộp bài) giải quyết triệt để rủi ro sinh viên mở nhiều tab trình duyệt để nộp bài nhiều lần nhằm gian lận điểm số.
2. **Khả năng mở rộng (Scalability)**: Hệ thống sử dụng cơ chế Stateless Token (JWT) cho phép mở rộng chiều ngang (Horizontal Pod Autoscaling) mượt mà mà không cần đồng bộ session bộ nhớ giữa các máy chủ.
