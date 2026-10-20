# TỔNG HỢP CẬP NHẬT BACKEND (CHANGELOG & FRONTEND INTEGRATION) — 20/10/2026

> **Ngày cập nhật:** 20/10/2026  
> **Repository:** `Thai-DM/vat_li_be` (`d:\vatli1\hethongvatli1`)  
> **Trạng thái:** Đã kiểm thử 100% passed, đã commit và push lên nhánh `main`.

---

## MỤC LỤC
1. [Tổng quan các thay đổi trong ngày](#1-tổng-quan-các-thay-đổi-trong-ngày)
2. [Chi tiết 1: Chức năng Ẩn / Hiện đề thi (`isPublished`)](#2-chi-tiết-1-chức-năng-ẩn--hiện-đề-thi-ispublished)
3. [Chi tiết 2: Quản lý tiến độ học tập chi tiết theo từng học liệu (`MaterialProgress`)](#3-chi-tiết-2-quản-lý-tiến-độ-học-tập-chi-tiết-theo-từng-học-liệu-materialprogress)
4. [Chi tiết 3: Dữ liệu Media & Phân quyền hình ảnh trong đề thi](#4-chi-tiết-3-dữ-liệu-media--phân-quyền-hình-ảnh-trong-đề-thi)
5. [Hướng dẫn tích hợp cho Frontend](#5-hướng-dẫn-tích-hợp-cho-frontend)
6. [Danh sách Commit & File thay đổi](#6-danh-sách-commit--file-thay-đổi)

---

## 1. TỔNG QUAN CÁC THAY ĐỔI TRONG NGÀY

Trong ngày 20/10/2026, Backend đã xử lý dứt điểm 2 khối tính năng quan trọng phục vụ kết nối Frontend:
- **Ẩn / Hiện đề thi (`isPublished`):** Hoàn thiện đồng bộ từ Entity, DTO, Service, Notification, API lọc theo phân quyền (Sinh viên vs Giảng viên) và lớp bảo vệ chặn làm bài thi chưa công bố.
- **Tiến độ học tập theo học liệu (`MaterialProgress`):** Cho phép theo dõi chi tiết trạng thái từng tài liệu/video học liệu (thay vì chỉ lưu chung chung theo Topic), tự động rollup % hoàn thành của Topic và trả về danh sách `completedMaterialIds` cho FE.

---

## 2. CHI TIẾT 1: CHỨC NĂNG ẨN / HIỆN ĐỀ THI (`isPublished`)

### A. Vấn đề trước khi sửa
- `UpdateExamDTO` có khai báo trường `isPublished`, nhưng `ExamServiceImpl.updateExam()` bỏ qua không lưu.
- Entity `Exam` và `ExamDTO` trả về FE không có trường `isPublished`, khiến FE nhận `null` / `undefined` (giao diện hiển thị *"Chưa có thông tin"* và checkbox *"Hiển thị đề"* không hoạt động).
- API lấy danh sách đề không lọc theo trạng thái công bố, khiến sinh viên nhìn thấy cả các đề thi đang soạn thảo / đề nháp.

### B. Giải pháp kỹ thuật đã triển khai
1. **Entity & DTOs:**
   - [`Exam.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/entity/Exam.java): Bổ sung mapping với cột có sẵn trong DB:
     ```java
     @Column(name = "is_published")
     @Builder.Default
     private Boolean isPublished = true;
     ```
   - [`ExamDTO.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/model/dto/ExamDTO.java): Thêm trường `private Boolean isPublished;`.
   - [`CreateExamDTO.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/model/request/CreateExamDTO.java): Thêm `private Boolean isPublished;` (mặc định `true` nếu client không gửi).
2. **Logic Service ([`ExamServiceImpl.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/service/impl/ExamServiceImpl.java)):**
   - **Tạo đề (`createExam`):** Lưu giá trị `isPublished`. Nếu tạo đề ở chế độ ẩn (`isPublished = false`), hệ thống **không** bắn thông báo tới sinh viên trong lớp.
   - **Chỉnh sửa đề (`updateExam`):** Cập nhật `isPublished`. Nếu đề chuyển trạng thái từ ẩn sang hiện (`false` -> `true`), hệ thống tự động kích hoạt thông báo cho sinh viên: *"Lớp học vừa công bố bài thi: ..."*.
   - **Lọc danh sách đề thi (`getExamsByClass`, `getAllExams`):**
     - **Sinh viên:** Chỉ xem được đề thi đã công bố (`isPublished = true`).
     - **Giảng viên / Quản trị viên:** Xem toàn bộ đề (kèm boolean `isPublished` chính xác) để phục vụ quản lý và bật/tắt checkbox.
   - **Bảo vệ làm bài thi (`startAttempt`):** Nếu sinh viên cố tình truy cập link làm bài của đề chưa công bố, hệ thống chặn lại với lỗi `403 FORBIDDEN`: *"Kỳ thi này hiện chưa được công bố"*.
   - **Student Agenda ([`StudentPortalServiceImpl.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/service/impl/StudentPortalServiceImpl.java)):** Loại bỏ đề ẩn khỏi lịch thi của sinh viên.

---

## 3. CHI TIẾT 2: QUẢN LÝ TIẾN ĐỘ HỌC TẬP CHI TIẾT THEO TỪNG HỌC LIỆU (`MaterialProgress`)

### A. Vấn đề trước khi sửa
- BE trước đây chỉ lưu tiến độ chung theo Topic (`LearningProgress`) với các trường: `studentId`, `classId`, `topicId`, `progressPercent`.
- Không có bảng nào lưu trữ việc sinh viên đã đọc học liệu PDF nào, xem video nào. FE không thể tick xanh học liệu đã xem và không thể khôi phục trạng thái khi sinh viên F5 tải lại trang.

### B. Giải pháp kỹ thuật đã triển khai
1. **Cơ sở dữ liệu ([`init.sql`](file:///d:/vatli1/hethongvatli1/init.sql)):**
   - Tạo bảng `material_progress`:
     ```sql
     CREATE TABLE IF NOT EXISTS material_progress (
         progress_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
         student_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
         class_id UUID NOT NULL REFERENCES classes(class_id) ON DELETE CASCADE,
         material_id UUID NOT NULL REFERENCES materials(material_id) ON DELETE CASCADE,
         status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
         progress_percent INT NOT NULL DEFAULT 0,
         completed_at TIMESTAMP WITH TIME ZONE,
         last_accessed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
         CONSTRAINT uq_student_class_material UNIQUE (student_id, class_id, material_id)
     );
     ```
2. **Entity & Repository:**
   - [`MaterialProgress.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/entity/MaterialProgress.java)
   - [`MaterialProgressRepository.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/repository/MaterialProgressRepository.java)
3. **API & Logic Rollup tiến độ ([`LearningProgressServiceImpl.java`](file:///d:/vatli1/hethongvatli1/src/main/java/com/vatly1/example/service/impl/LearningProgressServiceImpl.java)):**
   - **API cập nhật học liệu:** `POST /api/student/progress/material`
     - Nhận `materialId`, `classId`, `status` (`COMPLETED`, `IN_PROGRESS`, `NOT_STARTED`), `progressPercent`.
     - Lưu tiến độ học liệu chi tiết và cập nhật `completedAt`.
     - **Tự động tính toán lại Topic:** Đếm tổng số học liệu trong Topic và số học liệu đã `COMPLETED` để tự động tính `% tiến độ Topic` tương ứng trong bảng `learning_progress`.
   - **API lấy tiến độ lớp học:** `GET /api/student/progress/class/{classId}`
     - Trả về danh sách `LearningProgressDTO` của các Topic.
     - Mỗi Topic bổ sung thêm mảng `completedMaterialIds: List<UUID>` chứa toàn bộ ID các học liệu mà sinh viên đó đã hoàn thành trong Topic.

---

## 4. CHI TIẾT 3: DỮ LIỆU MEDIA & PHÂN QUYỀN HÌNH ẢNH TRONG ĐỀ THI

- **DTO câu hỏi:** Bổ sung trường `mediaUrl` vào `ExamQuestionDetailDTO` và `StudentExamQuestionDTO`. Service tự động map URL hình ảnh từ ngân hàng câu hỏi vào bài thi.
- **Phân quyền File (`FileController.verifyFileAccessPermission`):** Cho phép truy cập file ảnh nếu người dùng có quyền xem câu hỏi hoặc đề thi tương ứng (thay vì chỉ người upload), sửa triệt để lỗi `403 Forbidden` khi sinh viên làm bài thi có hình ảnh minh họa hoặc giảng viên khác mở đề thi chung.

---

## 5. HƯỚNG DẪN TÍCH HỢP CHO FRONTEND

### 5.1. Tích hợp Ẩn / Hiện đề thi
1. **Hiển thị danh sách đề:**
   - Đọc trường `isPublished` (boolean: `true` / `false`) từ `exam` object.
   - Nếu `isPublished === true`: hiển thị nhãn *"Đã công bố"* hoặc badge màu xanh.
   - Nếu `isPublished === false`: hiển thị nhãn *"Chưa công bố (Ẩn)"* hoặc badge màu vàng/xám.
2. **Checkbox "Hiển thị đề" (Giảng viên):**
   - Khi tạo đề (`POST /api/exams`): Gửi payload kèm `{ ..., "isPublished": true/false }`.
   - Khi chỉnh sửa đề (`PUT /api/exams/{id}`): Gửi `{ ..., "isPublished": true/false }`.
   - Checkbox binding: `v-model="form.isPublished"` hoặc `checked={exam.isPublished}`.
3. **Xử lý phía sinh viên:**
   - API `GET /api/exams/class/{classId}` tự động chỉ trả về đề `isPublished = true`, FE không cần filter thủ công.
   - Nếu sinh viên vào link trực tiếp của đề ẩn, BE trả mã lỗi `403` với message `"Kỳ thi này hiện chưa được công bố"`, FE chỉ cần hiển thị thông báo toast/alert tương ứng.

### 5.2. Tích hợp Đánh dấu hoàn thành học liệu
1. **Lấy danh sách học liệu đã học:**
   - Gọi `GET /api/student/progress/class/{classId}`.
   - Trong mỗi topic, kiểm tra:
     ```typescript
     const isMaterialDone = topicProgress.completedMaterialIds?.includes(material.materialId);
     ```
   - Render icon tick xanh nếu `isMaterialDone === true`.
2. **Cập nhật khi sinh viên xem/học xong tài liệu:**
   - Gửi request:
     ```http
     POST /api/student/progress/material
     Content-Type: application/json
     Authorization: Bearer <token>

     {
       "materialId": "UUID_CỦA_HỌC_LIỆU",
       "classId": "UUID_CỦA_LỚP_HỌC",
       "status": "COMPLETED",
       "progressPercent": 100
     }
     ```
   - Response trả về tiến độ Topic mới đã được tự động tính toán lại.

---

## 6. DANH SÁCH COMMIT & FILE THAY ĐỔI

| Commit | Thông điệp | Nội dung chính |
|---|---|---|
| `89529b9` | `fix(exam): implement isPublished visibility toggle, filtering, and attempt protection` | Thêm `isPublished` vào Exam, ExamDTO, CreateExamDTO, lọc danh sách đề thi theo quyền, chặn sinh viên làm đề ẩn, thêm test suite `ExamPublishStatusTest`. |
| `21ae37b` | `feat(progress): implement material-level learning progress tracking and topic rollup` | Tạo bảng & entity `material_progress`, API cập nhật tiến độ học liệu, tự động tính % Topic cha, thêm test suite `MaterialLearningProgressTest`. |

### Danh sách các tệp thay đổi chính:
- `src/main/java/com/vatly1/example/entity/Exam.java`
- `src/main/java/com/vatly1/example/model/dto/ExamDTO.java`
- `src/main/java/com/vatly1/example/model/request/CreateExamDTO.java`
- `src/main/java/com/vatly1/example/repository/IExamRepository.java`
- `src/main/java/com/vatly1/example/service/impl/ExamServiceImpl.java`
- `src/main/java/com/vatly1/example/service/impl/StudentPortalServiceImpl.java`
- `src/main/java/com/vatly1/example/entity/MaterialProgress.java`
- `src/main/java/com/vatly1/example/repository/MaterialProgressRepository.java`
- `src/main/java/com/vatly1/example/model/dto/MaterialProgressDTO.java`
- `src/main/java/com/vatly1/example/model/request/UpdateMaterialProgressDTO.java`
- `src/main/java/com/vatly1/example/controller/StudentProgressController.java`
- `src/main/java/com/vatly1/example/service/impl/LearningProgressServiceImpl.java`
- `init.sql`
- `src/test/java/com/vatly1/example/service/ExamPublishStatusTest.java`
- `src/test/java/com/vatly1/example/service/MaterialLearningProgressTest.java`
