# Kế hoạch Triển khai Backend — Hệ thống Học phần Vật lý 1
**Ngôn ngữ:** Java | **Framework:** Spring Boot 3.x | **CSDL:** PostgreSQL 14+ (Neon cloud)

---

## 1. Mục tiêu Hệ thống (Từ tai_lieu_co_ban.txt)

| Mục tiêu | Trạng thái |
|---|---|
| Quản lý tài khoản, lớp học (IAM) | ✅ Hoàn thành |
| Học liệu, phê duyệt theo quy trình Bộ môn | ✅ Hoàn thành |
| Chuẩn hóa ngân hàng câu hỏi, ma trận đề, làm bài MCQ | ✅ Hoàn thành |
| 04 bài thí nghiệm ảo — giao bài, nộp, chấm rubric, xác nhận | ✅ Hoàn thành |
| Trợ giảng Socratic AI — lưu phiên, citation, từ chối | ✅ Hoàn thành |
| Bảng điều khiển tiến độ, minh chứng, nhật ký | ✅ Hoàn thành (Sprint 5) |
| Dữ liệu phân tích — nội dung khó, chất lượng câu hỏi | ✅ Hoàn thành (Sprint 6) |
| Cấu hình hệ thống, production readiness | 🔲 Sprint 7 |

---

## 2. Tech Stack Backend

| Layer | Công nghệ | Phiên bản |
|---|---|---|
| **Framework** | Spring Boot | 3.3.x |
| **ORM** | Spring Data JPA + Hibernate | 6.x |
| **Bảo mật** | Spring Security + JWT (jjwt) | 0.12.x |
| **CSDL** | PostgreSQL | 14+ (Neon) |
| **Test CSDL** | H2 In-Memory | - |
| **Validation** | Jakarta Validation | 8.x |
| **API Docs** | Springdoc OpenAPI (Swagger UI) | 2.x |
| **Testing** | JUnit 5 + MockMvc | - |
| **Build** | Maven | 3.9+ |

---

## 3. Cấu trúc Thư mục Hiện tại

```
D:\vatli1\hethongvatli1\
├── src\main\java\com\vatly1\example\
│   ├── controller\                          (13 controller)
│   │   ├── AiTutorController.java
│   │   ├── ClassController.java
│   │   ├── ExamController.java
│   │   ├── ExperimentController.java
│   │   ├── LearningMaterialController.java
│   │   ├── LearningProgressController.java
│   │   ├── QuestionBankController.java
│   │   ├── SemesterController.java
│   │   ├── StudentClassController.java
│   │   ├── StudentProgressController.java
│   │   ├── SubjectController.java
│   │   ├── TopicController.java
│   │   └── UserController.java
│   ├── entity\                              (42 JPA entity, 18 enum)
│   ├── service\impl\                        (16 service)
│   │   ├── AiTutorServiceImpl.java
│   │   ├── ClassEnrollmentServiceImpl.java
│   │   ├── ClassServiceImpl.java
│   │   ├── ClassStaffServiceImpl.java
│   │   ├── ExamServiceImpl.java
│   │   ├── ExperimentServiceImpl.java
│   │   ├── FileStorageServiceImpl.java
│   │   ├── LearningMaterialServiceImpl.java
│   │   ├── LearningProgressServiceImpl.java
│   │   ├── PdfQuestionParserServiceImpl.java
│   │   ├── QuestionBankServiceImpl.java
│   │   ├── RefreshTokenServiceImpl.java
│   │   ├── SemesterServiceImpl.java
│   │   ├── SubjectServiceImpl.java
│   │   ├── TopicServiceImpl.java
│   │   └── UserServiceImpl.java
│   └── repository\, security\, filter\, utils\, dto\, exception\...
├── src\test\java\com\vatly1\example\controller\   (13 test class, 110 test cases)
│   ├── IdorSecurityControllerTest.java      (12 IDOR cross-user tests)
│   └── ...
└── pom.xml
```

---

## 4. Database Schema

File: `D:\vatli1\schema_vatly1.sql` — **42 bảng**, UUID PK, TIMESTAMPTZ.

**Constraint đã áp dụng trên Neon:**
- `UNIQUE INDEX idx_one_current_semester` — chỉ 1 học kỳ `is_current=true` (atomic UPDATE)
- `UNIQUE(submission_id)` trên `experiment_confirmations` — chống double-confirm
- Magic bytes check (MZ/PE, ELF) trên file upload

---

## 5. Lộ trình Phát triển

### ✅ Sprint 0 — Auth Module

