# Hệ Thống Quản Lý Học Tập & Thí Nghiệm Ảo Vật Lý 1 (Backend)

[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%20%7C%2024-orange.svg?style=flat&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen.svg?style=flat&logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-6-green.svg?style=flat&logo=springsecurity)](https://spring.io/projects/spring-security)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%7C%20Neon-336791.svg?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![MinIO](https://img.shields.io/badge/MinIO-Object%20Storage%20(S3)-c72c48.svg?style=flat&logo=minio)](https://min.io/)
[![Tests](https://img.shields.io/badge/Tests-194%2F194%20Passing%20(100%25)-success.svg?style=flat&logo=checkmarx)](https://github.com/Thai-DM/vat_li_be)
[![OpenAPI](https://img.shields.io/badge/Swagger-OpenAPI%203.0-yellow.svg?style=flat&logo=swagger)](http://localhost:8080/swagger-ui/index.html)

Backend RESTful API cho **Hệ Thống Quản Lý Học Tập & Thí Nghiệm Ảo môn Vật Lý 1**, phục vụ toàn diện công tác giảng dạy lý thuyết, ngân hàng câu hỏi, tổ chức thi trắc nghiệm trực tuyến, quản lý thí nghiệm ảo và theo dõi tiến độ học tập có tích hợp **Trợ lý AI (AI Tutor)** và **Lưu trữ đối tượng phân tán MinIO**.

---

## 📌 Mục Lục

1. [Tính Năng Nổi Bật](#-tính-năng-nổi-bật)
2. [Ngăn Xếp Công Nghệ (Tech Stack)](#-ngăn-xếp-công-nghệ-tech-stack)
3. [Kiến Trúc & Cấu Trúc Mã Nguồn](#-kiến-trúc--cấu-trúc-mã-nguồn)
4. [Các Phân Hệ Nghiệp Vụ](#-các-phân-hệ-nghiệp-vụ)
5. [Cài Đặt & Khởi Chạy](#-cài-đặt--khởi-chạy)
6. [Tài Liệu API (Swagger UI)](#-tài-liệu-api-swagger-ui)
7. [Kiểm Thử & Đảm Bảo Chất Lượng](#-kiểm-thử--đảm-bảo-chất-lượng)

---

## 🚀 Tính Năng Nổi Bật

- **Bảo mật chuẩn Doanh nghiệp (RBAC & JWT Rotation):**
  - Cơ chế Access Token (stateless) kết hợp Refresh Token (stateful, có thể thu hồi, xoay vòng tự động - Token Rotation).
  - Tự động phát hiện hành vi tái sử dụng Refresh Token bị xâm nhập (Token Reuse Detection) và lập tức vô hiệu hóa toàn bộ phiên của người dùng.
  - Phân quyền theo vai trò chặt chẽ: `ADMIN`, `LECTURER` (Giảng viên/Trợ giảng), `STUDENT` (Sinh viên).
  - Chống truy cập trái phép ngang quyền (IDOR Protection) trên bài thi, điểm số và minh chứng thí nghiệm.
- **Lưu trữ Đối tượng MinIO (S3-Compatible):**
  - Tích hợp MinIO Object Storage để lưu trữ tập trung hình ảnh câu hỏi, bài thí nghiệm, minh chứng báo cáo, tài liệu học tập và ảnh đại diện người dùng.
  - Hỗ trợ cơ chế **Smart Local Fallback** tự động chuyển sang lưu trữ cục bộ khi môi trường kiểm thử không có MinIO.
  - Endpoint truyền phát (streaming) tệp công khai `/api/v1/files/**` với chuẩn MediaType (`image/png`, `image/jpeg`, `application/pdf`, `.xlsx`, ...) và cache HTTP.
- **Ngân hàng Câu hỏi & Import Tự Động bằng Excel:**
  - Hỗ trợ tạo câu hỏi đơn lẻ và tính năng **Import hàng loạt từ tệp Excel (.xlsx)** thông qua Apache POI.
  - Hỗ trợ tải tệp Excel mẫu chuẩn hóa (`/api/v1/questions/import/template`).
  - Tự động kiểm tra tính hợp lệ dữ liệu, phân loại độ khó, phát hiện đáp án đúng/sai và gắn tag chủ đề.
- **Tổ chức Thi Trắc Nghiệm Trực Tuyến & Chống Gian Lận:**
  - Tạo đề thi linh hoạt theo ma trận kiến thức (tỷ lệ câu Dễ, Trung bình, Khó).
  - Tự động xáo trộn ngẫu nhiên thứ tự câu hỏi và đáp án cho từng sinh viên tham gia.
  - Chấm điểm tự động tức thì ngay khi nộp bài; giới hạn số lần làm bài và kiểm soát thời gian làm bài nghiêm ngặt.
  - Bảo vệ chống gian lận nộp bài đồng thời (Concurrency Lock Protection) đảm bảo tính toàn vẹn điểm số.
- **Quản Lý Thí Nghiệm Ảo & Báo Cáo Thực Hành:**
  - Giảng viên giao bài tập thí nghiệm vật lý, thiết lập hạn nộp và tiêu chí chấm.
  - Sinh viên làm bài, nộp số liệu đo đạc kèm ảnh/tệp minh chứng (lưu trực tiếp lên MinIO).
  - Giảng viên/Trợ giảng chấm điểm trực tiếp, nhận xét và gửi phản hồi cho sinh viên.
- **Trợ Lý Học Tập AI (AI Physics Tutor):**
  - Chat tương tác hỏi - đáp giải thích hiện tượng và bài tập môn Vật lý 1 theo ngữ cảnh.
  - Tự động ghi nhận lịch sử hội thoại và thu thập đánh giá chất lượng câu trả lời (Rating & Comment).
  - Tự động phát hiện các chủ đề kiến thức mà sinh viên còn yếu (Topic Gap Detection).
- **Phân Tích Thống Kê & Báo Cáo Tự Động (Analytics & Dashboards):**
  - Cron Job tự động tổng hợp dữ liệu định kỳ: độ khó chủ đề học tập, hiệu quả của tài liệu.
  - Bảng thống kê trực quan (Dashboard) cho Giảng viên và Quản trị viên theo dõi tiến độ cả lớp.
- **Chống Nghẽn Mạng & Nhật Ký Kiểm Toán (Rate Limiting & Audit Logging):**
  - Tích hợp bộ lọc giới hạn tần suất gọi API (In-Memory Sliding Window Rate Limiting bằng ConcurrentHashMap) bảo vệ các endpoint xác thực (signin, signup, forgot password) chống tấn công brute-force.
  - Ghi nhận đầy đủ Activity Log và Security Audit Log phục vụ truy vết.

---

## 🛠 Ngăn Xếp Công Nghệ (Tech Stack)

| Thành phần | Công nghệ / Thư viện | Mục đích sử dụng |
| :--- | :--- | :--- |
| **Ngôn ngữ** | Java 17 / Java 21 / Java 24 | Môi trường chạy backend chính |
| **Framework** | Spring Boot 3.4.3 | Khung ứng dụng backend RESTful API |
| **Bảo mật** | Spring Security 6, JJWT 0.11.5 | Xác thực, phân quyền RBAC, mã hóa JWT |
| **Cơ sở dữ liệu** | PostgreSQL 16 / Neon Cloud, Spring Data JPA | Quản lý dữ liệu quan hệ, ORM |
| **Rate Limiting** | In-Memory Sliding Window (ConcurrentHashMap) | Giới hạn tần suất request chống brute-force |
| **Lưu trữ đối tượng** | MinIO Java SDK 8.5.7 | Lưu trữ tệp tin, ảnh, tài liệu theo chuẩn S3 |
| **Xử lý tệp Excel** | Apache POI 5.2.5 (poi-ooxml) | Import ngân hàng câu hỏi & xuất template Excel |
| **Tài liệu API** | Springdoc OpenAPI 2.7.0 (Swagger UI) | Tự động sinh tài liệu API trực quan tương tác |
| **Tiện ích mã** | Project Lombok | Giảm thiểu boilerplate code (Getter, Setter, Builder) |
| **Container** | Docker & Docker Compose | Đóng gói và chạy môi trường PostgreSQL và MinIO |
| **Kiểm thử** | JUnit 5, Mockito, Spring Boot Test, H2/PostgreSQL Test | 194 ca kiểm thử tích hợp và đơn vị tự động |

---

## 📁 Kiến Trúc & Cấu Trúc Mã Nguồn

Dự án áp dụng mô hình kiến trúc phân tầng chuẩn hoá (**Controller - Service - Repository - Entity - Model**). Toàn bộ các đối tượng dữ liệu truyền tải được gom vào package trung tâm **`com.vatly1.example.model`**, phân tách thành đúng 3 subpackage chuyên biệt:

### 1. Phân chia 3 Subpackage trong `model`:
- **`com.vatly1.example.model.request`** (38 Request Models):
  - Chuyên phục vụ tiếp nhận payload đầu vào từ Client cho các thao tác Tạo mới (`Create*`), Cập nhật (`Update*`), Xác thực (`Signin*`, `Refresh*`, `Reset*`), Nộp bài (`Submit*`), Phân quyền (`Assign*`, `Enroll*`), Cấu hình (`*Setting*`).
- **`com.vatly1.example.model.response`** (7 Response Models):
  - Chuyên phục vụ cấu trúc phản hồi chuẩn đầu ra cho Client:
    - `ApiResponse`: Standard API Wrapper response (`status`, `message`, `data`, `timestamp`).
    - `AuthResponseDTO`: Payload phản hồi đăng nhập / xác thực JWT token.
    - `DashboardDataDTO`, `DashboardSnapshotDTO`: Dữ liệu thống kê tổng hợp dashboard.
    - `QuestionImportResultDTO`: Kết quả import file câu hỏi Excel.
    - `UserDataDTO`, `UserResponseDTO`: Dữ liệu tài khoản người dùng chuẩn hóa.
- **`com.vatly1.example.model.dto`** (23 Domain / Transfer DTOs):
  - Chuyên phục vụ các đối tượng dữ liệu nghiệp vụ dùng chung giữa Service, Converter và Controller (`ClassDTO`, `ExamDTO`, `SubjectDTO`, `TopicDTO`, `UserProfileDTO`, `EvidenceDTO`, `AiFeedbackDTO`, ...).

```java
// Ví dụ các câu lệnh import chuẩn mực và sáng sủa:
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;
import com.vatly1.example.model.dto.*;
```

### 2. Cấu trúc thư mục mã nguồn:
```text
hethongvatli1/src/main/java/com/vatly1/example/
├── configuration/            # Cấu hình Spring Beans, MinIO, OpenAPI, WebMvc
├── controller/               # REST API Controllers (User, Exam, Class, File, AI,...)
├── converter/                # Lớp chuyển đổi ánh xạ Entity <-> Model/DTO
├── model/                    # Tầng Data Models phân chia 3 thư mục con:
│   ├── request/              # 38 Request Payload Models (Create*, Update*, Signin*,...)
│   ├── response/             # 7 Response Payload Models (ApiResponse, AuthResponse,...)
│   └── dto/                  # 23 Domain / Transfer DTOs (ClassDTO, ExamDTO, SubjectDTO,...)
├── entity/                   # Các thực thể JPA ánh xạ CSDL (User, Class, Exam, Topic,...)
│   └── enums/                # Các Enum định nghĩa trạng thái, vai trò hệ thống
├── exception/                # Bộ xử lý ngoại lệ tập trung (GlobalExceptionHandler)
├── filter/                   # Bộ lọc bảo mật JWT (JwtTokenFilter) & RateLimitFilter
├── repository/               # Các JPA Repositories truy vấn cơ sở dữ liệu
├── security/                 # Cấu hình WebSecurity, UserDetails, PasswordEncoder
├── service/                  # Interface nghiệp vụ hệ thống
│   └── impl/                 # Hiện thực hóa chi tiết các Service
└── utils/                    # Các hàm tiện ích hỗ trợ (JwtTokenUtils, FileUtils,...)
```

---

## 📑 Các Phân Hệ Nghiệp Vụ

### 1. Phân Hệ Xác Thực & Tài Khoản (Authentication & User Management)
- `POST /api/v1/users/signin`: Đăng nhập, cấp phát Access Token và Refresh Token.
- `POST /api/v1/users/refresh`: Xoay vòng Refresh Token lấy Access Token mới.
- `POST /api/v1/users/register`: Đăng ký tài khoản sinh viên.
- `POST /api/v1/users/logout`: Đăng xuất và thu hồi Refresh Token.
- `POST /api/v1/users/forgot-password` & `reset-password`: Khôi phục mật khẩu qua Email token.
- `GET/PUT /api/v1/users/me`: Quản lý thông tin cá nhân và đổi mật khẩu.
- `GET/POST/PUT /api/v1/users/admin/**`: Quản trị viên quản lý danh sách tài khoản, khóa/mở khóa tài khoản.

### 2. Phân Hệ Quản Lý Học Phần, Học Kỳ & Lớp Học
- Quản lý danh mục Môn học (`/api/v1/subjects/**`), Học kỳ (`/api/v1/semesters/**`).
- Quản lý Lớp học phần (`/api/v1/classes/**`): Tạo lớp, cập nhật trạng thái lớp.
- Phân công Giảng viên / Trợ giảng (`/api/v1/classes/{classId}/staff/**`).
- Ghi danh sinh viên vào lớp học (`/api/v1/classes/{classId}/enrollments/**`).

### 3. Phân Hệ Ngân Hàng Câu Hỏi & Đề Thi Trắc Nghiệm
- Quản lý câu hỏi theo môn và chủ đề (`/api/v1/questions/**`).
- **Import câu hỏi từ Excel**:
  - `POST /api/v1/questions/import/excel`: Đọc tệp `.xlsx`, parse câu hỏi và các đáp án A, B, C, D, phân loại độ khó tự động.
  - `GET /api/v1/questions/import/template`: Tải về tệp Excel mẫu có sẵn cấu trúc chuẩn.
- Quản lý đề thi (`/api/v1/exams/**`): Tạo đề thi theo ma trận câu hỏi, thiết lập thời gian làm bài, số lượt thử.
- Làm bài thi & Chấm điểm (`/api/v1/exams/{id}/attempts/**`):
  - Sinh đề xáo trộn ngẫu nhiên.
  - Nộp bài thi và nhận kết quả tức thì kèm lịch sử đáp án chi tiết.

### 4. Phân Hệ Thí Nghiệm Ảo & Báo Cáo Minh Chứng
- Giao bài thí nghiệm (`/api/v1/experiments/**`): Hướng dẫn thực hành, yêu cầu kết quả.
- Nộp báo cáo thí nghiệm (`/api/v1/experiments/{id}/submissions/**`): Nộp số liệu và tệp minh chứng ảnh/PDF.
- Giảng viên chấm bài thực hành, ghi nhận xét và điểm số.

### 5. Phân Hệ Lưu Trữ Tệp & Ảnh (MinIO Storage)
- `POST /api/v1/files/upload`: Tải lên hình ảnh, tài liệu (lưu trữ trên MinIO Bucket `vatly1-bucket`).
- `GET /api/v1/files/**`: Truy xuất và hiển thị tệp tin, hình ảnh trực tiếp (Public Streaming Endpoint).

### 6. Phân Hệ AI Tutor & Phân Tích Dữ Liệu
- `POST /api/v1/ai/conversations`: Khởi tạo và trò chuyện hỏi đáp học tập môn Vật lý 1.
- `POST /api/v1/ai/feedback`: Gửi đánh giá sao (1-5) và bình luận phản hồi cho câu trả lời của AI.
- `GET /api/v1/analytics/dashboard`: Bảng điều khiển tổng hợp số liệu cho Giảng viên/Admin.

---

## ⚙️ Cài Đặt & Khởi Chạy

### 1. Yêu Cầu Môi Trường
- **JDK**: Java 17 trở lên (đã kiểm thử tương thích tốt trên Java 17, 21 và 24).
- **Maven**: 3.9+ (hoặc sử dụng trực tiếp Maven Wrapper `./mvnw` đính kèm dự án).
- **Docker & Docker Compose**: Để khởi chạy PostgreSQL và MinIO.

### 2. Khởi Động Các Dịch Vụ Hạ Tầng (Docker Compose)
Dự án đã chuẩn bị sẵn file `docker-compose.yml` định nghĩa PostgreSQL 16 và MinIO:

```bash
# Di chuyển vào thư mục backend
cd hethongvatli1

# Khởi chạy các container nền (PostgreSQL, MinIO)
docker-compose up -d
```

Sau khi khởi chạy thành công:
- **PostgreSQL**: `localhost:5432` (Database: `vatly1`, User: `vatly1`, Password: `vatly1_pass`)
- **MinIO API**: `http://localhost:9000`
- **MinIO Web Console**: `http://localhost:9001` (User: `minioadmin`, Password: `minioadmin`)

### 3. Cấu Hình Ứng Dụng
Xem cấu hình mẫu tại `src/main/resources/application.yml` hoặc chỉnh sửa các biến môi trường:
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vatly1
    username: vatly1
    password: vatly1_pass
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: vatly1-bucket
  url-prefix: http://localhost:9000/vatly1-bucket
  enabled: true
```

### 4. Build & Chạy Ứng Dụng
```bash
# Sử dụng Maven Wrapper (Windows PowerShell)
$env:JAVA_HOME = "E:\java"  # Đường dẫn JDK 17+ của bạn nếu cần
.\mvnw clean spring-boot:run

# Hoặc trên Linux/macOS
./mvnw clean spring-boot:run
```

Hệ thống sẽ lắng nghe tại: `http://localhost:8080`.

---

## 📖 Tài Liệu API (Swagger UI)

Sau khi ứng dụng khởi chạy thành công, truy cập tài liệu API tương tác trực tiếp qua Swagger UI:

- **Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **OpenAPI JSON Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

> **Mẹo:** Bạn có thể bấm nút **Authorize** trên giao diện Swagger và dán mã Token JWT dạng `Bearer <token>` để kiểm thử trực tiếp các endpoint yêu cầu quyền `ADMIN`, `LECTURER` hoặc `STUDENT`.

---

## 🧪 Kiểm Thử & Đảm Bảo Chất Lượng

Dự án sở hữu bộ kiểm thử tự động toàn diện bao quát 23 phân hệ, tích hợp xác thực bảo mật, kiểm tra IDOR, Rate Limiting và tải đồng thời:

```bash
# Chạy toàn bộ test suite
.\mvnw test
```

### Kết Quả Kiểm Thử Thực Tế:
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.vatly1.example.concurrency.ExamConcurrencyIntegrationTest
...
[INFO] Results:
[INFO] 
[INFO] Tests run: 194, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] --------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] --------------------------------------------------------
```

### Danh Sách 23 Phân Hệ Kiểm Thử (194/194 PASS - 100%):
1. `ActivityAndAuditLogTest` - Kiểm tra ghi nhận nhật ký kiểm toán & hoạt động.
2. `ActuatorHealthTest` - Giám sát sức khỏe ứng dụng và các service phụ trợ.
3. `AiTutorControllerTest` - Kiểm thử hội thoại AI và đánh giá phản hồi.
4. `AnalyticsControllerTest` - Kiểm thử tổng hợp dữ liệu thống kê & cron job.
5. `ClassControllerTest` - Quản lý lớp học phần, phân công trợ giảng, ghi danh.
6. `DashboardControllerTest` - Thống kê số liệu bảng điều khiển tổng quan.
7. `EvidenceControllerTest` - Quản lý tệp minh chứng kết quả thí nghiệm.
8. `ExamControllerTest` - Tạo đề thi, ma trận đề, sinh câu hỏi và làm bài thi.
9. `ExperimentControllerTest` - Giao bài thí nghiệm, nộp bài và chấm điểm thực hành.
10. `FileStorageIntegrationTest` - Upload ảnh, PDF, stream tệp qua MinIO và fallback an toàn.
11. `IdorSecurityControllerTest` - Bảo vệ chống truy cập trái phép chéo tài nguyên.
12. `LearningMaterialControllerTest` - Quản lý tài liệu học tập lý thuyết môn học.
13. `LearningProgressControllerTest` - Theo dõi tiến độ hoàn thành bài học của sinh viên.
14. `PasswordResetControllerTest` - Quy trình quên mật khẩu và đặt lại mật khẩu an toàn.
15. `QuestionBankControllerTest` - Ngân hàng câu hỏi & Import hàng loạt từ tệp Excel.
16. `RateLimitControllerTest` - Cơ chế chặn spam và giới hạn tần suất request.
17. `SemesterControllerTest` - Quản lý danh mục học kỳ.
18. `StudentClassControllerTest` - Dành cho sinh viên tra cứu lớp và tài liệu học.
19. `SubjectControllerTest` - Quản lý thông tin học phần Vật lý 1.
20. `SystemSettingControllerTest` - Cấu hình tham số hệ thống động.
21. `TopicControllerTest` - Quản lý các chủ đề bài học trong chương trình.
22. `UserControllerTest` - Đăng nhập, đăng ký, JWT token rotation, quản lý tài khoản.
23. `ExamConcurrencyIntegrationTest` - Kiểm thử nộp bài thi đồng thời chống xung đột dữ liệu.

---

## 👥 Đóng Góp & Phát Triển
- **Repository:** [https://github.com/Thai-DM/vat_li_be](https://github.com/Thai-DM/vat_li_be)
- **Tác giả:** Thai-DM
- **Bản quyền:** Hệ thống được phát triển phục vụ mục đích đào tạo và nghiên cứu học phần Vật lý 1.