- [x] `POST /api/v1/users/signin` — Đăng nhập
- [x] `POST /api/v1/users/signup` — Đăng ký (role STUDENT ép cứng)
- [x] `POST /api/v1/users/admin/create-user` — Admin tạo tài khoản
- [x] `GET /api/v1/users/me` — Lấy thông tin tài khoản
- [x] `POST /api/v1/users/refresh` — Refresh Token Rotation + Reuse Detection
- [x] `POST /api/v1/users/logout` — Thu hồi refresh token
- [x] `GET /api/v1/users/{username}` — Tìm user (Admin)
- [x] `DELETE /api/v1/users/{username}` — Xóa user (Admin)
- [x] JWT HMAC-SHA256, BCrypt cost=12, token hash SHA-256 lưu DB
- [x] 12 test cases — 100% PASS

---

### ✅ Sprint 1 — Môn học, Học kỳ, Lớp học

**Môn học:**
- [x] `GET /api/v1/subjects` — Danh sách
- [x] `POST /api/v1/subjects` — Tạo (Admin)
- [x] `GET /api/v1/subjects/{id}` — Chi tiết
- [x] `PUT /api/v1/subjects/{id}` — Cập nhật (Admin)

**Học kỳ:**
- [x] `GET /api/v1/semesters` — Danh sách
- [x] `POST /api/v1/semesters` — Tạo (Admin)
- [x] `PUT /api/v1/semesters/{id}` — Cập nhật
- [x] `PUT /api/v1/semesters/{id}/set-current` — Atomic DB update (không JVM lock)

**Lớp học & Phân công:**
- [x] `GET /api/v1/classes` — Danh sách lớp
- [x] `POST /api/v1/classes` — Tạo lớp (Admin/Instructor)
- [x] `PUT /api/v1/classes/{id}` — Cập nhật
- [x] `PUT /api/v1/classes/{id}/status` — Chuyển trạng thái (draft→active→completed)
- [x] `GET/POST /api/v1/classes/{id}/staff` — Quản lý GV/TA phụ trách
- [x] `DELETE /api/v1/classes/{id}/staff/{userId}` — Huỷ phân công
- [x] `GET /api/v1/classes/{id}/students` — Danh sách SV
- [x] `POST /api/v1/classes/{id}/enroll` — Ghi danh SV
- [x] `DELETE /api/v1/classes/{id}/students/{studentId}` — Xoá SV
- [x] `GET /api/v1/students/me/classes` — SV xem lớp của mình
- [x] IDOR 2 chiều (SV A không truy cập ghi danh của SV B)

---

### ✅ Sprint 2 — Học liệu & Ngân hàng Câu hỏi

**Chủ đề:**
- [x] `GET/POST /api/v1/subjects/{id}/topics` — Danh sách, tạo chương
- [x] `PUT/DELETE /api/v1/topics/{id}` — Cập nhật, xoá

**Học liệu:**
- [x] `GET/POST /api/v1/topics/{id}/materials` — Danh sách, tạo
- [x] `GET/PUT/DELETE /api/v1/materials/{id}` — Chi tiết, cập nhật, xoá
- [x] `POST /api/v1/materials/{id}/submit-approval` — Gửi duyệt
- [x] `POST /api/v1/materials/{id}/approve` — Phê duyệt (Instructor/Admin)
- [x] `POST /api/v1/materials/{id}/reject` — Từ chối + lý do

**File Upload:**
- [x] `POST /api/v1/files/upload` — Upload + magic bytes validation
- [x] `GET /api/v1/files/{id}/status` — Poll trạng thái

**Ngân hàng câu hỏi:**
- [x] `GET/POST /api/v1/questions` — Danh sách, tạo (MCQ đúng 1 đáp án đúng)
- [x] `GET/PUT/DELETE /api/v1/questions/{id}` — Chi tiết, cập nhật, xoá
- [x] `POST /api/v1/questions/{id}/approve` — Phê duyệt
- [x] `POST /api/v1/questions/{id}/reject` — Từ chối
- [x] `POST /api/v1/questions/import` — Import batch từ PDF
- [x] `POST/PUT/DELETE /api/v1/questions/{id}/options/{optionId}` — Quản lý đáp án

---

### ✅ Sprint 3 — Kiểm tra Trắc nghiệm

- [x] `GET/POST /api/v1/subjects/{id}/exam-matrices` — Quản lý ma trận đề
- [x] `GET/POST /api/v1/classes/{id}/exams` — Danh sách, tạo đề
- [x] `POST /api/v1/exams/generate` — Sinh đề tự động từ ma trận
- [x] `GET/PUT /api/v1/exams/{id}` — Chi tiết, cập nhật
- [x] `POST /api/v1/exams/{id}/start` — SV bắt đầu làm + deadline check
- [x] `POST /api/v1/attempts/{id}/answer` — SV lưu đáp án từng câu
- [x] `POST /api/v1/attempts/{id}/submit` — Nộp bài + auto-grade MCQ
- [x] `GET /api/v1/attempts/{id}/result` — Xem kết quả chi tiết
- [x] `GET /api/v1/exams/{id}/attempts` — GV xem kết quả cả lớp
- [x] `GET /api/v1/students/me/attempts` — SV xem lịch sử
- [x] IDOR đọc + ghi (SV B không gửi đáp án vào attemptId của SV A)
- [x] Deadline enforcement 2 đầu (start + submit)
- [x] PESSIMISTIC_WRITE lock chống double-submit

---

### ✅ Sprint 4 — Thí nghiệm ảo & AI Tutor Socratic

**Thí nghiệm ảo:**
- [x] `GET/POST /api/v1/subjects/{id}/experiments` — Danh sách 04 bài lab
- [x] `GET/PUT /api/v1/experiments/{id}` — Chi tiết, cập nhật
- [x] `GET/POST/PUT/DELETE /api/v1/experiments/{id}/rubrics` — Quản lý rubric
- [x] `POST /api/v1/classes/{id}/experiment-assignments` — GV giao bài
- [x] `GET /api/v1/classes/{id}/experiment-assignments` — GV xem bài đã giao
- [x] `GET /api/v1/students/me/experiment-assignments` — SV xem bài được giao
- [x] `POST /api/v1/experiment-assignments/{id}/submit` — SV nộp minh chứng
- [x] `GET /api/v1/experiment-assignments/{id}/submissions` — GV xem bài nộp
- [x] `POST /api/v1/submissions/{id}/grade` — TA/GV chấm theo rubric
- [x] `GET /api/v1/submissions/{id}/scores` — Xem điểm từng tiêu chí
- [x] `POST /api/v1/submissions/{id}/confirm` — GV xác nhận khóa điểm

**AI Tutor Socratic:**
- [x] `POST /api/v1/ai/conversations` — Tạo phiên chat (class_id, topic_id, mode)
- [x] `GET /api/v1/ai/conversations/mine` — Lịch sử phiên chat
- [x] `GET /api/v1/ai/conversations/{id}` — Chi tiết phiên
- [x] `GET /api/v1/ai/conversations/{id}/messages` — Danh sách tin nhắn
- [x] `POST /api/v1/ai/conversations/{id}/messages` — SV gửi tin nhắn
- [x] `PUT /api/v1/ai/conversations/{id}/end` — Kết thúc phiên
- [x] `POST /api/v1/ai/messages/{id}/feedback` — SV đánh giá AI (1–5 sao)
- [x] `GET /api/v1/classes/{id}/ai-conversations` — GV xem phiên chat cả lớp
- [x] IDOR: SV B không đọc/ghi phiên chat của SV A

**Tổng test: 110/110 — 100% BUILD SUCCESS**

---

### ✅ Sprint 5 — Tiến độ, Dashboard & User Profile *(Hoàn thành)*
> Mục tiêu: GV có bảng điều khiển tổng hợp; SV quản lý hồ sơ cá nhân; hệ thống tự ghi nhật ký hoạt động.
> Kết quả: 147/147 test cases PASS.

**AOP — Ghi nhật ký tự động:**
- [x] `ActivityLogAspect` (@AfterReturning) — ghi `activity_logs` khi viewMaterial, startExam, submitExam, submitLab, openAiChat
- [x] `AuditLogAspect` (@AfterReturning) — ghi `audit_logs` khi changeRole, approveMaterial, confirmLab, deleteAny

**Learning Progress:**
- [x] `GET /api/v1/classes/{id}/progress` — Tiến độ toàn lớp (Instructor/TA)
- [x] `GET /api/v1/students/me/progress` — SV xem tiến độ cá nhân
- [x] `GET /api/v1/students/{id}/progress` — GV xem tiến độ một SV

**Activity & Audit Logs:**
- [x] `GET /api/v1/classes/{id}/activity-logs` — Nhật ký cả lớp (Instructor/Admin)
- [x] `GET /api/v1/students/me/activity-logs` — SV xem nhật ký của mình
- [x] `GET /api/v1/admin/activity-logs` — Admin xem toàn bộ
- [x] `GET /api/v1/admin/audit-logs` — Nhật ký kiểm toán

**Evidence Repository:**
- [x] `GET /api/v1/students/me/evidence` — SV xem hồ sơ minh chứng
- [x] `GET /api/v1/students/{id}/evidence` — GV xem hồ sơ một SV
- [x] `GET /api/v1/classes/{id}/evidence` — GV xem hồ sơ cả lớp

**Dashboard:**
- [x] `GET /api/v1/dashboard/class/{id}` — Dashboard tổng hợp lớp
- [x] `GET /api/v1/dashboard/student/{id}` — Dashboard cá nhân một SV (Instructor xem)
- [x] `GET /api/v1/dashboard/me` — Dashboard cá nhân của SV hiện tại
- [x] `POST /api/v1/dashboard/class/{id}/regenerate` — Sinh lại snapshot (Admin)

**User Profile & Admin Management:**
- [x] `GET /api/v1/users/me/profile` — Xem hồ sơ cá nhân
- [x] `PUT /api/v1/users/me/profile` — Cập nhật hồ sơ
- [x] `GET /api/v1/users/{id}/profile` — Xem hồ sơ người khác (Instructor/Admin)
- [x] `PUT /api/v1/users/me` — Cập nhật username, email
- [x] `PUT /api/v1/users/me/password` — Đổi mật khẩu
- [x] `GET /api/v1/admin/users` — Danh sách tất cả user (phân trang, filter)
- [x] `PUT /api/v1/admin/users/{id}/status` — Khóa / Mở khóa tài khoản

---

### ✅ Sprint 6 — Dữ liệu Phân tích & Cron Jobs *(Hoàn thành)*
> Mục tiêu: Dữ liệu tổng hợp để GV phát hiện nội dung khó, cải tiến học liệu.
> Kết quả: 164/164 test cases PASS (17 test cases mới cho Analytics & Aggregation, bao gồm xác minh toán học DI chuẩn CTT, xử lý biên sĩ số nhỏ < 4, và kiểm chứng idempotent đa kỳ).

**Analytics Endpoints:**
- [x] `GET /api/v1/analytics/topic-difficulty` — Nội dung khó (avg_score, error_rate)
- [x] `GET /api/v1/analytics/question-quality` — Chất lượng câu hỏi (correct_rate, discrimination_index)
- [x] `GET /api/v1/analytics/ai-gaps` — Lỗ hổng học liệu AI hay từ chối
- [x] `GET /api/v1/analytics/material-effectiveness` — Hiệu quả học liệu
- [x] `POST /api/v1/analytics/trigger` — Kích hoạt tổng hợp thủ công (Admin only)

**Cron Aggregation Job (01:00 sáng mỗi ngày):**
- [x] Tính `topic_difficulty_stats` — JOIN exam_answers + question_bank
- [x] Tính `question_stats` — correct_rate, discrimination_index (top27% vs bottom27%)
- [x] Tổng hợp `ai_topic_gap_stats` — GROUP BY topic_id từ ai_refusals
- [x] Tính `material_effectiveness_stats` — JOIN activity_logs + exam_attempts
- [x] `AnalyticsCronJob` — Lên lịch chạy định kỳ `@Scheduled(cron = "0 0 1 * * *")`
- [x] `SchedulingConfig` — Cấu hình `@EnableScheduling` có thể bật/tắt linh hoạt theo profile

---

### 🔲 Sprint 7 — System Settings & Production Readiness *(~1 tuần)*

**System Settings:**
- [ ] `GET /api/v1/admin/settings` — Xem toàn bộ cấu hình
- [ ] `GET /api/v1/admin/settings/{key}` — Xem một cấu hình
- [ ] `PUT /api/v1/admin/settings/{key}` — Cập nhật (JSONB)

**Production readiness:**
- [ ] `docker-compose.yml` — PostgreSQL, MinIO
- [ ] `application-prod.yml` — cấu hình từ biến môi trường
- [ ] CORS — đổi `allowedOrigins = [<domain frontend>]`
- [ ] README — hướng dẫn cài đặt đầy đủ

---

## 6. Quyết định Kỹ thuật Quan trọng

> **Atomic Semester Lock:** Dùng `UPDATE ... SET is_current = CASE WHEN semester_id = :id THEN true ELSE false END` — không dùng `synchronized` (sai khi scale multi-pod).

> **Chống Double-Confirm:** `UNIQUE(submission_id)` trên `experiment_confirmations` + bắt `DataIntegrityViolationException` → 409 Conflict.

> **IDOR Pattern bắt buộc:** Mọi thao tác đọc/ghi phải verify `ownerId == currentUserId` tại tầng Service.

> **ENUM case-sensitivity:** PostgreSQL ENUM chữ thường (`student`, `instructor`). Java Enum chữ HOA. Cần `@Enumerated(EnumType.STRING)`.

> **JWT Secret Key Production:** Truyền qua biến môi trường `SECURITY_JWT_TOKEN_SECRET_KEY` — không hardcode, không commit Git.
