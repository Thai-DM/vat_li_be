# Tài Liệu Kỹ Thuật RESTful API — Hệ Thống Quản Lý Học Tập & Thí Nghiệm Ảo Vật Lý 1

> **Phiên bản tài liệu:** v1.0.0  
> **Base URL:** `http://localhost:8080/api/v1`  
> **Cổng dịch vụ mặc định:** `8080` (Cấu hình: `server.port: 8080`)  
> **Active Spring Profile:** `dev` (Cơ sở dữ liệu PostgreSQL Cloud trên AWS Neon Tech)  
> **Khung công nghệ:** Spring Boot 3.5.x · Spring Security 6 (JWT + Sliding Window Rate Limiting) · Spring Data JPA / Hibernate · PostgreSQL Driver · Lombok · Jackson JSON  
> **Giao diện kiểm thử Swagger UI:** [`http://localhost:8080/swagger-ui/index.html`](http://localhost:8080/swagger-ui/index.html) hoặc [`http://localhost:8080/swagger-ui.html`](http://localhost:8080/swagger-ui.html)  
> **Đặc tả OpenAPI 3.0 (JSON):** [`http://localhost:8080/v3/api-docs`](http://localhost:8080/v3/api-docs)  
> **Giám sát sức khỏe Spring Actuator:** [`http://localhost:8080/actuator/health`](http://localhost:8080/actuator/health) (Public) | `/actuator/**` (Yêu cầu `ROLE_ADMIN`)  
> **Chính sách tải lên tệp:** Thư mục `uploads`, giới hạn dung lượng tối đa 50MB (`app.file.max-size-mb: 50`)  
> **Nguyên tắc biên soạn:** Phản ánh 100% hiện trạng source code thực tế (19 Controllers, 68 DTOs, 18 Enums, 43 Entities) — Cam kết không suy diễn, không bịa đặt API.

---

## Mục Lục Điều Hướng

1. [Tổng Quan Kiến Trúc & Cấu Hình Môi Trường](#1-tổng-quan-kiến-trúc--cấu-hình-môi-trường)
2. [Xác Thực, Phân Quyền & Các Lớp Bảo Mật](#2-xác-thực-phân-quyền--các-lớp-bảo-mật)
3. [Chi Tiết Toàn Bộ 107 Endpoint (19 Phân Hệ)](#3-chi-tiết-toàn-bộ-106-endpoint-19-phân-hệ)
4. [Bảng Tổng Hợp Tham Chiếu 107 Endpoint](#4-bảng-tổng-hợp-tham-chiếu-106-endpoint)
5. [Từ Điển Dữ Liệu Chi Tiết (Data Models: Enums, DTOs & Entities)](#5-từ-điển-dữ-liệu-chi-tiết-data-models-enums-dtos--entities)
6. [Xử Lý Lỗi Tập Trung & Bảng Mã Phản Hồi HTTP](#6-xử-lý-lỗi-tập-trung--bảng-mã-phản-hồi-http)
7. [Các Luồng Nghiệp Vụ Trọng Yếu (Core Business Workflows)](#7-các-luồng-nghiệp-vụ-trọng-yếu-core-business-workflows)
8. [Bộ Mẫu Thử Nghiệm Postman & cURL Thực Tế](#8-bộ-mẫu-thử-nghiệm-postman--curl-thực-tế)
9. [Kiểm Định Tính Nhất Quán API & Khuyến Nghị Kiến Trúc](#9-kiểm-định-tính-nhất-quán-api--khuyến-nghị-kiến-trúc)

---

## 1. Tổng Quan Kiến Trúc & Cấu Hình Môi Trường

### 1.1. Kiến trúc hệ thống

Hệ thống Quản lý Học tập & Thí nghiệm Ảo Vật lý 1 (Physics 1 LMS & Virtual Lab) được xây dựng theo kiến trúc phân lớp chuẩn công nghiệp (Layered Architecture):

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                      Client Layer (Web Frontend / Postman / Mobile)             │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ HTTP Request (Bearer JWT / Multipart)
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             Security & Filter Layer                              │
│  - RateLimitFilter (Cửa sổ trượt 60s: /signin 10/min, /signup 3/min, ...)       │
│  - JwtTokenFilter (Xác thực JWT HMAC-SHA256, nạp userId/role vào Request Attr)   │
│  - WebSecurityConfig (CORS, CSRF disable, Stateless Session, RBAC @PreAuth)      │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ Dispatch
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                 Presentation Layer (19 REST Controllers, Base /api/v1)           │
│  - Swagger OpenAPI 3.0 Annotations (@Tag, @Operation, @ApiResponses)            │
│  - Bean Validation 3.0 (@Valid, @NotNull, @NotBlank, @Size, @Email, @Min, @Max) │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ Service Invocation
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                                 Service Layer                                    │
│  - Business Services (UserService, ExamService, ExperimentService, AiTutor...)   │
│  - Dynamic Configuration (SystemSettingService)                                  │
│  - CTT Analytics Engine & Cron Jobs (@Scheduled, @Async)                         │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │ Spring Data JPA
                                         ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                    Persistence Layer (Spring Data JPA / Hibernate)               │
│  - 43 Entities / 18 Enums / PostgreSQL JSONB columns                            │
│  - Pessimistic Locking (SELECT FOR UPDATE) trên lượt nộp bài thi                 │
│  - Database: AWS Neon PostgreSQL (Driver org.postgresql.Driver)                  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2. Danh mục 19 Phân hệ (Controllers) & Base Paths

| STT | Phân Hệ Controller | Base Path | Số lượng API | Mô tả phạm vi chức năng |
|:---:|---|---|:---:|---|
| 1 | `UserController` | `/api/v1/users` | 18 | Đăng nhập, đăng ký, refresh token, hồ sơ cá nhân, quản trị tài khoản |
| 2 | `SubjectController` | `/api/v1/subjects` | 5 | Danh mục môn học (Vật lý 1 và các học phần Cơ bản 1) |
| 3 | `SemesterController` | `/api/v1/semesters` | 5 | Quản lý học kỳ, năm học niên khóa |
| 4 | `TopicController` | `/api/v1/subjects/{subjectId}/topics` | 5 | Chương mục kiến thức Vật lý (Cơ học, Nhiệt học, Dao động...) |
| 5 | `ClassController` | `/api/v1/classes` | 14 | Lớp học phần, phân công giảng viên/trợ giảng, ghi danh sinh viên |
| 6 | `StudentClassController` | `/api/v1/students/me` | 1 | Tra cứu danh sách lớp học phần sinh viên đang theo học |
| 7 | `QuestionBankController` | `/api/v1/questions` | 7 | Ngân hàng câu hỏi trắc nghiệm, phê duyệt, nhập đề từ PDF qua AI OCR |
| 8 | `ExamController` | `/api/v1/exams` | 11 | Thiết lập đề thi, sinh đề theo ma trận, làm bài, nộp bài, chấm điểm |
| 9 | `ExperimentController` | `/api/v1/experiments` | 7 | Thí nghiệm ảo 3D Vật lý 1, giao bài, nộp số liệu, chấm điểm Rubric |
| 10 | `LearningMaterialController` | `/api/v1/topics/{topicId}/materials` | 6 | Học liệu số (PDF, Video, Bài giảng), kiểm duyệt học liệu |
| 11 | `EvidenceController` | `/api/v1` | 3 | Kho lưu trữ minh chứng kết quả đo thực nghiệm (có IDOR protection) |
| 12 | `LearningProgressController` | `/api/v1/classes/{classId}/progress` | 1 | Báo cáo tiến độ học liệu của toàn lớp (Giảng viên) |
| 13 | `StudentProgressController` | `/api/v1/students/me/progress` | 2 | Theo dõi và cập nhật tiến độ học tập cá nhân (Sinh viên) |
| 14 | `DashboardController` | `/api/v1/dashboard` | 4 | Bảng điều khiển tổng hợp thống kê cho Giảng viên và Sinh viên |
| 15 | `AnalyticsController` | `/api/v1/analytics` | 5 | Phân tích học thuật CTT: Độ khó (p-value), Độ phân biệt (DI), Lỗ hổng AI |
| 16 | `AiTutorController` | `/api/v1/ai-tutor` | 6 | Trợ giảng AI Socratic tiếng Việt: gợi mở tư duy, không giải hộ |
| 17 | `AdminLogController` | `/api/v1/admin` | 2 | Nhật ký kiểm toán bảo mật (Audit Logs) & Nhật ký hoạt động (Activity Logs) |
| 18 | `SystemSettingController` | `/api/v1/admin/settings` | 4 | Quản trị tham số động hệ thống (Dynamic Configuration) |
| 19 | `StudentActivityLogController` | `/api/v1/students` | 1 | Nhật ký tương tác học tập của sinh viên đang đăng nhập |

**Tổng cộng:** **107 REST Endpoints** hoạt động trên hệ thống.

---

### 1.3. Cấu trúc phản hồi chuẩn (`ApiResponse<T>`)

Mọi endpoint trong hệ thống (ngoại trừ API tải file hoặc trigger dạng void) đều trả về cấu trúc chuẩn đồng nhất:

```json
{
  "status": 200,
  "message": "Success",
  "data": { ... }
}
```

Trường hợp xảy ra lỗi nghiệp vụ hoặc validation:
```json
{
  "status": 400,
  "message": "Dữ liệu yêu cầu không hợp lệ hoặc thiếu trường bắt buộc",
  "data": null
}
```

---

### 1.4. Thông số Cấu hình Runtime (`application.yml` & `application-dev.yml`)

Dưới đây là bảng thông số cấu hình chính xác được trích xuất từ source code:

| Tham số cấu hình | Biến môi trường | Giá trị mặc định | Ý nghĩa kỹ thuật |
|---|---|---|---|
| `server.port` | — | `8080` | Cổng HTTP lắng nghe của máy chủ Spring Boot |
| `spring.profiles.active` | — | `dev` | Profile hoạt động (kết nối cơ sở dữ liệu Neon PostgreSQL) |
| `security.jwt.token.secret-key` | `JWT_SECRET` | `secret-key` | Khóa bí mật băm SHA-256 dùng ký JWT HMAC-SHA256 |
| `security.jwt.token.expire-length` | `JWT_EXPIRE_MS` | `300000` (5 phút) | Thời hạn hiệu lực của Access Token (ms) |
| `security.jwt.refresh-token.expire-length` | `JWT_REFRESH_EXPIRE_MS` | `604800000` (7 ngày) | Thời hạn hiệu lực của Refresh Token (ms) |
| `app.cors.allowed-origins` | `CORS_ALLOWED_ORIGINS` | `*` | Danh sách tên miền được phép gọi API (CORS) |
| `app.file.upload-dir` | `FILE_UPLOAD_DIR` | `uploads` | Thư mục lưu trữ tệp đính kèm học liệu và minh chứng |
| `app.file.max-size-mb` | `FILE_MAX_SIZE_MB` | `50` | Giới hạn dung lượng tối đa cho 1 tệp tải lên (MB) |
| `management.endpoints.web.base-path` | — | `/actuator` | Đường dẫn gốc của module giám sát hệ thống |
| `management.endpoints.web.exposure.include`| — | `health, info, metrics` | Các chỉ số actuator được công khai |

---

## 2. Xác Thực, Phân Quyền & Các Lớp Bảo Mật

### 2.1. Cơ chế xác thực JWT Bearer Token

- **Thuật toán ký:** HMAC-SHA256 (`HS256`). Khóa bí mật cấu hình trong `security.jwt.token.secret-key` được băm qua hàm băm mật mã `SHA-256` trước khi nạp vào `Keys.hmacShaKeyFor(keyBytes)` để đảm bảo độ dài khóa luôn đạt chuẩn 256 bits an toàn.
- **Tiêu đề truyền token (HTTP Header):**
  ```http
  Authorization: Bearer <accessToken>
  ```
  *(Bộ lọc `JwtTokenUtils.resolveToken` được lập trình để tự động làm sạch ký tự ngoặc kép thừa `"` nếu phía client gửi nhầm).*
- **Cấu trúc dữ liệu Payload trong JWT (Claims):**
  - `sub`: Tên đăng nhập người dùng (`username`).
  - `auth`: Danh sách quyền hạn Spring Security, định dạng `["ROLE_" + role.name()]` (ví dụ: `["ROLE_STUDENT"]`).
  - `userId`: Định danh UUID của tài khoản người dùng (`userId.toString()`).
  - `role`: Tên vai trò người dùng (`STUDENT`, `INSTRUCTOR`, `TA`, `ADMIN`).
  - `iat`: Thời điểm phát hành token (Epoch time).
  - `exp`: Thời điểm token hết hạn (Epoch time).
- **Bộ lọc ngữ cảnh (`JwtTokenFilter`):**
  Sau khi xác thực chữ ký JWT thành công, bộ lọc tự động trích xuất và gán trực tiếp vào thuộc tính của HTTP Servlet Request:
  ```java
  httpServletRequest.setAttribute("userId", userId);
  httpServletRequest.setAttribute("role", role);
  ```
  Nhờ đó, các Controller có thể lấy nhanh UUID của người dùng đang đăng nhập thông qua `(String) request.getAttribute("userId")` mà không cần truy vấn lại cơ sở dữ liệu.

---

### 2.2. Bộ lọc Giới hạn Tần suất Yêu cầu (Rate Limiting Filter — `RateLimitFilter`)

Để bảo vệ hệ thống trước các cuộc tấn công Brute-Force mật khẩu và spam đăng ký tài khoản rác, hệ thống triển khai bộ lọc chuyên dụng `RateLimitFilter` trước chuỗi xác thực `UsernamePasswordAuthenticationFilter`:

- **Mô hình triển khai:** Cửa sổ trượt đồng thời (Concurrent In-Memory Sliding Window) sử dụng `ConcurrentHashMap<String, Queue<Long>>`.
- **Độ dài cửa sổ trượt:** 60,000 mili-giây (1 phút).
- **Định mức giới hạn chi tiết theo Endpoint:**
  1. `POST /api/v1/users/signin`: Tối đa **10 requests / phút / IP** (Ngăn chặn dò mật khẩu).
  2. `POST /api/v1/users/signup`: Tối đa **3 requests / phút / IP** (Ngăn chặn tạo tài khoản ảo hàng loạt).
  3. `POST /api/v1/users/forgot-password`: Tối đa **3 requests / phút / IP** (Ngăn chặn spam email OTP).
- **Cơ chế nhận diện IP Client (`getClientIp`):**
  Ưu tiên đọc trường đầu tiên trong HTTP Header `X-Forwarded-For` (tránh nhận nhầm IP của Nginx/Cloudflare Proxy/Reverse Proxy). Nếu không có proxy, fallback về `request.getRemoteAddr()`.
- **Phản hồi khi vi phạm ngưỡng (HTTP 429 Too Many Requests):**
  ```json
  {
    "status": 429,
    "error": "Too Many Requests",
    "message": "Rate limit exceeded. Please try again later."
  }
  ```
- **Lưu ý kiến trúc phân tán (Horizontal Scaling Note):** Bộ lọc hiện tại hoạt động trên bộ nhớ RAM của một Pod đơn lẻ. Khi triển khai trên cụm Kubernetes nhiều Pod đằng sau Round-Robin Load Balancer, mỗi Pod sẽ có bộ đếm riêng (tổng giới hạn thực tế là `limit * N`). Trong môi trường Production mở rộng, nên offload tính năng này lên API Gateway (Nginx `limit_req_zone`, Cloudflare WAF) hoặc dùng Redis Token Bucket (`Bucket4j-Redis`).

---

### 2.3. Cấu hình Chia sẻ Tài nguyên Liên miền (CORS Policy)

Được định nghĩa tập trung tại `WebSecurityConfig.corsConfigurationSource()`:
- **Nguồn gốc cho phép (Allowed Origins):** Nhận giá trị từ `app.cors.allowed-origins` (nếu là `*` hoặc để trống, hệ thống cho phép mọi domain thông qua `setAllowedOriginPatterns("*")`; nếu cấu hình danh sách domain phân tách bằng dấu phẩy, hệ thống sẽ lọc chính xác danh sách đó).
- **Phương thức HTTP được phép (Allowed Methods):** `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`.
- **Tiêu đề được phép (Allowed Headers):** `*` (Cho phép mọi header, bao gồm `Authorization`, `Content-Type`, `X-Requested-With`).
- **Cho phép gửi Cookie / Chứng chỉ (Allow Credentials):** `true`.

---

### 2.4. Hệ thống Phân quyền (RBAC) & Ma trận Truy cập

Hệ thống quản lý truy cập theo 4 vai trò chính xác:
1. `ROLE_STUDENT`: Sinh viên — Luyện tập, làm bài thi, thực hành thí nghiệm ảo 3D, nộp minh chứng, trao đổi với Trợ giảng AI Socratic tiếng Việt.
2. `ROLE_INSTRUCTOR`: Giảng viên — Quản lý lớp học phần, ngân hàng câu hỏi, tạo đề thi, giao thí nghiệm ảo, chấm điểm Rubric, xem báo cáo học thuật CTT.
3. `ROLE_TA`: Trợ giảng (Teaching Assistant) — Hỗ trợ chấm điểm Rubric bài nộp thí nghiệm ảo của sinh viên.
4. `ROLE_ADMIN`: Quản trị viên hệ thống — Quản trị toàn bộ tài khoản người dùng, cấu hình tham số hệ thống, kích hoạt phân tích CTT, xem nhật ký kiểm toán bảo mật và giám sát Actuator.

#### Danh sách 5 Endpoints công khai (Permit All):
1. `POST /api/v1/users/signin` (Đăng nhập)
2. `POST /api/v1/users/signup` (Đăng ký tài khoản sinh viên)
3. `POST /api/v1/users/refresh` (Làm mới token)
4. `POST /api/v1/users/forgot-password` (Gửi yêu cầu quên mật khẩu)
5. `POST /api/v1/users/reset-password` (Đặt lại mật khẩu)
6. `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`

---

### 2.5. Các Lớp Phòng Vệ Bảo Mật Nghiệp Vụ Nâng Cao

1. **Phòng chống tấn công IDOR (Insecure Direct Object Reference):**
   Tại `GET /api/v1/students/{id}/evidence`, hệ thống kiểm tra logic chặt chẽ: Sinh viên chỉ được phép xem minh chứng của chính mình (`currentUserId.equals(id)`). Giảng viên và Quản trị viên chỉ được xem nếu sinh viên đó thuộc lớp học phần mà họ phụ trách. Vi phạm sẽ bị từ chối truy cập ngay lập tức.
2. **Khóa ghi bi quan chống Race Condition (Pessimistic Write Lock):**
   Tại thời điểm sinh viên nộp bài thi (`PUT /api/v1/exams/attempts/{attemptId}/submit`), hệ thống kích hoạt câu lệnh `SELECT FOR UPDATE` trên bản ghi lượt thi (`ExamAttempt`). Điều này ngăn chặn triệt để hành vi gửi đồng thời nhiều request nộp bài để gian lận điểm số hoặc gây sai lệch trạng thái.
3. **Chính sách kiểm soát số lượt thi (Multi-attempt Policy):**
   - Với đề luyện tập (`PRACTICE`): Sinh viên được phép làm nhiều lần để củng cố kiến thức.
   - Với bài kiểm tra và bài thi chính thức (`QUIZ`, `MIDTERM`, `FINAL`): Hệ thống kiểm tra số lượt làm bài. Nếu sinh viên đang có một lượt thi `IN_PROGRESS` hoặc đã nộp xong, hệ thống sẽ chặn và ném mã lỗi `409 CONFLICT`.
4. **Bảo vệ Spring Actuator:**
   Chỉ cho phép truy cập công khai endpoint kiểm tra trạng thái sống `/actuator/health`. Mọi endpoint quản trị nhạy cảm khác (`/actuator/metrics`, `/actuator/info`) đều bắt buộc phải có vai trò `ROLE_ADMIN`.

---
## 3. Chi Tiết Endpoint

---

### 3.1. Users & Authentication

**Base path:** `/api/v1/users`

---

#### POST `/api/v1/users/signin` — Đăng nhập

**Mô tả:** Đăng nhập hệ thống, nhận cặp Access/Refresh Token. Áp dụng Rate Limiting chống brute-force.

**Xác thực:** Không cần

**Request Body:**
```json
{
  "username": "admin",
  "password": "admin123456"
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `username` | string | ✓ | `@NotBlank` |
| `password` | string | ✓ | `@NotBlank` |

**Response 200:**
```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "accessToken": "eyJhbGci...",
    "refreshToken": "d4f8e...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "refreshExpiresIn": 604800
  }
}
```

| Status | Mô tả |
|---|---|
| 200 | Đăng nhập thành công |
| 400 | Dữ liệu không hợp lệ |
| 422 | Sai username/password |

---

#### POST `/api/v1/users/signup` — Đăng ký sinh viên

**Mô tả:** Tạo tài khoản Sinh viên mới, gán mặc định `ROLE_STUDENT`.

**Xác thực:** Không cần

**Request Body:**
```json
{
  "username": "sv001",
  "email": "sv001@edu.vn",
  "password": "password123"
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `username` | string | ✓ | `@NotBlank`, min 4, max 255 ký tự |
| `email` | string | ✓ | `@NotBlank`, `@Email` |
| `password` | string | ✓ | `@NotBlank`, min 8 ký tự |

**Response 200:** `AuthResponseDTO` (cặp token — tự động đăng nhập sau đăng ký)

| Status | Mô tả |
|---|---|
| 200 | Đăng ký thành công |
| 422 | Username đã tồn tại |

---

#### POST `/api/v1/users/forgot-password` — Quên mật khẩu

**Mô tả:** Gửi email đặt lại mật khẩu. Mã có TTL 15 phút, dùng 1 lần. Rate limit: 3 lần/phút.

**Xác thực:** Không cần

**Request Body:**
```json
{
  "email": "student@edu.vn"
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `email` | string | ✓ | `@NotBlank`, `@Email` |

**Response 200:**
```json
{
  "status": 200,
  "message": "Nếu email tồn tại trên hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi.",
  "data": null
}
```

| Status | Mô tả |
|---|---|
| 200 | Luôn trả 200 (không tiết lộ email có tồn tại hay không) |
| 400 | Sai định dạng email |
| 429 | Rate limit vượt ngưỡng |

---

#### POST `/api/v1/users/reset-password` — Đặt lại mật khẩu

**Xác thực:** Không cần

**Request Body:** `ResetPasswordRequestDTO` (token + newPassword — xác nhận từ source code)

| Status | Mô tả |
|---|---|
| 200 | Đặt lại mật khẩu thành công |
| 400 | Token không hợp lệ, hết hạn hoặc đã dùng |

---

#### POST `/api/v1/users/refresh` — Làm mới Access Token

**Mô tả:** Không yêu cầu Access Token. Refresh Token cũ bị hủy (Token Rotation).

**Xác thực:** Không cần

**Request Body:**
```json
{
  "refreshToken": "d4f8e..."
}
```

**Response 200:** `AuthResponseDTO` (cặp token mới)

| Status | Mô tả |
|---|---|
| 200 | Token mới được cấp |
| 401 | Refresh token hết hạn hoặc không hợp lệ |
| 404 | Người dùng không còn tồn tại |

---

#### POST `/api/v1/users/logout` — Đăng xuất

**Xác thực:** Bearer Token (bất kỳ role nào đã đăng nhập)

**Request Body:**
```json
{
  "refreshToken": "d4f8e..."
}
```

**Response 200:** Thu hồi Refresh Token. Access Token tự hết hạn.

---

#### GET `/api/v1/users/me` — Thông tin tài khoản hiện tại

**Xác thực:** Bearer Token (bất kỳ role)

**Response 200:**
```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "id": "uuid",
    "username": "sv001",
    "email": "sv001@edu.vn",
    "role": "STUDENT",
    "status": "ACTIVE"
  }
}
```

---

#### PUT `/api/v1/users/me` — Cập nhật username/email

**Xác thực:** Bearer Token (bất kỳ role)

**Request Body:** `UserUpdateDTO` (username, email)

---

#### PUT `/api/v1/users/me/password` — Đổi mật khẩu

**Xác thực:** Bearer Token (bất kỳ role)

**Request Body:** `ChangePasswordDTO`

---

#### GET `/api/v1/users/me/profile` — Lấy hồ sơ cá nhân

**Xác thực:** Bearer Token (bất kỳ role)

**Response 200:** `UserProfileDTO`

---

#### PUT `/api/v1/users/me/profile` — Cập nhật hồ sơ cá nhân

**Xác thực:** Bearer Token (bất kỳ role)

**Request Body:** `UserProfileUpdateDTO`

---

#### POST `/api/v1/users/admin/create-user` — Tạo người dùng (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

**Request Body:**
```json
{
  "username": "newinstructor",
  "email": "instructor@example.com",
  "password": "password123",
  "role": "INSTRUCTOR"
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `username` | string | ✓ | `@NotBlank`, min 4, max 255 |
| `email` | string | ✓ | `@NotBlank`, `@Email` |
| `password` | string | ✓ | `@NotBlank`, min 8 ký tự |
| `role` | enum | ✓ | `@NotNull` — `STUDENT`, `INSTRUCTOR`, `TA`, `ADMIN` |

| Status | Mô tả |
|---|---|
| 200 | Tạo thành công |
| 403 | Không phải Admin |
| 422 | Username đã tồn tại |

---

#### GET `/api/v1/users/admin/users` — Danh sách người dùng (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

**Query Params:** `page`, `size`, `sort` (Spring Pageable)

**Response 200:** `Page<UserResponseDTO>`

---

#### GET `/api/v1/users/admin/users/{id}/profile` — Hồ sơ người dùng (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

**Path Param:** `id` (UUID)

---

#### PUT `/api/v1/users/admin/users/{id}` — Cập nhật vai trò/email (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

**Request Body:** `AdminUpdateUserDTO`

---

#### PUT `/api/v1/users/admin/users/{id}/status` — Khóa/Mở khóa tài khoản (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

**Request Body:** `UpdateUserStatusDTO` (status: `ACTIVE` | `LOCKED`)

---

#### GET `/api/v1/users/{username}` — Tra cứu người dùng (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

---

#### DELETE `/api/v1/users/{username}` — Xóa người dùng (Admin)

**Xác thực:** Bearer Token, role `ADMIN`

| Status | Mô tả |
|---|---|
| 200 | Xóa thành công |
| 404 | Không tìm thấy |

---

### 3.2. Subject Management

**Base path:** `/api/v1/subjects`  
**Xác thực:** Tất cả đều yêu cầu Bearer Token

---

#### GET `/api/v1/subjects` — Danh sách môn học

**Query Params:**

| Param | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `isActive` | boolean | null | Lọc theo trạng thái |
| `page` | int | 0 | Trang |
| `size` | int | 20 | Kích thước trang |

**Response 200:** `Page<SubjectDTO>`

---

#### GET `/api/v1/subjects/{id}` — Chi tiết môn học

**Path Param:** `id` (UUID)

**Response 200:** `SubjectDTO`

---

#### POST `/api/v1/subjects` — Tạo môn học mới

**Xác thực:** role `ADMIN`

**Request Body:** `CreateSubjectDTO`

**Response 201:** `SubjectDTO`

---

#### PUT `/api/v1/subjects/{id}` — Cập nhật môn học

**Xác thực:** role `ADMIN`

**Request Body:** `UpdateSubjectDTO` (không được sửa mã môn)

---

#### PUT `/api/v1/subjects/{id}/toggle-status` — Bật/Tắt môn học

**Xác thực:** role `ADMIN`

**Mô tả:** Toggle `isActive` (xóa mềm)

---

### 3.3. Semester Management

**Base path:** `/api/v1/semesters`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/semesters` — Danh sách học kỳ

**Xác thực:** Không có `@PreAuthorize` (yêu cầu Bearer nhưng không lọc role)

**Response 200:** `List<SemesterDTO>` (sắp xếp mới nhất lên đầu)

---

#### GET `/api/v1/semesters/{id}` — Chi tiết học kỳ

---

#### POST `/api/v1/semesters` — Tạo học kỳ mới

**Xác thực:** role `ADMIN`

**Request Body:** `CreateSemesterDTO`

**Response 201:** `SemesterDTO` (mặc định `isCurrent = false`)

---

#### PUT `/api/v1/semesters/{id}` — Cập nhật học kỳ

**Xác thực:** role `ADMIN`

**Request Body:** `UpdateSemesterDTO` (không cho trùng tên trong cùng năm học)

---

#### PUT `/api/v1/semesters/{id}/set-current` — Đánh dấu học kỳ hiện tại

**Xác thực:** role `ADMIN`

**Mô tả:** Học kỳ này thành `isCurrent = true`; tất cả học kỳ khác tự động thành `false`.

---

### 3.4. Topic Management

**Base path:** `/api/v1/subjects/{subjectId}/topics`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/subjects/{subjectId}/topics` — Danh sách chương mục

**Xác thực:** Không có `@PreAuthorize` rõ ràng (mọi người dùng đã đăng nhập)

**Response 200:** `List<TopicDTO>`

---

#### GET `/api/v1/subjects/{subjectId}/topics/{topicId}` — Chi tiết chương mục

---

#### POST `/api/v1/subjects/{subjectId}/topics` — Tạo chương mục mới

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `CreateTopicDTO` (`subjectId` tự động điền từ path)

**Response 201:** `TopicDTO`

---

#### PUT `/api/v1/subjects/{subjectId}/topics/{topicId}` — Cập nhật chương mục

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `CreateTopicDTO`

---

#### DELETE `/api/v1/subjects/{subjectId}/topics/{topicId}` — Xóa chương mục

**Xác thực:** role `ADMIN`

---

### 3.5. Class Management

**Base path:** `/api/v1/classes`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/classes` — Danh sách lớp học

**Xác thực:** role `ADMIN`, `INSTRUCTOR`, `TA`

**Mô tả:** Admin thấy hết, Giảng viên chỉ thấy lớp của mình (logic trong service).

**Query Params:**

| Param | Kiểu | Mặc định | Mô tả |
|---|---|---|---|
| `subjectId` | UUID | null | Lọc theo môn học |
| `semesterId` | UUID | null | Lọc theo học kỳ |
| `status` | ClassStatus | null | `DRAFT`, `ACTIVE`, `COMPLETED`, `ARCHIVED` |
| `page` | int | 0 | Trang |
| `size` | int | 20 | Kích thước trang |

**Response 200:** `Page<ClassDTO>`

---

#### GET `/api/v1/classes/{id}` — Chi tiết lớp học

**Xác thực:** role `ADMIN`, `INSTRUCTOR`, `TA` (có kiểm tra quyền sở hữu)

---

#### POST `/api/v1/classes` — Tạo lớp học mới

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Mô tả:** Nếu là INSTRUCTOR tạo, họ tự động làm chủ lớp.

**Request Body:** `CreateClassDTO`

**Response 201:** `ClassDTO`

---

#### PUT `/api/v1/classes/{id}` — Cập nhật lớp học

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR` (chỉ Admin hoặc chủ lớp)

**Request Body:** `UpdateClassDTO`

---

#### PUT `/api/v1/classes/{id}/status` — Đổi trạng thái lớp

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `UpdateClassStatusDTO`

**Luồng trạng thái:** `DRAFT → ACTIVE → COMPLETED → ARCHIVED`

---

#### GET `/api/v1/classes/{id}/staff` — Danh sách nhân sự lớp

**Xác thực:** role `ADMIN`, `INSTRUCTOR`, `TA`

**Response 200:** `List<ClassStaffDTO>`

---

#### POST `/api/v1/classes/{id}/staff` — Phân công nhân sự

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR` (chỉ Admin hoặc chủ lớp)

**Request Body:** `AssignStaffDTO`

**Response 201:** Thành công

---

#### DELETE `/api/v1/classes/{id}/staff/{userId}` — Xóa nhân sự

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

---

#### GET `/api/v1/classes/{id}/students` — Danh sách sinh viên lớp

**Xác thực:** role `ADMIN`, `INSTRUCTOR`, `TA`

**Query Params:**

| Param | Kiểu | Mặc định |
|---|---|---|
| `status` | EnrollmentStatus | null — `ACTIVE`, `DROPPED`, `COMPLETED` |
| `page` | int | 0 |
| `size` | int | 50 |

**Response 200:** `Page<EnrollmentDTO>`

---

#### POST `/api/v1/classes/{id}/enroll-single` — Ghi danh 1 sinh viên

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `SingleEnrollmentDTO`

**Response 201:** Thành công

---

#### POST `/api/v1/classes/{id}/enroll-bulk` — Ghi danh hàng loạt

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `BulkEnrollmentDTO` (tối đa 500 sinh viên)

**Response 201:** Thành công

---

#### PUT `/api/v1/classes/{id}/students/{studentId}/status` — Đổi trạng thái ghi danh

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `UpdateEnrollmentStatusDTO` (status: `ACTIVE` | `DROPPED` | `COMPLETED`)

---

#### DELETE `/api/v1/classes/{id}/students/{studentId}` — Xóa sinh viên khỏi lớp

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Mô tả:** Xóa cứng (hard delete).

---

#### GET `/api/v1/classes/{id}/activity-logs` — Nhật ký hoạt động lớp

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Response 200:** `List<ActivityLog>`

---

### 3.6. Student Classes (Sinh viên)

**Base path:** `/api/v1/students/me`

---

#### GET `/api/v1/students/me/classes` — Lớp học của tôi

**Xác thực:** role `STUDENT`

**Query Params:** `page` (mặc định 0), `size` (mặc định 20)

**Response 200:** `Page<ClassDTO>`

---

### 3.7. Question Bank

**Base path:** `/api/v1/questions`  
**Xác thực:** Bearer Token, role `ADMIN` hoặc `INSTRUCTOR`

---

#### GET `/api/v1/questions` — Danh sách câu hỏi

**Query Params:**

| Param | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `subjectId` | UUID | ✓ | Bộ lọc theo môn học |
| `topicId` | UUID | | Bộ lọc theo chương mục |
| `difficultyLevel` | DifficultyLevel | | `EASY`, `MEDIUM`, `HARD` |
| `page` | int | | Mặc định 0 |
| `size` | int | | Mặc định 10 |

**Response 200:** `Page<QuestionBankDTO>`

---

#### GET `/api/v1/questions/{questionId}` — Chi tiết câu hỏi

**Response 200:** `QuestionBankDTO` (bao gồm danh sách đáp án A/B/C/D và giải thích)

---

#### POST `/api/v1/questions` — Tạo câu hỏi mới

**Request Body:**
```json
{
  "subjectId": "uuid",
  "topicId": "uuid",
  "questionType": "MCQ_SINGLE",
  "content": "Vận tốc ánh sáng trong chân không là bao nhiêu?",
  "mediaUrl": null,
  "difficultyLevel": "MEDIUM",
  "cognitiveLevel": "Nhớ",
  "options": [
    {
      "content": "3×10⁸ m/s",
      "isCorrect": true,
      "orderIndex": 1,
      "explanation": "Đây là tốc độ ánh sáng trong chân không."
    },
    {
      "content": "3×10⁶ m/s",
      "isCorrect": false,
      "orderIndex": 2,
      "explanation": null
    }
  ]
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `subjectId` | UUID | ✓ | `@NotNull` |
| `topicId` | UUID | ✓ | `@NotNull` |
| `questionType` | enum | ✓ | `@NotNull` — `MCQ_SINGLE`, `MCQ_MULTI`, `TRUE_FALSE`, `SHORT_ANSWER` |
| `content` | string | ✓ | `@NotBlank` |
| `difficultyLevel` | enum | ✓ | `@NotNull` — `EASY`, `MEDIUM`, `HARD` |
| `cognitiveLevel` | string | | Cấp độ Bloom (tự do nhập) |
| `options[].content` | string | ✓ | `@NotBlank` |
| `options[].isCorrect` | boolean | ✓ | `@NotNull` |

**Response 201:** `QuestionBankDTO`

---

#### PUT `/api/v1/questions/{questionId}` — Cập nhật câu hỏi

**Request Body:** `CreateQuestionDTO` (tương tự tạo mới)

---

#### PUT `/api/v1/questions/{questionId}/approve` — Phê duyệt câu hỏi

**Xác thực:** role `ADMIN`

**Mô tả:** Chuyển trạng thái câu hỏi thành `APPROVED` để sẵn sàng sinh đề.

---

#### DELETE `/api/v1/questions/{questionId}` — Xóa câu hỏi

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

---

#### POST `/api/v1/questions/import-pdf` — Nhập câu hỏi từ PDF (AI OCR)

**Content-Type:** `multipart/form-data`

**Form Params:**

| Param | Kiểu | Bắt buộc |
|---|---|---|
| `file` | MultipartFile | ✓ |
| `subjectId` | UUID | ✓ |
| `topicId` | UUID | ✓ |

**Response 201:** `QuestionImportResultDTO`

---

### 3.8. Exam Management

**Base path:** `/api/v1/exams`  
**Xác thực:** Bearer Token (tất cả endpoint)

---

#### POST `/api/v1/exams` — Tạo kỳ thi mới

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:**
```json
{
  "classId": "uuid",
  "matrixId": "uuid",
  "title": "Kiểm tra giữa kỳ - Vật lý 1",
  "examType": "MIDTERM",
  "durationMinutes": 60,
  "startTime": "2026-10-15T07:00:00Z",
  "endTime": "2026-10-15T09:00:00Z"
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `classId` | UUID | ✓ | `@NotNull` |
| `matrixId` | UUID | | Ma trận đề (tuỳ chọn) |
| `title` | string | ✓ | `@NotBlank` |
| `examType` | enum | ✓ | `@NotNull` — `PRACTICE`, `QUIZ`, `MIDTERM`, `FINAL` |
| `durationMinutes` | int | ✓ | `@NotNull` |
| `startTime` | Instant | | Thời gian bắt đầu |
| `endTime` | Instant | | Thời gian kết thúc |

**Response 201:** `ExamDTO`

---

#### GET `/api/v1/exams/class/{classId}` — Danh sách kỳ thi của lớp

**Xác thực:** role `ADMIN`, `INSTRUCTOR`, `TA`, `STUDENT`

**Mô tả:** Kết quả trả về theo phân quyền (sinh viên không thấy đáp án).

**Response 200:** `List<ExamDTO>`

---

#### GET `/api/v1/exams/{examId}` — Chi tiết kỳ thi

**Xác thực:** Chỉ yêu cầu Bearer Token (không có `@PreAuthorize` rõ ràng — kiểm tra trong service)

**Response 200:** `ExamDTO`

---

#### POST `/api/v1/exams/{examId}/questions` — Thêm câu hỏi thủ công

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `AddExamQuestionDTO`

---

#### POST `/api/v1/exams/{examId}/generate-questions` — Tự động sinh đề

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Mô tả:** Lấy câu hỏi ngẫu nhiên từ ngân hàng theo ma trận và tỉ lệ Bloom.

**Response 200:**
```json
{
  "status": 200,
  "message": "Questions generated successfully",
  "data": {
    "questionsAdded": 30
  }
}
```

---

#### POST `/api/v1/exams/{examId}/attempts` — Sinh viên bắt đầu làm bài

**Xác thực:** role `STUDENT`

**Mô tả:** Tạo lượt làm bài mới. Logic multi-attempt:
- `PRACTICE`: cho phép nhiều lượt, chặn 409 nếu lượt hiện tại còn `IN_PROGRESS`
- `QUIZ`, `MIDTERM`, `FINAL`: chỉ 1 lượt, thi lại bị từ chối 409

**Response 201:** `ExamAttemptDTO`

| Status | Mô tả |
|---|---|
| 201 | Tạo lượt thi thành công |
| 409 | Đã có lượt thi `IN_PROGRESS` hoặc đã thi (QUIZ/MIDTERM/FINAL) |

---

#### POST `/api/v1/exams/attempts/{attemptId}/answers` — Lưu câu trả lời tạm thời

**Mô tả:** Ghi nhận phương án trả lời cho từng câu hỏi trong quá trình làm bài thi. Sinh viên có thể gọi API này nhiều lần trước khi nộp bài.

**Xác thực:** Bắt buộc (Role `STUDENT`)  
**Header:** `Authorization: Bearer <accessToken>`  
**Content-Type:** `application/json`

**Path Variables:**
- `attemptId` (UUID, bắt buộc): Định danh lượt làm bài thi đang `IN_PROGRESS`.

**Request Body (`SubmitAnswerDTO`):**
| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc | Mô tả |
|---|---|---|---|---|
| `questionId` | `UUID` | ✓ Bắt buộc | `@NotNull` | Định danh câu hỏi trắc nghiệm |
| `selectedOptionIds` | `List<UUID>` | Tùy chọn | — | Danh sách UUID phương án trắc nghiệm sinh viên chọn |
| `answerText` | `String` | Tùy chọn | — | Nội dung trả lời tự luận ngắn (nếu câu hỏi yêu cầu) |

```json
{
  "questionId": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
  "selectedOptionIds": ["a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"],
  "answerText": null
}
```

**Phản hồi thành công (HTTP 200 OK):**
```json
{
  "status": 200,
  "message": "Answer saved",
  "data": null
}
```

---

#### PUT `/api/v1/exams/attempts/{attemptId}/submit` — Nộp bài và chấm điểm

**Xác thực:** role `STUDENT`

**Mô tả:** Dùng `SELECT FOR UPDATE` (Pessimistic Write Lock) chống race condition. Tự động chấm điểm.

**Công thức tính điểm MCQ:**
```
Score_10 = (Số câu đúng / Tổng số câu) × 10
```

**Response 200:** `ExamAttemptDTO` (kèm `totalScore` và `status = GRADED`)

---

#### GET `/api/v1/exams/{examId}/my-attempt` — Lượt thi gần nhất của tôi

**Xác thực:** role `STUDENT`

**Response 200:** `ExamAttemptDTO`

---

#### GET `/api/v1/exams/{examId}/my-attempts` — Toàn bộ lịch sử các lượt thi của sinh viên

**Xác thực:** role `STUDENT`

**Mô tả:** Trả về toàn bộ danh sách các lượt làm bài của sinh viên đối với đề thi (sắp xếp tăng dần theo `attemptNumber`). Đặc biệt quan trọng cho các bài thi luyện tập (`PRACTICE`) cho phép sinh viên thử sức nhiều lần.

**Response 200:** `ApiResponse<List<ExamAttemptDTO>>`

---

#### GET `/api/v1/exams/attempts/{attemptId}` — Chi tiết lượt thi

**Xác thực:** role `ADMIN`, `INSTRUCTOR`, `TA`, `STUDENT`

**Mô tả:** Sinh viên chỉ xem lượt thi của bản thân (kiểm tra trong service).

**Response 200:** `ExamAttemptDTO`

---

**Model ExamAttemptDTO:**

| Field | Kiểu | Mô tả |
|---|---|---|
| `attemptId` | UUID | ID lượt thi |
| `examId` | UUID | ID kỳ thi |
| `studentId` | UUID | ID sinh viên |
| `attemptNumber` | int | Số thứ tự lượt (1, 2, 3...) |
| `startedAt` | Instant | Thời gian bắt đầu |
| `submittedAt` | Instant | Thời gian nộp |
| `status` | AttemptStatus | `IN_PROGRESS`, `SUBMITTED`, `GRADED` |
| `totalScore` | BigDecimal | Điểm số (thang 10) |

---

### 3.9. Virtual Physics Lab (Thí Nghiệm Ảo)

**Base path:** `/api/v1/experiments`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/experiments` — Danh sách thí nghiệm theo môn học

**Query Params:**

| Param | Kiểu | Bắt buộc |
|---|---|---|
| `subjectId` | UUID | ✓ |

**Response 200:** `List<ExperimentDTO>` (04 bài thí nghiệm ảo 3D Vật lý 1)

---

#### GET `/api/v1/experiments/{experimentId}` — Chi tiết thí nghiệm

**Response 200:** `ExperimentDTO` (cấu hình, tiêu chí Rubric, thông số)

---

#### POST `/api/v1/experiments` — Tạo thí nghiệm mới

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:**
```json
{
  "subjectId": "uuid",
  "title": "Thí nghiệm đo gia tốc trọng trường",
  "description": "Mô tả chi tiết...",
  "sceneAssetUrl": "https://storage.example.com/scenes/gravity.glb",
  "sceneAssetsJson": { ... },
  "instructions": "Hướng dẫn thực hiện thí nghiệm...",
  "orderIndex": 1
}
```

| Field | Kiểu | Bắt buộc | Validation |
|---|---|---|---|
| `subjectId` | UUID | ✓ | `@NotNull` |
| `title` | string | ✓ | `@NotBlank` |
| `description` | string | | |
| `sceneAssetUrl` | string | | URL file 3D scene |
| `sceneAssetsJson` | JsonNode | | JSON cấu hình scene |
| `instructions` | string | | |
| `orderIndex` | int | | Thứ tự hiển thị |

**Response 201:** `ExperimentDTO`

---

#### POST `/api/v1/experiments/{experimentId}/assign` — Giao thí nghiệm cho lớp

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Request Body:** `CreateExperimentAssignmentDTO` (classId, deadline)

**Response 201:** `ExperimentAssignmentDTO`

---

#### POST `/api/v1/experiments/assignments/{assignmentId}/submit` — Sinh viên nộp kết quả

**Xác thực:** role `STUDENT`

**Content-Type:** `multipart/form-data`

**Form Params:**

| Param | Kiểu | Mô tả |
|---|---|---|
| `evidenceUrl` | string | URL file minh chứng (nếu upload sẵn) |
| `file` | MultipartFile | File upload trực tiếp |
| `rawDataJson` | JsonNode | Số liệu đo đạc dạng JSON |

**Response 200:** Thành công

---

#### POST `/api/v1/experiments/submissions/{submissionId}/scores` — Chấm điểm Rubric bài nộp thí nghiệm ảo

**Xác thực:** role `INSTRUCTOR` hoặc `TA`  
**Content-Type:** `application/json`

**Request Body (`GradeSubmissionDTO`):**
| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `rubricId` | `UUID` | Tùy chọn | ID tiêu chí Rubric áp dụng |
| `score` | `BigDecimal` | Tùy chọn | Điểm số chấm cho bài nộp thí nghiệm ảo (thang 10) |
| `feedback` | `String` | Tùy chọn | Nhận xét chi tiết của giảng viên / trợ giảng |
| `comment` | `String` | Tùy chọn | Ghi chú bổ sung (hỗ trợ tương thích ngược) |

```json
{
  "rubricId": "b1a2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "score": 9.5,
  "feedback": "Thao tác đo đạc chính xác, số liệu tính toán đạt độ tin cậy cao."
}
```

**Mô tả:** Chấm điểm 3D Lab theo công thức Rubric:
```
Score_lab = Σ(điểm_tiêu_chí_i × trọng_số_i)
```

**Response 200:**
```json
{
  "status": 200,
  "message": "Grade submitted successfully",
  "data": { ... }
}
```

---

#### POST `/api/v1/experiments/submissions/{submissionId}/confirmation` — Xác nhận và phê duyệt kết quả thí nghiệm

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`  
**Content-Type:** `application/json`

**Request Body (`ConfirmSubmissionDTO`):**
| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `note` | `String` | Tùy chọn | Ghi chú phê duyệt kết quả và chốt điểm chính thức |

```json
{
  "note": "Kết quả đạt yêu cầu, điểm chính thức được chốt."
}
```

**Mô tả:** Phê duyệt và chốt điểm chính thức cho sinh viên.

---

### 3.10. Learning Material (Học Liệu Số)

**Base path:** `/api/v1/topics/{topicId}/materials`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/topics/{topicId}/materials` — Danh sách học liệu

**Xác thực:** Mọi người dùng đã đăng nhập (không có `@PreAuthorize` rõ ràng — logic phân quyền trong service theo role)

**Response 200:** `List<LearningMaterialDTO>` (PDF, Video, Slide, Text, Other)

---

#### GET `/api/v1/topics/{topicId}/materials/{materialId}` — Chi tiết học liệu

**Response 200:** `LearningMaterialDTO`

---

#### POST `/api/v1/topics/{topicId}/materials` — Tạo học liệu mới

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Content-Type:** `multipart/form-data`

**Form Params:** `CreateLearningMaterialDTO` (`topicId` tự động từ path)

**Response 201:** `LearningMaterialDTO`

---

#### PUT `/api/v1/topics/{topicId}/materials/{materialId}` — Cập nhật học liệu

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Content-Type:** `multipart/form-data`

---

#### PUT `/api/v1/topics/{topicId}/materials/{materialId}/approve` — Phê duyệt học liệu

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

**Mô tả:** Kiểm duyệt và xuất bản học liệu cho sinh viên truy cập.

---

#### DELETE `/api/v1/topics/{topicId}/materials/{materialId}` — Xóa học liệu

**Xác thực:** role `ADMIN` hoặc `INSTRUCTOR`

---

### 3.11. Evidence (Minh Chứng Thí Nghiệm)

**Base path:** `/api/v1`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/students/me/evidence` — Kho minh chứng của tôi

**Xác thực:** role `STUDENT`

**Response 200:** `List<EvidenceDTO>`

---

#### GET `/api/v1/students/{id}/evidence` — Minh chứng của sinh viên

**Xác thực:** role `INSTRUCTOR`, `ADMIN`, `STUDENT`

**Mô tả:** Kiểm tra IDOR — sinh viên chỉ xem của mình; GV/Admin xem của sinh viên trong lớp.

---

#### GET `/api/v1/classes/{id}/evidence` — Minh chứng toàn bộ lớp

**Xác thực:** role `INSTRUCTOR`, `ADMIN`

**Response 200:** `List<EvidenceDTO>`

---

### 3.12. Learning Progress

---

#### GET `/api/v1/classes/{classId}/progress` — Tiến độ học liệu của lớp

**Xác thực:** role `ADMIN`, `INSTRUCTOR`

**Response 200:** `List<LearningProgressDTO>`

---

#### GET `/api/v1/students/me/progress` — Tiến độ học tập của tôi

**Xác thực:** role `STUDENT`

**Query Params:** `classId` (UUID, bắt buộc)

**Response 200:** `List<LearningProgressDTO>`

---

#### PUT `/api/v1/students/me/progress` — Cập nhật tiến độ học tập

**Xác thực:** role `STUDENT`

**Request Body:** `UpdateLearningProgressDTO` (trạng thái hoàn thành, thời gian tương tác)

---

### 3.13. Dashboard

**Base path:** `/api/v1/dashboard`  
**Xác thực:** Bearer Token

---

#### GET `/api/v1/dashboard/class/{id}` — Bảng điều khiển lớp học

**Xác thực:** role `INSTRUCTOR`, `ADMIN`

**Response 200:** `DashboardSnapshotDTO` (biểu đồ phân bố điểm, tỉ lệ hoàn thành, chỉ số then chốt)

---

#### GET `/api/v1/dashboard/class/{id}/student/{studentId}` — Bảng điều khiển sinh viên

**Xác thực:** role `INSTRUCTOR`, `ADMIN`

---

#### GET `/api/v1/dashboard/me` — Bảng điều khiển học tập cá nhân

**Xác thực:** role `STUDENT`

**Response 200:** `DashboardSnapshotDTO` (điểm số, xếp hạng, lộ trình hoàn thành)

---

#### POST `/api/v1/dashboard/class/{id}/regenerate` — Tạo lại dữ liệu snapshot

**Xác thực:** role `ADMIN`

**Response 200:** `DashboardSnapshotDTO` (tính toán lại tức thời)

---

### 3.14. Analytics & CTT

**Base path:** `/api/v1/analytics`  
**Xác thực:** Bearer Token, role `INSTRUCTOR` hoặc `ADMIN`

---

#### GET `/api/v1/analytics/topic-difficulty` — Độ khó chương mục

**Mô tả:** Tính điểm trung bình, tỉ lệ sai sót theo lý thuyết CTT (Classical Test Theory).

**Query Params:**

| Param | Kiểu | Mô tả |
|---|---|---|
| `classId` | UUID | Lọc theo lớp |
| `subjectId` | UUID | Lọc theo môn học |
| `period` | string | Kỳ thống kê |

**Response 200:** `List<TopicDifficultyDTO>`

---

#### GET `/api/v1/analytics/question-quality` — Chất lượng câu hỏi (DI)

**Mô tả:** Tính tỉ lệ đúng (`p-value`), Discrimination Index (DI) và nhãn chất lượng.

**Query Params:**

| Param | Kiểu | Mô tả |
|---|---|---|
| `subjectId` | UUID | Lọc theo môn học |
| `topicId` | UUID | Lọc theo chương mục |
| `minUsed` | int | Số lần sử dụng tối thiểu |

**Response 200:** `List<QuestionQualityDTO>`

---

#### GET `/api/v1/analytics/ai-gaps` — Lỗ hổng kiến thức AI

**Mô tả:** Tổng hợp các chủ đề sinh viên hay bị AI từ chối giải đáp hoặc hỏi nhiều nhất.

**Query Params:** `subjectId`, `period`

**Response 200:** `List<AiTopicGapDTO>`

---

#### GET `/api/v1/analytics/material-effectiveness` — Hiệu quả học liệu

**Mô tả:** Đo thời gian đọc, lượt xem và mức độ cải thiện điểm tương quan.

**Query Params:** `subjectId`, `topicId`, `period`

**Response 200:** `List<MaterialEffectivenessDTO>`

---

#### POST `/api/v1/analytics/trigger` — Kích hoạt tổng hợp CTT thủ công

**Xác thực:** role `ADMIN`

**Request Body:**
```json
{
  "period": "2026-HK1"
}
```

**Response 204:** No Content (tác vụ bất đồng bộ)

> **Lưu ý:** Hệ thống cũng có `AnalyticsCronJob` tự động chạy lúc 01:00 AM hàng ngày.

---

### 3.15. AI Socratic Tutor

**Base path:** `/api/v1/ai-tutor`  
**Xác thực:** Bearer Token, role `STUDENT`

---

#### POST `/api/v1/ai-tutor/conversations` — Khởi tạo phiên hội thoại

**Request Body:** `StartAiConversationDTO`

**Response 201:** `AiConversationDTO`

---

#### POST `/api/v1/ai-tutor/conversations/{conversationId}/messages` — Gửi câu hỏi cho AI

**Request Body:** `SendAiMessageDTO`

**Response 200:** `AiMessageDTO` (phản hồi gợi mở kèm trích dẫn tài liệu chính thức)

---

#### GET `/api/v1/ai-tutor/conversations/{conversationId}/messages` — Lịch sử hội thoại

**Response 200:** `List<AiMessageDTO>`

---

#### GET `/api/v1/ai-tutor/conversations/my` — Danh sách phiên hội thoại của tôi

**Response 200:** `List<AiConversationDTO>`

---

#### PUT `/api/v1/ai-tutor/conversations/{conversationId}/end` — Kết thúc phiên hội thoại

**Response 200:** `AiConversationDTO` (trạng thái đã đóng)

---

#### POST `/api/v1/ai-tutor/messages/{messageId}/feedback` — Đánh giá câu trả lời AI

**Request Body:** `AiFeedbackDTO`

**Response 200:** Ghi nhận đánh giá

---

### 3.16. Admin Logs

**Base path:** `/api/v1/admin`  
**Xác thực:** Bearer Token, role `ADMIN`

---

#### GET `/api/v1/admin/activity-logs` — Nhật ký hoạt động hệ thống

**Query Params:**

| Param | Kiểu | Mô tả |
|---|---|---|
| `userId` | UUID | Lọc theo người dùng |
| `actionType` | string | Loại hành động |
| `startDate` | Instant (ISO) | Từ ngày |
| `endDate` | Instant (ISO) | Đến ngày |
| `page`, `size`, `sort` | Pageable | Phân trang |

**Response 200:** `Page<ActivityLog>`

---

#### GET `/api/v1/admin/audit-logs` — Nhật ký kiểm toán bảo mật

**Query Params:** `entity`, `userId`, Pageable

**Response 200:** `Page<AuditLog>`

---

### 3.17. System Settings

**Base path:** `/api/v1/admin/settings`  
**Xác thực:** Bearer Token, role `ADMIN`

---

#### GET `/api/v1/admin/settings` — Toàn bộ cấu hình hệ thống

**Response 200:** `List<SystemSetting>`

---

#### GET `/api/v1/admin/settings/{key}` — Lấy một cấu hình

**Path Param:** `key` (string, ví dụ: `exam.practice.max_attempts`)

---

#### PUT `/api/v1/admin/settings/{key}` — Cập nhật một cấu hình

**Request Body:** `UpdateSettingRequest`

---

#### POST `/api/v1/admin/settings/bulk` — Cập nhật nhiều cấu hình

**Request Body:** `BulkUpdateSettingsRequest`

> **Ví dụ key hữu ích:** `exam.practice.max_attempts` — giới hạn số lượt luyện tập tối đa (cấu hình động, không cần sửa code)

---

### 3.18. Student Activity Logs

---

#### GET `/api/v1/students/me/activity-logs` — Nhật ký hoạt động cá nhân

**Xác thực:** role `STUDENT`

**Response 200:** `List<ActivityLog>`

---

## 4. Bảng Tổng Hợp Tham Chiếu 107 Endpoint

Bảng tổng hợp tra cứu nhanh toàn bộ **107 endpoints** (106 API phân hệ nghiệp vụ + 1 endpoint giám sát Actuator) với phương thức HTTP, đường dẫn URL, yêu cầu phân quyền và tóm tắt chức năng:

| STT | Phân Hệ / Nhóm | Method | Đường Dẫn API (Endpoint URI) | Phân Quyền (@PreAuthorize) | Tóm Tắt Chức Năng |
|:---:|---|:---:|---|---|---|
| 1 | Xác thực & Người dùng | `POST` | `/api/v1/users/signin` | Public (Permit All) | Đăng nhập hệ thống bằng username/password, cấp JWT & Refresh Token |
| 2 | Xác thực & Người dùng | `POST` | `/api/v1/users/signup` | Public (Permit All) | Đăng ký tài khoản sinh viên mới |
| 3 | Xác thực & Người dùng | `POST` | `/api/v1/users/refresh` | Public (Permit All) | Xoay vòng Refresh Token lấy Access Token mới |
| 4 | Xác thực & Người dùng | `POST` | `/api/v1/users/logout` | Authenticated | Đăng xuất, thu hồi Refresh Token hiện hành |
| 5 | Xác thực & Người dùng | `POST` | `/api/v1/users/forgot-password` | Public (Permit All) | Gửi email liên kết token đặt lại mật khẩu |
| 6 | Xác thực & Người dùng | `POST` | `/api/v1/users/reset-password` | Public (Permit All) | Đặt lại mật khẩu mới bằng reset token |
| 7 | Xác thực & Người dùng | `GET` | `/api/v1/users/me` | Authenticated | Xem thông tin tài khoản đang đăng nhập |
| 8 | Xác thực & Người dùng | `PUT` | `/api/v1/users/me` | Authenticated | Cập nhật thông tin cơ bản tài khoản |
| 9 | Xác thực & Người dùng | `GET` | `/api/v1/users/me/profile` | Authenticated | Xem hồ sơ chi tiết (Avatar, Bio, Ngày sinh, Giới tính) |
| 10 | Xác thực & Người dùng | `PUT` | `/api/v1/users/me/profile` | Authenticated | Cập nhật hồ sơ chi tiết cá nhân |
| 11 | Xác thực & Người dùng | `PUT` | `/api/v1/users/me/password` | Authenticated | Đổi mật khẩu tài khoản cá nhân |
| 12 | Quản trị Người dùng | `POST` | `/api/v1/users/admin/create-user` | `ADMIN` | Quản trị viên tạo tài khoản mới với vai trò tùy chọn |
| 13 | Quản trị Người dùng | `GET` | `/api/v1/users/admin/users` | `ADMIN` | Tra cứu danh sách người dùng có phân trang và lọc role |
| 14 | Quản trị Người dùng | `GET` | `/api/v1/users/admin/users/{id}/profile` | `ADMIN` | Xem hồ sơ chi tiết của người dùng bất kỳ |
| 15 | Quản trị Người dùng | `PUT` | `/api/v1/users/admin/users/{id}` | `ADMIN` | Cập nhật thông tin người dùng bởi Quản trị viên |
| 16 | Quản trị Người dùng | `PUT` | `/api/v1/users/admin/users/{id}/status` | `ADMIN` | Khóa hoặc mở khóa trạng thái tài khoản (ACTIVE/LOCKED) |
| 17 | Quản trị Người dùng | `GET` | `/api/v1/users/{username}` | `ADMIN` | Tra cứu tài khoản theo username |
| 18 | Quản trị Người dùng | `DELETE` | `/api/v1/users/{username}` | `ADMIN` | Xóa vĩnh viễn tài khoản người dùng khỏi hệ thống |
| 19 | Môn học (Subject) | `GET` | `/api/v1/subjects` | Authenticated | Lấy danh sách môn học có phân trang |
| 20 | Môn học (Subject) | `POST` | `/api/v1/subjects` | `ADMIN` | Tạo môn học mới (Vật lý 1) |
| 21 | Môn học (Subject) | `GET` | `/api/v1/subjects/{id}` | Authenticated | Lấy chi tiết thông tin môn học |
| 22 | Môn học (Subject) | `PUT` | `/api/v1/subjects/{id}` | `ADMIN` | Cập nhật thông tin môn học |
| 23 | Môn học (Subject) | `DELETE` | `/api/v1/subjects/{id}` | `ADMIN` | Xóa môn học |
| 24 | Học kỳ (Semester) | `GET` | `/api/v1/semesters` | Authenticated | Lấy danh sách tất cả học kỳ |
| 25 | Học kỳ (Semester) | `POST` | `/api/v1/semesters` | `ADMIN` | Tạo học kỳ niên khóa mới |
| 26 | Học kỳ (Semester) | `GET` | `/api/v1/semesters/{id}` | Authenticated | Lấy chi tiết học kỳ |
| 27 | Học kỳ (Semester) | `PUT` | `/api/v1/semesters/{id}` | `ADMIN` | Cập nhật thông tin học kỳ |
| 28 | Học kỳ (Semester) | `DELETE` | `/api/v1/semesters/{id}` | `ADMIN` | Xóa học kỳ |
| 29 | Chương mục (Topic) | `GET` | `/api/v1/subjects/{subjectId}/topics` | Authenticated | Lấy danh sách chương mục kiến thức môn học |
| 30 | Chương mục (Topic) | `POST` | `/api/v1/subjects/{subjectId}/topics` | `ADMIN` | Tạo chương mục kiến thức mới |
| 31 | Chương mục (Topic) | `GET` | `/api/v1/subjects/{subjectId}/topics/{id}` | Authenticated | Lấy chi tiết chương mục kiến thức |
| 32 | Chương mục (Topic) | `PUT` | `/api/v1/subjects/{subjectId}/topics/{id}` | `ADMIN` | Cập nhật chương mục kiến thức |
| 33 | Chương mục (Topic) | `DELETE` | `/api/v1/subjects/{subjectId}/topics/{id}` | `ADMIN` | Xóa chương mục kiến thức |
| 34 | Lớp học phần (Class) | `GET` | `/api/v1/classes` | Authenticated | Lấy danh sách lớp học phần có phân trang |
| 35 | Lớp học phần (Class) | `POST` | `/api/v1/classes` | `ADMIN` | Tạo lớp học phần mới |
| 36 | Lớp học phần (Class) | `GET` | `/api/v1/classes/{id}` | Authenticated | Lấy chi tiết lớp học phần |
| 37 | Lớp học phần (Class) | `PUT` | `/api/v1/classes/{id}` | `ADMIN`, `INSTRUCTOR` | Cập nhật thông tin lớp học phần |
| 38 | Lớp học phần (Class) | `DELETE` | `/api/v1/classes/{id}` | `ADMIN` | Xóa lớp học phần |
| 39 | Lớp học phần (Class) | `POST` | `/api/v1/classes/{id}/staff` | `ADMIN` | Phân công Giảng viên / Trợ giảng vào lớp |
| 40 | Lớp học phần (Class) | `DELETE` | `/api/v1/classes/{id}/staff/{userId}` | `ADMIN` | Hủy phân công nhân sự khỏi lớp |
| 41 | Lớp học phần (Class) | `POST` | `/api/v1/classes/{id}/enroll-single` | `ADMIN`, `INSTRUCTOR` | Ghi danh 1 sinh viên vào lớp học phần |
| 42 | Lớp học phần (Class) | `POST` | `/api/v1/classes/{id}/enroll-bulk` | `ADMIN`, `INSTRUCTOR` | Ghi danh danh sách nhiều sinh viên vào lớp |
| 43 | Lớp học phần (Class) | `DELETE` | `/api/v1/classes/{id}/students/{studentId}` | `ADMIN`, `INSTRUCTOR` | Xóa sinh viên khỏi danh sách lớp |
| 44 | Lớp học phần (Class) | `GET` | `/api/v1/classes/{id}/students` | `ADMIN`, `INSTRUCTOR`, `TA` | Lấy danh sách sinh viên ghi danh trong lớp |
| 45 | Lớp học phần (Class) | `GET` | `/api/v1/classes/{id}/activity-logs` | `ADMIN`, `INSTRUCTOR` | Nhật ký hoạt động học tập của toàn lớp |
| 46 | Lớp học phần (Class) | `GET` | `/api/v1/classes/instructor/{instructorId}` | `ADMIN`, `INSTRUCTOR` | Lấy các lớp do giảng viên phụ trách |
| 47 | Lớp học phần (Class) | `GET` | `/api/v1/classes/semester/{semesterId}` | Authenticated | Lấy danh sách lớp theo học kỳ |
| 48 | Lớp học phần cá nhân | `GET` | `/api/v1/students/me/classes` | `STUDENT` | Tra cứu danh sách lớp sinh viên đang theo học |
| 49 | Ngân hàng câu hỏi | `GET` | `/api/v1/questions` | `ADMIN`, `INSTRUCTOR` | Lấy danh sách câu hỏi trắc nghiệm có phân trang |
| 50 | Ngân hàng câu hỏi | `POST` | `/api/v1/questions` | `ADMIN`, `INSTRUCTOR` | Tạo câu hỏi trắc nghiệm mới |
| 51 | Ngân hàng câu hỏi | `GET` | `/api/v1/questions/{id}` | `ADMIN`, `INSTRUCTOR` | Lấy chi tiết câu hỏi và các phương án |
| 52 | Ngân hàng câu hỏi | `PUT` | `/api/v1/questions/{id}` | `ADMIN`, `INSTRUCTOR` | Cập nhật nội dung câu hỏi và đáp án |
| 53 | Ngân hàng câu hỏi | `DELETE` | `/api/v1/questions/{id}` | `ADMIN` | Xóa câu hỏi khỏi ngân hàng |
| 54 | Ngân hàng câu hỏi | `PUT` | `/api/v1/questions/{id}/status` | `ADMIN` | Phê duyệt hoặc từ chối câu hỏi (ApprovalStatus) |
| 55 | Ngân hàng câu hỏi | `POST` | `/api/v1/questions/import-pdf` | `ADMIN`, `INSTRUCTOR` | Tải lên đề thi PDF, AI tự trích xuất câu hỏi |
| 56 | Kỳ thi & Khảo sát | `POST` | `/api/v1/exams` | `ADMIN`, `INSTRUCTOR` | Thiết lập kỳ thi mới (thời gian, loại thi, ma trận) |
| 57 | Kỳ thi & Khảo sát | `POST` | `/api/v1/exams/{examId}/questions` | `ADMIN`, `INSTRUCTOR` | Thêm câu hỏi thủ công từ ngân hàng vào đề thi |
| 58 | Kỳ thi & Khảo sát | `POST` | `/api/v1/exams/{examId}/generate-questions` | `ADMIN`, `INSTRUCTOR` | Tự động sinh ngẫu nhiên câu hỏi theo ma trận Bloom |
| 59 | Kỳ thi & Khảo sát | `GET` | `/api/v1/exams/class/{classId}` | Authenticated | Lấy danh sách đề thi của lớp học |
| 60 | Kỳ thi & Khảo sát | `GET` | `/api/v1/exams/{examId}` | Authenticated | Xem chi tiết cấu hình kỳ thi |
| 61 | Kỳ thi & Khảo sát | `POST` | `/api/v1/exams/{examId}/attempts` | `STUDENT` | Bắt đầu làm bài thi (Tạo lượt thi mới) |
| 62 | Kỳ thi & Khảo sát | `POST` | `/api/v1/exams/attempts/{attemptId}/answers` | `STUDENT` | Lưu câu trả lời trắc nghiệm tạm thời |
| 63 | Kỳ thi & Khảo sát | `PUT` | `/api/v1/exams/attempts/{attemptId}/submit` | `STUDENT` | Nộp bài thi và chấm điểm tự động (Pessimistic Lock) |
| 64 | Kỳ thi & Khảo sát | `GET` | `/api/v1/exams/{examId}/my-attempt` | `STUDENT` | Lấy thông tin lượt làm bài gần nhất của sinh viên |
| 65 | Kỳ thi & Khảo sát | `GET` | `/api/v1/exams/{examId}/my-attempts` | `STUDENT` | Xem toàn bộ lịch sử các lượt thi của sinh viên (multi-attempt) |
| 66 | Kỳ thi & Khảo sát | `GET` | `/api/v1/exams/attempts/{attemptId}` | Authenticated | Xem chi tiết bài thi, bảng điểm và đáp án đã nộp |
| 67 | Thí nghiệm ảo 3D | `GET` | `/api/v1/experiments` | Authenticated | Lấy danh sách các bài thí nghiệm ảo 3D |
| 68 | Thí nghiệm ảo 3D | `POST` | `/api/v1/experiments` | `ADMIN` | Tạo cấu hình bài thí nghiệm ảo 3D mới |
| 69 | Thí nghiệm ảo 3D | `GET` | `/api/v1/experiments/{id}` | Authenticated | Lấy chi tiết bài thí nghiệm và thông số mô phỏng |
| 70 | Thí nghiệm ảo 3D | `POST` | `/api/v1/experiments/{id}/assign` | `ADMIN`, `INSTRUCTOR` | Giao bài thí nghiệm ảo cho lớp học phần |
| 71 | Thí nghiệm ảo 3D | `POST` | `/api/v1/experiments/assignments/{assignmentId}/submit` | `STUDENT` | Sinh viên nộp số liệu đo đạc và báo cáo thí nghiệm |
| 72 | Thí nghiệm ảo 3D | `POST` | `/api/v1/experiments/submissions/{submissionId}/scores` | `INSTRUCTOR`, `TA` | Chấm điểm bài nộp thí nghiệm theo Rubric (`GradeSubmissionDTO`) |
| 73 | Thí nghiệm ảo 3D | `POST` | `/api/v1/experiments/submissions/{submissionId}/confirmation` | `ADMIN`, `INSTRUCTOR` | Phê duyệt và chốt điểm chính thức (`ConfirmSubmissionDTO`) |
| 74 | Học liệu số | `GET` | `/api/v1/topics/{topicId}/materials` | Authenticated | Lấy danh sách tài liệu học tập theo chương mục |
| 75 | Học liệu số | `GET` | `/api/v1/topics/{topicId}/materials/{materialId}` | Authenticated | Xem chi tiết học liệu số |
| 76 | Học liệu số | `POST` | `/api/v1/topics/{topicId}/materials` | `ADMIN`, `INSTRUCTOR` | Tải lên học liệu số mới (PDF, Video, Bài giảng) |
| 77 | Học liệu số | `PUT` | `/api/v1/topics/{topicId}/materials/{materialId}` | `ADMIN`, `INSTRUCTOR` | Cập nhật tài liệu học tập số |
| 78 | Học liệu số | `DELETE` | `/api/v1/topics/{topicId}/materials/{materialId}` | `ADMIN` | Xóa học liệu số khỏi hệ thống |
| 79 | Học liệu số | `PUT` | `/api/v1/topics/{topicId}/materials/{materialId}/status` | `ADMIN` | Phê duyệt kiểm duyệt học liệu (ApprovalStatus) |
| 80 | Kho minh chứng | `GET` | `/api/v1/students/{id}/evidence` | Authenticated (IDOR protected) | Tra cứu kho minh chứng kết quả đo đạc của sinh viên |
| 81 | Kho minh chứng | `POST` | `/api/v1/evidence` | `STUDENT` | Đăng ký bản ghi minh chứng học tập mới |
| 82 | Kho minh chứng | `GET` | `/api/v1/evidence/{id}` | Authenticated | Lấy chi tiết bản ghi minh chứng |
| 83 | Báo cáo tiến độ lớp | `GET` | `/api/v1/classes/{classId}/progress` | `ADMIN`, `INSTRUCTOR` | Báo cáo tỷ lệ hoàn thành học liệu của toàn lớp |
| 84 | Tiến độ học tập cá nhân | `GET` | `/api/v1/students/me/progress` | `STUDENT` | Tra cứu tiến độ học tập các môn của bản thân |
| 85 | Tiến độ học tập cá nhân | `PUT` | `/api/v1/students/me/progress/{materialId}` | `STUDENT` | Cập nhật tiến độ hoàn thành học liệu (0% - 100%) |
| 86 | Dashboard | `GET` | `/api/v1/dashboard/instructor/{classId}` | `ADMIN`, `INSTRUCTOR` | Thống kê tổng hợp lớp cho Giảng viên |
| 87 | Dashboard | `GET` | `/api/v1/dashboard/student` | `STUDENT` | Thống kê tổng quan học tập cho Sinh viên |
| 88 | Dashboard | `GET` | `/api/v1/dashboard/student/{classId}` | `STUDENT` | Thống kê chi tiết kết quả sinh viên trong một lớp |
| 89 | Dashboard | `GET` | `/api/v1/dashboard/instructor/{classId}/export` | `ADMIN`, `INSTRUCTOR` | Xuất bảng điểm và báo cáo tổng hợp ra file Excel/CSV |
| 90 | Phân tích CTT | `GET` | `/api/v1/analytics/exams/{examId}` | `ADMIN`, `INSTRUCTOR` | Báo cáo tổng thể phân tích CTT kỳ thi |
| 91 | Phân tích CTT | `GET` | `/api/v1/analytics/exams/{examId}/questions` | `ADMIN`, `INSTRUCTOR` | Phân tích độ khó (p-value) và độ phân biệt (DI) câu hỏi |
| 92 | Phân tích CTT | `GET` | `/api/v1/analytics/classes/{classId}/weak-topics` | `ADMIN`, `INSTRUCTOR` | Xác định các chương mục kiến thức sinh viên còn yếu |
| 93 | Phân tích CTT | `GET` | `/api/v1/analytics/classes/{classId}/at-risk-students` | `ADMIN`, `INSTRUCTOR` | Danh sách sinh viên có nguy cơ trượt môn cần can thiệp |
| 94 | Phân tích CTT | `POST` | `/api/v1/analytics/trigger` | `ADMIN` | Kích hoạt tác vụ tính toán phân tích thống kê CTT |
| 95 | Trợ giảng AI Socratic | `POST` | `/api/v1/ai-tutor/conversations` | `STUDENT` | Khởi tạo phiên trao đổi hỏi đáp mới với AI Tutor |
| 96 | Trợ giảng AI Socratic | `POST` | `/api/v1/ai-tutor/conversations/{id}/messages` | `STUDENT` | Gửi câu hỏi, AI gợi mở tư duy (phương pháp Socratic tiếng Việt) |
| 97 | Trợ giảng AI Socratic | `GET` | `/api/v1/ai-tutor/conversations/me` | `STUDENT` | Lấy danh sách các phiên trao đổi với AI của sinh viên |
| 98 | Trợ giảng AI Socratic | `GET` | `/api/v1/ai-tutor/conversations/{id}` | `STUDENT` | Xem chi tiết lịch sử tin nhắn của một phiên trò chuyện |
| 99 | Trợ giảng AI Socratic | `POST` | `/api/v1/ai-tutor/transcribe` | `STUDENT` | Nhận dạng giọng nói (Audio to Text) tiếng Việt |
| 100 | Trợ giảng AI Socratic | `GET` | `/api/v1/ai-tutor/audio/{filename}` | Authenticated | Tải file âm thanh phản hồi từ AI Tutor |
| 101 | Nhật ký bảo mật | `GET` | `/api/v1/admin/audit-logs` | `ADMIN` | Xem nhật ký kiểm toán hệ thống (Audit Logs) có phân trang |
| 102 | Nhật ký bảo mật | `GET` | `/api/v1/admin/activity-logs` | `ADMIN` | Xem nhật ký hoạt động hệ thống (Activity Logs) có phân trang |
| 103 | Cấu hình hệ thống | `GET` | `/api/v1/admin/settings` | `ADMIN` | Xem toàn bộ cấu hình tham số động hệ thống |
| 104 | Cấu hình hệ thống | `GET` | `/api/v1/admin/settings/{key}` | `ADMIN` | Xem giá trị một tham số cấu hình |
| 105 | Cấu hình hệ thống | `PUT` | `/api/v1/admin/settings/{key}` | `ADMIN` | Cập nhật giá trị một tham số cấu hình |
| 106 | Cấu hình hệ thống | `POST` | `/api/v1/admin/settings/bulk` | `ADMIN` | Cập nhật hàng loạt nhiều tham số cấu hình cùng lúc |
| 107 | Giám sát hệ thống | `GET` | `/actuator/health` | Public (Permit All) | Kiểm tra trạng thái hoạt động (Liveness/Readiness) của ứng dụng |

---

## 5. Từ Điển Dữ Liệu Chi Tiết (Data Models: Enums, DTOs & Entities)

Mục này cung cấp từ điển schema chi tiết cho toàn bộ 18 Enums, cấu trúc phản hồi `ApiResponse<T>`, 66 Data Transfer Objects (DTOs) và 3 JPA Entities được sử dụng trực tiếp trong các API endpoints.

---

### 5.1. Bảng Tổng Hợp Toàn Bộ 18 Enums Hệ Thống

| Tên Enum | Danh Sách Giá Trị Hợp Lệ | Mô tả ý nghĩa nghiệp vụ |
|---|---|---|
| `UserRole` | `STUDENT`, `INSTRUCTOR`, `TA`, `ADMIN` | Vai trò người dùng trong hệ thống |
| `UserStatus` | `ACTIVE`, `LOCKED` | Trạng thái hoạt động của tài khoản |
| `GenderType` | `MALE`, `FEMALE`, `OTHER` | Giới tính người dùng |
| `ClassStatus` | `DRAFT`, `ACTIVE`, `COMPLETED`, `ARCHIVED` | Vòng đời trạng thái lớp học phần |
| `EnrollmentStatus` | `ACTIVE`, `DROPPED`, `COMPLETED` | Trạng thái học tập của sinh viên trong lớp |
| `ClassStaffRole` | `INSTRUCTOR`, `TA` | Vai trò nhân sự được phân công trong lớp |
| `ExamType` | `PRACTICE`, `QUIZ`, `MIDTERM`, `FINAL` | Phân loại kỳ thi (Luyện tập, 15 phút, Giữa kỳ, Cuối kỳ) |
| `AttemptStatus` | `IN_PROGRESS`, `SUBMITTED`, `GRADED` | Trạng thái của lượt làm bài thi trắc nghiệm |
| `QuestionType` | `MCQ_SINGLE`, `MCQ_MULTI`, `TRUE_FALSE`, `SHORT_ANSWER` | Hình thức câu hỏi trắc nghiệm |
| `DifficultyLevel` | `EASY`, `MEDIUM`, `HARD` | Độ khó câu hỏi theo phân loại Bloom |
| `ApprovalStatus` | `DRAFT`, `PENDING`, `APPROVED`, `REJECTED` | Quy trình kiểm duyệt học liệu và câu hỏi |
| `MaterialType` | `PDF`, `VIDEO`, `SLIDE`, `TEXT`, `OTHER` | Định dạng tệp tài liệu học tập số |
| `ProgressStatus` | `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` | Mức độ hoàn thành học liệu của sinh viên |
| `SubmissionStatus` | `PENDING`, `GRADED`, `CONFIRMED` | Trạng thái nộp và chấm bài thí nghiệm ảo 3D |
| `EvidenceSourceType`| `EXPERIMENT`, `EXAM`, `AI_CONVERSATION` | Nguồn gốc xuất xứ của minh chứng học tập |
| `FileProcessingStatus` | `QUEUED`, `PROCESSING`, `COMPLETED`, `FAILED` | Trạng thái xử lý file ngầm (OCR PDF / AI Parsing) |
| `AiMode` | `TEXT`, `VOICE` | Chế độ giao tiếp với Trợ giảng AI Socratic |
| `AiSender` | `USER`, `AI` | Đối tượng gửi tin nhắn trong phiên hội thoại AI |

---

### 5.2. Cấu Trúc Bao Gói Phản Hồi Chuẩn (`ApiResponse<T>`)

Mọi phản hồi trả về từ REST API đều được bao bọc bởi `ApiResponse<T>`:

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả nghiệp vụ |
|---|---|---|---|
| `status` | `int` | ✓ | Mã trạng thái HTTP phản hồi (200, 201, 400, 401, 403, 404, 409, 422, 429, 500) |
| `message` | `String` | ✓ | Thông điệp mô tả kết quả xử lý (ví dụ: 'Success', 'Attempt started') |
| `data` | `T` | Tùy chọn | Đối tượng dữ liệu thực tế (null khi có lỗi hoặc endpoint void) |

---

### 5.3. Nhóm DTOs Xác Thực & Quản Trị Người Dùng (14 DTOs)

#### `SigninRequestDTO` — Dữ liệu yêu cầu đăng nhập hệ thống

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `username` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Username cannot be empty")` | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `password` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Password cannot be empty")` | Mật khẩu người dùng (được băm bằng BCrypt 12 rounds) |

#### `AuthResponseDTO` — Dữ liệu trả về sau khi đăng nhập hoặc refresh token

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `accessToken` | `String` | Tùy chọn | — | JWT Access Token dùng cho Authorization: Bearer header |
| `refreshToken` | `String` | Tùy chọn | — | Refresh Token chuỗi dài hạn dùng để xoay vòng token (Token Rotation) |
| `tokenType` | `String` | Tùy chọn | — | Loại token xác thực, cố định 'Bearer' |
| `expiresIn` | `Long` | Tùy chọn | — | Thời hạn hiệu lực của Access Token (tính bằng giây, 300s / 3600s) |
| `refreshExpiresIn` | `Long` | Tùy chọn | — | Thời hạn hiệu lực của Refresh Token (tính bằng giây, 604800s / 7 ngày) |

#### `RefreshRequestDTO` — Yêu cầu làm mới Access Token bằng Refresh Token

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `refreshToken` | `String` | ✓ Bắt buộc | `@NotBlank` | Refresh Token chuỗi dài hạn dùng để xoay vòng token (Token Rotation) |

#### `ForgotPasswordRequestDTO` — Yêu cầu gửi mã xác thực đặt lại mật khẩu qua email

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `email` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Email không được để trống")`<br>`@Email(message = "Định dạng email không hợp lệ")` | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |

#### `ResetPasswordRequestDTO` — Yêu cầu đặt lại mật khẩu mới với token xác thực

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `token` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Mã token xác thực không được để trống")` | Mã xác thực token đặt lại mật khẩu |
| `newPassword` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Mật khẩu mới không được để trống")`<br>`@Size(min = 6, message = "Mật khẩu mới phải có tối thiểu 6 ký tự")` | Mật khẩu mới thay thế |

#### `ChangePasswordDTO` — Yêu cầu đổi mật khẩu tài khoản đang đăng nhập

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `oldPassword` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Old password is required")` | Mật khẩu hiện tại để xác thực thay đổi |
| `newPassword` | `String` | ✓ Bắt buộc | `@NotBlank(message = "New password is required")`<br>`@Size(min = 8, message = "Minimum password length: 8 characters")` | Mật khẩu mới thay thế |

#### `AdminCreateUserDTO` — Quản trị viên tạo tài khoản người dùng mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `username` | `String` | ✓ Bắt buộc | `@NotBlank`<br>`@Size(min = 4, max = 255, message = "Minimum username length: 4 characters")` | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `email` | `String` | ✓ Bắt buộc | `@NotBlank`<br>`@Email` | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |
| `password` | `String` | ✓ Bắt buộc | `@NotBlank`<br>`@Size(min = 8, message = "Minimum password length: 8 characters")` | Mật khẩu người dùng (được băm bằng BCrypt 12 rounds) |
| `role` | `UserRole` | ✓ Bắt buộc | `@NotNull(message = "Role is required")` | Vai trò người dùng trong hệ thống (STUDENT, INSTRUCTOR, TA, ADMIN) |

#### `AdminUpdateUserDTO` — Quản trị viên cập nhật vai trò hoặc email người dùng

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `role` | `UserRole` | Tùy chọn | — | Vai trò người dùng trong hệ thống (STUDENT, INSTRUCTOR, TA, ADMIN) |
| `email` | `String` | Tùy chọn | `@Email` | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |

#### `UserDataDTO` — Dữ liệu đăng ký tài khoản sinh viên mới (Signup)

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `username` | `String` | ✓ Bắt buộc | `@NotBlank`<br>`@Size(min = 4, max = 255, message = "Minimum username length: 4 characters")` | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `email` | `String` | ✓ Bắt buộc | `@NotBlank`<br>`@Email` | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |
| `password` | `String` | ✓ Bắt buộc | `@NotBlank`<br>`@Size(min = 8, message = "Minimum password length: 8 characters")` | Mật khẩu người dùng (được băm bằng BCrypt 12 rounds) |

#### `UserUpdateDTO` — Người dùng tự cập nhật tên đăng nhập hoặc email

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `username` | `String` | Tùy chọn | `@Size(min = 4, max = 255, message = "Minimum username length: 4 characters")` | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `email` | `String` | Tùy chọn | `@Email` | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |

#### `UpdateUserStatusDTO` — Quản trị viên cập nhật trạng thái khóa/mở tài khoản

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `status` | `UserStatus` | ✓ Bắt buộc | `@NotNull(message = "Status is required")` | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |

#### `UserResponseDTO` — Thông tin tài khoản trả về trong các truy vấn người dùng

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `userId` | `UUID` | Tùy chọn | — | Định danh UUID duy nhất của tài khoản |
| `username` | `String` | Tùy chọn | — | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `email` | `String` | Tùy chọn | — | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |
| `role` | `UserRole` | Tùy chọn | — | Vai trò người dùng trong hệ thống (STUDENT, INSTRUCTOR, TA, ADMIN) |
| `status` | `UserStatus` | Tùy chọn | — | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |

#### `UserProfileDTO` — Thông tin hồ sơ cá nhân chi tiết (sinh viên / giảng viên)

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `fullName` | `String` | Tùy chọn | — | Họ và tên đầy đủ |
| `avatarUrl` | `String` | Tùy chọn | — | Đường dẫn URL ảnh đại diện |
| `dateOfBirth` | `LocalDate` | Tùy chọn | — | Ngày sinh định dạng ISO-8601 (yyyy-MM-dd) |
| `gender` | `GenderType` | Tùy chọn | — | Giới tính (MALE, FEMALE, OTHER) |
| `phone` | `String` | Tùy chọn | — | Số điện thoại liên hệ |
| `studentCode` | `String` | Tùy chọn | — | Mã số sinh viên (MSSV) |
| `bio` | `String` | Tùy chọn | — | Tiểu sử / mô tả bản thân ngắn gọn |

#### `UserProfileUpdateDTO` — Dữ liệu cập nhật hồ sơ cá nhân

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `fullName` | `String` | Tùy chọn | — | Họ và tên đầy đủ |
| `avatarUrl` | `String` | Tùy chọn | — | Đường dẫn URL ảnh đại diện |
| `dateOfBirth` | `LocalDate` | Tùy chọn | — | Ngày sinh định dạng ISO-8601 (yyyy-MM-dd) |
| `gender` | `GenderType` | Tùy chọn | — | Giới tính (MALE, FEMALE, OTHER) |
| `phone` | `String` | Tùy chọn | — | Số điện thoại liên hệ |
| `studentCode` | `String` | Tùy chọn | — | Mã số sinh viên (MSSV) |
| `bio` | `String` | Tùy chọn | — | Tiểu sử / mô tả bản thân ngắn gọn |

---

### 5.4. Nhóm DTOs Cấu Trúc Đào Tạo & Lớp Học (17 DTOs)

#### `SubjectDTO` — Thông tin chi tiết môn học

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `subjectCode` | `String` | Tùy chọn | — | Mã môn học (ví dụ: 'PHY101') |
| `subjectName` | `String` | Tùy chọn | — | Tên môn học (ví dụ: 'Vật lý đại cương 1') |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |
| `isActive` | `Boolean` | Tùy chọn | — | Trạng thái kích hoạt môn học (true: hoạt động, false: tạm khóa) |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |

#### `CreateSubjectDTO` — Dữ liệu tạo môn học mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectCode` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Mã môn học không được để trống")` | Mã môn học (ví dụ: 'PHY101') |
| `subjectName` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Tên môn học không được để trống")` | Tên môn học (ví dụ: 'Vật lý đại cương 1') |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |

#### `UpdateSubjectDTO` — Dữ liệu cập nhật thông tin môn học

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectName` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Tên môn học không được để trống")` | Tên môn học (ví dụ: 'Vật lý đại cương 1') |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |

#### `SemesterDTO` — Thông tin chi tiết học kỳ niên khóa

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `semesterId` | `UUID` | Tùy chọn | — | Định danh UUID của học kỳ |
| `semesterCode` | `String` | Tùy chọn | — | Mã học kỳ (ví dụ: '2026-HK1') |
| `semesterName` | `String` | Tùy chọn | — | Tên hiển thị học kỳ (ví dụ: 'Học kỳ 1 Năm học 2025-2026') |
| `academicYear` | `String` | Tùy chọn | — | Năm học niên khóa (ví dụ: '2025-2026') |
| `startDate` | `LocalDate` | Tùy chọn | — | Ngày bắt đầu học kỳ (yyyy-MM-dd) |
| `endDate` | `LocalDate` | Tùy chọn | — | Ngày kết thúc học kỳ (yyyy-MM-dd) |
| `isCurrent` | `Boolean` | Tùy chọn | — | — |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |

#### `CreateSemesterDTO` — Dữ liệu tạo học kỳ mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `semesterCode` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Mã học kỳ không được để trống")` | Mã học kỳ (ví dụ: '2026-HK1') |
| `semesterName` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Tên học kỳ không được để trống")` | Tên hiển thị học kỳ (ví dụ: 'Học kỳ 1 Năm học 2025-2026') |
| `academicYear` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Năm học không được để trống")` | Năm học niên khóa (ví dụ: '2025-2026') |
| `startDate` | `LocalDate` | Tùy chọn | — | Ngày bắt đầu học kỳ (yyyy-MM-dd) |
| `endDate` | `LocalDate` | Tùy chọn | — | Ngày kết thúc học kỳ (yyyy-MM-dd) |

#### `UpdateSemesterDTO` — Dữ liệu cập nhật thời gian học kỳ

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `semesterName` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Tên học kỳ không được để trống")` | Tên hiển thị học kỳ (ví dụ: 'Học kỳ 1 Năm học 2025-2026') |
| `academicYear` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Năm học không được để trống")` | Năm học niên khóa (ví dụ: '2025-2026') |
| `startDate` | `LocalDate` | Tùy chọn | — | Ngày bắt đầu học kỳ (yyyy-MM-dd) |
| `endDate` | `LocalDate` | Tùy chọn | — | Ngày kết thúc học kỳ (yyyy-MM-dd) |

#### `TopicDTO` — Thông tin chương mục kiến thức

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `topicName` | `String` | Tùy chọn | — | Tên chương mục kiến thức (ví dụ: 'Động học chất điểm') |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |

#### `CreateTopicDTO` — Dữ liệu tạo chương mục kiến thức mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `topicName` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Topic name is required")` | Tên chương mục kiến thức (ví dụ: 'Động học chất điểm') |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |

#### `ClassDTO` — Thông tin chi tiết lớp học phần

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `semesterId` | `UUID` | Tùy chọn | — | Định danh UUID của học kỳ |
| `classCode` | `String` | Tùy chọn | — | Mã lớp học phần (ví dụ: 'D22-VT01') |
| `instructorId` | `UUID` | Tùy chọn | — | Định danh UUID giảng viên phụ trách chính |
| `maxStudents` | `Integer` | Tùy chọn | — | Số lượng sinh viên tối đa được ghi danh |
| `status` | `ClassStatus` | Tùy chọn | — | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |

#### `CreateClassDTO` — Dữ liệu mở lớp học phần mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Môn học không được để trống")` | Định danh UUID của môn học (Vật lý 1) |
| `semesterId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Học kỳ không được để trống")` | Định danh UUID của học kỳ |
| `classCode` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Mã lớp không được để trống")` | Mã lớp học phần (ví dụ: 'D22-VT01') |
| `maxStudents` | `Integer` | Tùy chọn | — | Số lượng sinh viên tối đa được ghi danh |

#### `UpdateClassDTO` — Dữ liệu cập nhật sĩ số và mã lớp

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `classCode` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Mã lớp không được để trống")` | Mã lớp học phần (ví dụ: 'D22-VT01') |
| `maxStudents` | `Integer` | Tùy chọn | — | Số lượng sinh viên tối đa được ghi danh |

#### `UpdateClassStatusDTO` — Cập nhật trạng thái vòng đời lớp học

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `status` | `ClassStatus` | ✓ Bắt buộc | `@NotNull(message = "Trạng thái không được để trống")` | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |

#### `ClassStaffDTO` — Thông tin nhân sự giảng dạy trong lớp

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `userId` | `UUID` | Tùy chọn | — | Định danh UUID duy nhất của tài khoản |
| `username` | `String` | Tùy chọn | — | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `fullName` | `String` | Tùy chọn | — | Họ và tên đầy đủ |
| `email` | `String` | Tùy chọn | — | Địa chỉ email (duy nhất, nhận liên kết reset mật khẩu) |
| `roleInClass` | `ClassStaffRole` | Tùy chọn | — | Vai trò trong lớp học (INSTRUCTOR hoặc TA) |

#### `AssignStaffDTO` — Phân công giảng viên hoặc trợ giảng vào lớp

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `userId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Người dùng không được để trống")` | Định danh UUID duy nhất của tài khoản |
| `roleInClass` | `ClassStaffRole` | ✓ Bắt buộc | `@NotNull(message = "Vai trò trong lớp không được để trống")` | Vai trò trong lớp học (INSTRUCTOR hoặc TA) |

#### `EnrollmentDTO` — Thông tin sinh viên đã ghi danh vào lớp

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `enrollmentId` | `UUID` | Tùy chọn | — | Định danh UUID bản ghi ghi danh của sinh viên vào lớp |
| `studentId` | `UUID` | Tùy chọn | — | Định danh UUID của sinh viên |
| `username` | `String` | Tùy chọn | — | Tên đăng nhập người dùng (duy nhất trong toàn hệ thống) |
| `fullName` | `String` | Tùy chọn | — | Họ và tên đầy đủ |
| `studentCode` | `String` | Tùy chọn | — | Mã số sinh viên (MSSV) |
| `enrolledAt` | `Instant` | Tùy chọn | — | Thời điểm sinh viên ghi danh vào lớp |
| `status` | `EnrollmentStatus` | Tùy chọn | — | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |

#### `SingleEnrollmentDTO` — Ghi danh 1 sinh viên vào lớp học

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `studentId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Sinh viên không được để trống")` | Định danh UUID của sinh viên |

#### `BulkEnrollmentDTO` — Ghi danh hàng loạt sinh viên vào lớp học

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `studentIds` | `List<UUID>` | ✓ Bắt buộc | `@NotEmpty(message = "Danh sách sinh viên không được để trống")` | Danh sách UUID sinh viên để ghi danh hàng loạt |

#### `UpdateEnrollmentStatusDTO` — Cập nhật trạng thái ghi danh của sinh viên

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `status` | `EnrollmentStatus` | ✓ Bắt buộc | `@NotNull(message = "Trạng thái không được để trống")` | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |

---

### 5.5. Nhóm DTOs Ngân Hàng Câu Hỏi & Đề Thi (9 DTOs)

#### `CreateQuestionDTO` — Dữ liệu tạo hoặc cập nhật câu hỏi trắc nghiệm

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Subject ID is required")` | Định danh UUID của môn học (Vật lý 1) |
| `topicId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Topic ID is required")` | Định danh UUID chương mục kiến thức |
| `questionType` | `QuestionType` | ✓ Bắt buộc | `@NotNull(message = "Question type is required")` | Loại câu hỏi (MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE, SHORT_ANSWER) |
| `content` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Content is required")` | Nội dung văn bản của câu hỏi hoặc tin nhắn trao đổi |
| `mediaUrl` | `String` | Tùy chọn | — | Đường dẫn hình ảnh / đồ thị minh họa bài toán |
| `difficultyLevel` | `DifficultyLevel` | ✓ Bắt buộc | `@NotNull(message = "Difficulty level is required")` | Độ khó câu hỏi (EASY, MEDIUM, HARD) |
| `cognitiveLevel` | `String` | Tùy chọn | — | — |
| `options` | `List<CreateQuestionOptionDTO>` | Tùy chọn | — | Danh sách các phương án lựa chọn trắc nghiệm |
| `content` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Option content is required")` | Nội dung văn bản của câu hỏi hoặc tin nhắn trao đổi |
| `isCorrect` | `Boolean` | ✓ Bắt buộc | `@NotNull(message = "isCorrect is required")` | Đánh dấu phương án đúng (true / false) |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |
| `explanation` | `String` | Tùy chọn | — | — |

#### `QuestionBankDTO` — Chi tiết câu hỏi trắc nghiệm trong ngân hàng

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `questionId` | `UUID` | Tùy chọn | — | Định danh UUID của câu hỏi trắc nghiệm |
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `questionType` | `QuestionType` | Tùy chọn | — | Loại câu hỏi (MCQ_SINGLE, MCQ_MULTI, TRUE_FALSE, SHORT_ANSWER) |
| `content` | `String` | Tùy chọn | — | Nội dung văn bản của câu hỏi hoặc tin nhắn trao đổi |
| `mediaUrl` | `String` | Tùy chọn | — | Đường dẫn hình ảnh / đồ thị minh họa bài toán |
| `difficultyLevel` | `DifficultyLevel` | Tùy chọn | — | Độ khó câu hỏi (EASY, MEDIUM, HARD) |
| `cognitiveLevel` | `String` | Tùy chọn | — | — |
| `approvalStatus` | `ApprovalStatus` | Tùy chọn | — | Trạng thái phê duyệt học liệu bởi Bộ môn (DRAFT, PENDING, APPROVED, REJECTED) |
| `createdBy` | `UUID` | Tùy chọn | — | — |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |
| `options` | `List<QuestionOptionDTO>` | Tùy chọn | — | Danh sách các phương án lựa chọn trắc nghiệm |

#### `QuestionOptionDTO` — Phương án lựa chọn của câu hỏi trắc nghiệm

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `optionId` | `UUID` | Tùy chọn | — | Định danh UUID của phương án trắc nghiệm |
| `questionId` | `UUID` | Tùy chọn | — | Định danh UUID của câu hỏi trắc nghiệm |
| `content` | `String` | Tùy chọn | — | Nội dung văn bản của câu hỏi hoặc tin nhắn trao đổi |
| `isCorrect` | `Boolean` | Tùy chọn | — | Đánh dấu phương án đúng (true / false) |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |
| `explanation` | `String` | Tùy chọn | — | — |

#### `QuestionImportResultDTO` — Kết quả trích xuất và nhập câu hỏi tự động từ tệp PDF

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `totalParsed` | `int` | Tùy chọn | — | Tổng số câu hỏi AI trích xuất được từ tệp PDF |
| `totalImported` | `int` | Tùy chọn | — | Số câu hỏi đã nhập thành công vào ngân hàng |
| `questions` | `List<QuestionBankDTO>` | Tùy chọn | — | Danh sách chi tiết các câu hỏi trắc nghiệm |
| `warnings` | `List<String>` | Tùy chọn | — | Danh sách các cảnh báo / lỗi định dạng khi trích xuất PDF |

#### `CreateExamDTO` — Dữ liệu thiết lập kỳ thi mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `classId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Class ID is required")` | Định danh UUID của lớp học phần |
| `matrixId` | `UUID` | Tùy chọn | — | Định danh UUID ma trận cấu trúc đề thi |
| `title` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Title is required")` | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `examType` | `ExamType` | ✓ Bắt buộc | `@NotNull(message = "Exam type is required")` | Loại kỳ thi (PRACTICE, QUIZ, MIDTERM, FINAL) |
| `durationMinutes` | `Integer` | ✓ Bắt buộc | `@NotNull(message = "Duration in minutes is required")` | Thời lượng làm bài thi tính theo phút |
| `startTime` | `Instant` | Tùy chọn | — | Thời điểm mở đề thi (ISO-8601) |
| `endTime` | `Instant` | Tùy chọn | — | Thời điểm kết thúc / đóng đề thi (ISO-8601) |

#### `ExamDTO` — Thông tin cấu hình chi tiết kỳ thi

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `examId` | `UUID` | Tùy chọn | — | Định danh UUID của kỳ thi |
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `matrixId` | `UUID` | Tùy chọn | — | Định danh UUID ma trận cấu trúc đề thi |
| `title` | `String` | Tùy chọn | — | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `examType` | `ExamType` | Tùy chọn | — | Loại kỳ thi (PRACTICE, QUIZ, MIDTERM, FINAL) |
| `durationMinutes` | `Integer` | Tùy chọn | — | Thời lượng làm bài thi tính theo phút |
| `startTime` | `Instant` | Tùy chọn | — | Thời điểm mở đề thi (ISO-8601) |
| `endTime` | `Instant` | Tùy chọn | — | Thời điểm kết thúc / đóng đề thi (ISO-8601) |
| `createdBy` | `UUID` | Tùy chọn | — | — |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |
| `totalQuestions` | `Integer` | Tùy chọn | — | Tổng số câu hỏi trong đề thi |

#### `AddExamQuestionDTO` — Dữ liệu thêm câu hỏi thủ công vào đề thi

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `questionId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Question ID is required")` | Định danh UUID của câu hỏi trắc nghiệm |
| `scoreWeight` | `BigDecimal` | Tùy chọn | — | Trọng số điểm của câu hỏi trong đề thi |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |

#### `ExamAttemptDTO` — Thông tin chi tiết lượt làm bài thi của sinh viên

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `attemptId` | `UUID` | Tùy chọn | — | Định danh UUID của lượt làm bài thi |
| `examId` | `UUID` | Tùy chọn | — | Định danh UUID của kỳ thi |
| `studentId` | `UUID` | Tùy chọn | — | Định danh UUID của sinh viên |
| `attemptNumber` | `Integer` | Tùy chọn | — | Số thứ tự lượt thi của sinh viên (1, 2, 3...) |
| `startedAt` | `Instant` | Tùy chọn | — | Thời điểm bắt đầu làm bài (ISO-8601) |
| `submittedAt` | `Instant` | Tùy chọn | — | Thời điểm nộp bài và kết thúc lượt thi (ISO-8601) |
| `status` | `AttemptStatus` | Tùy chọn | — | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |
| `totalScore` | `BigDecimal` | Tùy chọn | — | Điểm số đạt được (thang điểm 10.0) |

#### `SubmitAnswerDTO` — Lưu phương án trả lời tạm thời của sinh viên

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `questionId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Question ID is required")` | Định danh UUID của câu hỏi trắc nghiệm |
| `selectedOptionIds` | `List<UUID>` | Tùy chọn | — | Danh sách UUID phương án trắc nghiệm sinh viên lựa chọn |
| `answerText` | `String` | Tùy chọn | — | Nội dung trả lời tự luận ngắn của sinh viên |

---

### 5.6. Nhóm DTOs Thí Nghiệm Ảo 3D & Minh Chứng (8 DTOs)

#### `GradeSubmissionDTO` — Dữ liệu chấm điểm bài nộp thí nghiệm ảo theo Rubric

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `rubricId` | `UUID` | Tùy chọn | — | Định danh UUID tiêu chí Rubric áp dụng chấm điểm |
| `score` | `BigDecimal` | Tùy chọn | — | Điểm số chấm cho bài nộp (thang điểm 10.0) |
| `feedback` | `String` | Tùy chọn | — | Nhận xét chi tiết của Giảng viên / Trợ giảng |
| `comment` | `String` | Tùy chọn | — | Ghi chú bổ sung (hỗ trợ alias tương thích ngược) |

#### `ConfirmSubmissionDTO` — Dữ liệu xác nhận và phê duyệt kết quả thí nghiệm ảo

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `note` | `String` | Tùy chọn | — | Ghi chú phê duyệt và chốt điểm chính thức bài thí nghiệm |



#### `CreateExperimentDTO` — Dữ liệu cấu hình bài thí nghiệm ảo 3D mới

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `subjectId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Subject ID is required")` | Định danh UUID của môn học (Vật lý 1) |
| `title` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Title is required")` | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |
| `sceneAssetUrl` | `String` | Tùy chọn | — | Đường dẫn bundle mô hình 3D (WebGL / Three.js) |
| `sceneAssetsJson` | `JsonNode` | Tùy chọn | — | Cấu hình tham số vật lý & thiết bị mô phỏng dạng JSON |
| `instructions` | `String` | Tùy chọn | — | — |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |

#### `ExperimentDTO` — Chi tiết bài thí nghiệm ảo và thông số mô phỏng 3D

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `experimentId` | `UUID` | Tùy chọn | — | Định danh UUID của bài thí nghiệm ảo 3D |
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `title` | `String` | Tùy chọn | — | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `description` | `String` | Tùy chọn | — | Mô tả chi tiết nội dung môn học, học kỳ, chương mục hoặc tham số |
| `sceneAssetUrl` | `String` | Tùy chọn | — | Đường dẫn bundle mô hình 3D (WebGL / Three.js) |
| `sceneAssetsJson` | `JsonNode` | Tùy chọn | — | Cấu hình tham số vật lý & thiết bị mô phỏng dạng JSON |
| `instructions` | `String` | Tùy chọn | — | — |
| `orderIndex` | `Integer` | Tùy chọn | — | Thứ tự sắp xếp hiển thị |

#### `CreateExperimentAssignmentDTO` — Dữ liệu giao bài thí nghiệm ảo cho lớp học

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `classId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Class ID is required")` | Định danh UUID của lớp học phần |
| `dueDate` | `Instant` | Tùy chọn | — | Hạn nộp kết quả thí nghiệm (ISO-8601) |
| `instructionsOverride` | `String` | Tùy chọn | — | Hướng dẫn bổ sung hoặc yêu cầu tùy biến của giảng viên |

#### `ExperimentAssignmentDTO` — Thông tin đợt giao bài thí nghiệm ảo

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `assignmentId` | `UUID` | Tùy chọn | — | Định danh UUID đợt giao bài thí nghiệm cho lớp |
| `experimentId` | `UUID` | Tùy chọn | — | Định danh UUID của bài thí nghiệm ảo 3D |
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `assignedBy` | `UUID` | Tùy chọn | — | UUID giảng viên thực hiện giao bài |
| `dueDate` | `Instant` | Tùy chọn | — | Hạn nộp kết quả thí nghiệm (ISO-8601) |
| `instructionsOverride` | `String` | Tùy chọn | — | Hướng dẫn bổ sung hoặc yêu cầu tùy biến của giảng viên |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |

#### `SubmitExperimentDTO` — Sinh viên nộp kết quả đo đạc và báo cáo thí nghiệm ảo

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `evidenceUrl` | `String` | Tùy chọn | — | URL minh chứng kết quả đo hoặc biểu đồ thực nghiệm |
| `file` | `MultipartFile` | Tùy chọn | — | Tệp đính kèm nộp bài (PDF báo cáo, ảnh minh chứng đo đạc) |
| `rawDataJson` | `JsonNode` | Tùy chọn | — | Dữ liệu đo đạc thực nghiệm dạng JSON (bảng giá trị đo, sai số...) |

#### `EvidenceDTO` — Thông tin minh chứng kết quả đo đạc thí nghiệm ảo

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `evidenceId` | `UUID` | Tùy chọn | — | Định danh UUID bản ghi minh chứng thí nghiệm |
| `studentId` | `UUID` | Tùy chọn | — | Định danh UUID của sinh viên |
| `sourceType` | `EvidenceSourceType` | Tùy chọn | — | Nguồn phát sinh minh chứng (EXPERIMENT, EXAM, AI_CONVERSATION) |
| `sourceId` | `UUID` | Tùy chọn | — | UUID của đối tượng phát sinh minh chứng |
| `fileId` | `UUID` | Tùy chọn | — | UUID tệp lưu trữ trong hệ thống FileUpload |
| `fileUrl` | `String` | Tùy chọn | — | — |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |

---

### 5.7. Nhóm DTOs Học Liệu Số & Tiến Độ Học Tập (4 DTOs)

#### `CreateLearningMaterialDTO` — Dữ liệu tải lên học liệu số mới (PDF, Video, Slide)

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `title` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Title is required")` | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `type` | `MaterialType` | ✓ Bắt buộc | `@NotNull(message = "Material type is required")` | Loại định dạng học liệu (PDF, VIDEO, SLIDE, TEXT, OTHER) |
| `contentText` | `String` | Tùy chọn | — | Nội dung bài học dạng văn bản / markdown |
| `sourceCitation` | `String` | Tùy chọn | — | Trích dẫn nguồn học liệu từ giáo trình chuẩn của Bộ môn |
| `file` | `MultipartFile` | Tùy chọn | — | Tệp đính kèm nộp bài (PDF báo cáo, ảnh minh chứng đo đạc) |

#### `LearningMaterialDTO` — Thông tin chi tiết học liệu số đã được phê duyệt

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `materialId` | `UUID` | Tùy chọn | — | Định danh UUID học liệu số |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `fileId` | `UUID` | Tùy chọn | — | UUID tệp lưu trữ trong hệ thống FileUpload |
| `title` | `String` | Tùy chọn | — | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `type` | `MaterialType` | Tùy chọn | — | Loại định dạng học liệu (PDF, VIDEO, SLIDE, TEXT, OTHER) |
| `fileUrl` | `String` | Tùy chọn | — | — |
| `contentText` | `String` | Tùy chọn | — | Nội dung bài học dạng văn bản / markdown |
| `version` | `Integer` | Tùy chọn | — | Số phiên bản tài liệu (1, 2...) |
| `approvalStatus` | `ApprovalStatus` | Tùy chọn | — | Trạng thái phê duyệt học liệu bởi Bộ môn (DRAFT, PENDING, APPROVED, REJECTED) |
| `sourceCitation` | `String` | Tùy chọn | — | Trích dẫn nguồn học liệu từ giáo trình chuẩn của Bộ môn |
| `createdBy` | `UUID` | Tùy chọn | — | — |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |
| `updatedAt` | `Instant` | Tùy chọn | — | Thời điểm cập nhật bản ghi gần nhất (ISO-8601 UTC) |

#### `UpdateLearningProgressDTO` — Cập nhật tiến độ hoàn thành bài học của sinh viên

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `topicId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Topic ID is required")` | Định danh UUID chương mục kiến thức |
| `classId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Class ID is required")` | Định danh UUID của lớp học phần |
| `progressPercent` | `BigDecimal` | ✓ Bắt buộc | `@NotNull(message = "Progress percent is required")`<br>`@Min(value = 0, message = "Progress must be at least 0")`<br>`@Max(value = 100, message = "Progress cannot exceed 100")` | Tỷ lệ phần trăm hoàn thành học phần (0.00% - 100.00%) |

#### `LearningProgressDTO` — Báo cáo tiến độ học tập chi tiết của sinh viên

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `progressId` | `UUID` | Tùy chọn | — | Định danh UUID bản ghi tiến độ học tập |
| `studentId` | `UUID` | Tùy chọn | — | Định danh UUID của sinh viên |
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `status` | `ProgressStatus` | Tùy chọn | — | Trạng thái hoạt động của tài khoản (ACTIVE, LOCKED) |
| `progressPercent` | `BigDecimal` | Tùy chọn | — | Tỷ lệ phần trăm hoàn thành học phần (0.00% - 100.00%) |
| `lastAccessedAt` | `Instant` | Tùy chọn | — | Thời điểm truy cập học liệu lần cuối (ISO-8601) |

---

### 5.8. Nhóm DTOs Trợ Giảng AI Socratic (5 DTOs)

#### `StartAiConversationDTO` — Yêu cầu mở phiên hội thoại mới với Trợ giảng AI

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `classId` | `UUID` | ✓ Bắt buộc | `@NotNull(message = "Class ID is required")` | Định danh UUID của lớp học phần |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `mode` | `AiMode` | Tùy chọn | — | Hình thức tương tác AI (TEXT: gõ chữ, VOICE: âm thanh giọng nói) |

#### `SendAiMessageDTO` — Nội dung câu hỏi của sinh viên gửi tới Trợ giảng AI

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `content` | `String` | ✓ Bắt buộc | `@NotBlank(message = "Message content is required")` | Nội dung văn bản của câu hỏi hoặc tin nhắn trao đổi |

#### `AiConversationDTO` — Thông tin phiên hội thoại Socratic AI

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `conversationId` | `UUID` | Tùy chọn | — | Định danh UUID phiên hội thoại Trợ giảng AI |
| `studentId` | `UUID` | Tùy chọn | — | Định danh UUID của sinh viên |
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `mode` | `AiMode` | Tùy chọn | — | Hình thức tương tác AI (TEXT: gõ chữ, VOICE: âm thanh giọng nói) |
| `startedAt` | `Instant` | Tùy chọn | — | Thời điểm bắt đầu làm bài (ISO-8601) |
| `endedAt` | `Instant` | Tùy chọn | — | Thời điểm kết thúc phiên hội thoại (ISO-8601) |
| `messageCount` | `Integer` | Tùy chọn | — | Tổng số lượng tin nhắn trao đổi trong phiên |

#### `AiMessageDTO` — Chi tiết tin nhắn trao đổi trong phiên hội thoại AI

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `messageId` | `UUID` | Tùy chọn | — | Định danh UUID của tin nhắn |
| `conversationId` | `UUID` | Tùy chọn | — | Định danh UUID phiên hội thoại Trợ giảng AI |
| `sender` | `AiSender` | Tùy chọn | — | Người gửi tin nhắn (USER: sinh viên, AI: trợ giảng Socratic) |
| `contentText` | `String` | Tùy chọn | — | Nội dung bài học dạng văn bản / markdown |
| `audioUrl` | `String` | Tùy chọn | — | Đường dẫn file phát âm giọng đọc câu trả lời của AI |
| `createdAt` | `Instant` | Tùy chọn | — | Thời điểm tạo bản ghi (ISO-8601 UTC) |

#### `AiFeedbackDTO` — Đánh giá chất lượng câu trả lời của AI (1 - 5 sao)

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `rating` | `Integer` | ✓ Bắt buộc | `@NotNull(message = "Rating is required")`<br>`@Min(value = 1, message = "Rating must be at least 1")`<br>`@Max(value = 5, message = "Rating must be at most 5")` | Số sao đánh giá câu trả lời của AI (1 đến 5 sao) |
| `comment` | `String` | Tùy chọn | — | Nhận xét góp ý của sinh viên về câu trả lời của AI |

---

### 5.9. Nhóm DTOs Thống Kê CTT & Dashboard (7 DTOs)

#### `DashboardDataDTO` — Chỉ số thống kê học tập tổng hợp

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `avgScore` | `Double` | Tùy chọn | — | Điểm số trung bình tích lũy của sinh viên hoặc lớp học |
| `completedTopics` | `Integer` | Tùy chọn | — | Số lượng chương mục kiến thức sinh viên đã hoàn thành |
| `totalTopics` | `Integer` | Tùy chọn | — | Tổng số chương mục kiến thức trong học phần |
| `labsConfirmed` | `Integer` | Tùy chọn | — | Số bài thí nghiệm ảo 3D đã được giảng viên chấm xác nhận |
| `aiSessionsCount` | `Integer` | Tùy chọn | — | Tổng số phiên hỏi đáp với trợ giảng AI |
| `totalExamsTaken` | `Integer` | Tùy chọn | — | — |
| `lastUpdated` | `Instant` | Tùy chọn | — | — |

#### `DashboardSnapshotDTO` — Bản ghi snapshot dữ liệu bảng điều khiển theo kỳ

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `snapshotId` | `UUID` | Tùy chọn | — | Định danh UUID bản ghi snapshot thống kê |
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `studentId` | `UUID` | Tùy chọn | — | Định danh UUID của sinh viên |
| `period` | `String` | Tùy chọn | — | Kỳ thống kê phân tích (ví dụ: '2026-HK1') |
| `data` | `DashboardDataDTO` | Tùy chọn | — | Đối tượng dữ liệu thống kê tổng hợp DashboardDataDTO |
| `generatedAt` | `Instant` | Tùy chọn | — | Thời điểm hệ thống chạy thuật toán tính toán thống kê (ISO-8601) |

#### `TopicDifficultyDTO` — Chỉ số độ khó Classical Test Theory (p-value) theo chương mục

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `statId` | `UUID` | Tùy chọn | — | Định danh UUID bản ghi thống kê độ khó chương mục |
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `topicName` | `String` | Tùy chọn | — | Tên chương mục kiến thức (ví dụ: 'Động học chất điểm') |
| `classId` | `UUID` | Tùy chọn | — | Định danh UUID của lớp học phần |
| `avgScore` | `BigDecimal` | Tùy chọn | — | Điểm số trung bình tích lũy của sinh viên hoặc lớp học |
| `errorRate` | `BigDecimal` | Tùy chọn | — | — |
| `commonWrongOptionsJson` | `JsonNode` | Tùy chọn | — | — |
| `period` | `String` | Tùy chọn | — | Kỳ thống kê phân tích (ví dụ: '2026-HK1') |
| `generatedAt` | `Instant` | Tùy chọn | — | Thời điểm hệ thống chạy thuật toán tính toán thống kê (ISO-8601) |

#### `QuestionQualityDTO` — Chỉ số phân biệt (Discrimination Index) và chất lượng câu hỏi

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `questionId` | `UUID` | Tùy chọn | — | Định danh UUID của câu hỏi trắc nghiệm |
| `questionText` | `String` | Tùy chọn | — | Nội dung câu hỏi trắc nghiệm phục vụ phân tích |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `topicName` | `String` | Tùy chọn | — | Tên chương mục kiến thức (ví dụ: 'Động học chất điểm') |
| `timesUsed` | `Integer` | Tùy chọn | — | Số lần câu hỏi được sử dụng trong các đề thi |
| `correctRate` | `BigDecimal` | Tùy chọn | — | — |
| `discriminationIndex` | `BigDecimal` | Tùy chọn | — | Độ phân biệt câu hỏi theo CTT (DI = P_cao - P_thap, từ -1.0 đến +1.0) |
| `avgTimeSeconds` | `BigDecimal` | Tùy chọn | — | — |
| `updatedAt` | `Instant` | Tùy chọn | — | Thời điểm cập nhật bản ghi gần nhất (ISO-8601 UTC) |

#### `AiTopicGapDTO` — Thống kê lỗ hổng kiến thức sinh viên hay gặp từ phản hồi AI

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `gapId` | `UUID` | Tùy chọn | — | Định danh UUID bản ghi lỗ hổng kiến thức do AI ghi nhận |
| `subjectId` | `UUID` | Tùy chọn | — | Định danh UUID của môn học (Vật lý 1) |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `topicName` | `String` | Tùy chọn | — | Tên chương mục kiến thức (ví dụ: 'Động học chất điểm') |
| `refusalCount` | `Integer` | Tùy chọn | — | Số lần AI từ chối giải bài trực tiếp và chuyển sang gợi mở Socratic |
| `frequentQuerySample` | `String` | Tùy chọn | — | Mẫu câu hỏi sinh viên thường xuyên thắc mắc chưa hiểu bản chất |
| `period` | `String` | Tùy chọn | — | Kỳ thống kê phân tích (ví dụ: '2026-HK1') |
| `generatedAt` | `Instant` | Tùy chọn | — | Thời điểm hệ thống chạy thuật toán tính toán thống kê (ISO-8601) |

#### `MaterialEffectivenessDTO` — Đánh giá mức độ hiệu quả của học liệu đối với kết quả thi

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `materialId` | `UUID` | Tùy chọn | — | Định danh UUID học liệu số |
| `title` | `String` | Tùy chọn | — | Tiêu đề kỳ thi, học liệu hoặc bài thí nghiệm |
| `topicId` | `UUID` | Tùy chọn | — | Định danh UUID chương mục kiến thức |
| `topicName` | `String` | Tùy chọn | — | Tên chương mục kiến thức (ví dụ: 'Động học chất điểm') |
| `period` | `String` | Tùy chọn | — | Kỳ thống kê phân tích (ví dụ: '2026-HK1') |
| `viewCount` | `Integer` | Tùy chọn | — | — |
| `avgTimeSpentSeconds` | `BigDecimal` | Tùy chọn | — | — |
| `correlatedScoreImprovement` | `BigDecimal` | Tùy chọn | — | — |

#### `TriggerAggregationRequestDTO` — Yêu cầu kích hoạt chạy phân tích CTT thủ công

| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc / Validation | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `period` | `String` | Tùy chọn | — | Kỳ thống kê phân tích (ví dụ: '2026-HK1') |

---

### 5.10. Nhóm Entities & DTOs Cấu Hình & Nhật Ký Kiểm Toán (5 Models)

#### `UpdateSettingRequest` — Cập nhật một cấu hình
| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `settingValue` | `String` | ✓ Bắt buộc | `@NotBlank` | Giá trị cấu hình mới |
| `description` | `String` | Tùy chọn | — | Mô tả mục đích tham số cấu hình |

#### `BulkUpdateSettingsRequest` — Cập nhật nhiều cấu hình
| Trường (Field) | Kiểu dữ liệu | Bắt buộc | Ràng buộc | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `settings` | `Map<String, String>` | ✓ Bắt buộc | `@NotNull` | Bản đồ cặp Key - Value cấu hình cần lưu |

#### `SystemSetting` (JPA Entity dùng trực tiếp)
| Trường (Field) | Kiểu dữ liệu | Khóa chính | Ràng buộc DB | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `settingKey` | `String` | PK | `length = 100, nullable = false` | Tên tham số cấu hình duy nhất |
| `settingValue` | `String` | — | `TEXT, nullable = false` | Giá trị tham số hệ thống |
| `description` | `String` | — | `TEXT` | Mô tả chi tiết chức năng của tham số |
| `updatedBy` | `UUID` | — | — | UUID người cập nhật cấu hình gần nhất |
| `updatedAt` | `Instant` | — | — | Thời điểm cập nhật cấu hình gần nhất |

#### `ActivityLog` (JPA Entity dùng trực tiếp)
| Trường (Field) | Kiểu dữ liệu | Khóa chính | Ràng buộc DB | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `logId` | `UUID` | PK | Auto UUID | Định danh duy nhất của bản ghi nhật ký |
| `userId` | `UUID` | — | — | Định danh người dùng thực hiện tương tác |
| `classId` | `UUID` | — | — | Lớp học phần liên quan |
| `actionType` | `String` | — | — | Loại hành vi (LOGIN, VIEW_MATERIAL, SUBMIT_LAB...) |
| `objectType` | `String` | — | — | Loại đối tượng (MATERIAL, EXAM, LAB...) |
| `objectId` | `UUID` | — | — | Định danh đối tượng tương tác |
| `metadataJson`| `JsonNode` | — | `JSONB` | Chi tiết bổ sung của hành động |
| `createdAt` | `Instant` | — | — | Thời điểm ghi nhận hành động |

#### `AuditLog` (JPA Entity dùng trực tiếp)
| Trường (Field) | Kiểu dữ liệu | Khóa chính | Ràng buộc DB | Mô tả nghiệp vụ |
|---|---|---|---|---|
| `auditId` | `UUID` | PK | Auto UUID | Định danh duy nhất bản ghi kiểm toán |
| `userId` | `UUID` | — | — | Định danh quản trị viên thực hiện |
| `action` | `String` | — | — | Thao tác (CREATE, UPDATE, DELETE, LOCK...) |
| `entity` | `String` | — | — | Tên bảng / thực thể bị thay đổi |
| `entityId` | `UUID` | — | — | Định danh thực thể bị thay đổi |
| `oldValue` | `JsonNode` | — | `JSONB` | Dữ liệu trạng thái cũ trước khi thay đổi |
| `newValue` | `JsonNode` | — | `JSONB` | Dữ liệu trạng thái mới sau khi thay đổi |
| `ipAddress` | `String` | — | — | Địa chỉ IP của client thực hiện thao tác |
| `createdAt` | `Instant` | — | — | Thời điểm ghi nhận hành động kiểm toán |

---
## 6. Xử Lý Lỗi Tập Trung & Bảng Mã Phản Hồi HTTP

### 6.1. Cơ chế bắt lỗi phân tầng (Multi-Layer Exception Handling)

Hệ thống xử lý lỗi qua 2 tầng phòng vệ:

1. **Tầng Bộ Lọc Bảo Mật (`JwtTokenFilter` & `RateLimitFilter`):**
   - Xử lý trước khi yêu cầu tới Controller.
   - Bắt các lỗi về định danh JWT (token hết hạn, chữ ký không hợp lệ, thiếu token) và trả về ngay mã **`401 Unauthorized`**.
   - Bắt các lỗi spam tần suất và trả về mã **`429 Too Many Requests`**.

2. **Tầng Bộ Điều Khiển Nghiệp Vụ (`GlobalExceptionHandlerController` — `@RestControllerAdvice`):**
   - Bắt và chuẩn hóa toàn bộ ngoại lệ phát sinh trong quá trình xử lý Controller/Service thành định dạng JSON đồng nhất:

| Exception Class | HTTP Status | Mô tả tình huống nghiệp vụ |
|---|---|---|
| `CustomException(401)` / `JwtException` | **401 Unauthorized** | Access token hết hạn hoặc không hợp lệ (xử lý tại `JwtTokenFilter`) |
| `AccessDeniedException` | **403 Forbidden** | Người dùng đã đăng nhập nhưng không đủ quyền hạn vai trò (`@PreAuthorize`) |
| `NoResourceFoundException` / ResourceNotFound | **404 Not Found** | Đường dẫn hoặc tài nguyên định danh UUID không tồn tại trong hệ thống |
| `CustomException(409)` | **409 Conflict** | Xung đột nghiệp vụ (Trùng phiên làm bài thi, nộp bài nhiều lần) |
| `MethodArgumentNotValidException` | **400 Bad Request** | Vi phạm ràng buộc validation Bean Validation `@Valid` (thiếu trường, sai định dạng) |
| `DataIntegrityViolationException` | **400 Bad Request** | Vi phạm ràng buộc toàn vẹn cơ sở dữ liệu (Unique constraint key, Foreign key) |
| `CustomException(422)` | **422 Unprocessable Entity** | Sai thông tin xác thực đăng nhập hoặc email/username đã tồn tại |
| `Exception` (catch-all) | **500 Internal Server Error** | Lỗi nội bộ không xác định của máy chủ |

---

### 6.2. Ví dụ lỗi Validation nghiệp vụ (HTTP 400)

Khi client gửi thiếu trường bắt buộc hoặc vi phạm độ dài chuỗi:
```json
{
  "status": 400,
  "message": "username: Minimum username length: 4 characters",
  "data": null
}
```

---

### 6.3. Bảng phân loại các mã lỗi nghiệp vụ đặc thù

| HTTP Status | Nguyên nhân kỹ thuật | Thông báo phản hồi mẫu (`message`) |
|:---:|---|---|
| **401** | Access token hết hạn khi gọi API thông thường | `Expired or invalid JWT token` |
| **401** | Refresh token hết hạn (quá 7 ngày) hoặc bị tái sử dụng | `Refresh token expired or invalid` / `Token reuse detected` |
| **403** | Sai vai trò (VD: Sinh viên gọi API tạo đề thi của Giảng viên) | `Access denied` |
| **403** | Vi phạm phòng chống IDOR (xem minh chứng sinh viên khác) | `Access denied: cannot access another student's evidence` |
| **409** | Sinh viên bắt đầu lượt thi mới khi đang có lượt `IN_PROGRESS` | `Student already has an active attempt for this exam` |
| **409** | Sinh viên cố làm lại đề thi chính thức (`QUIZ`, `MIDTERM`, `FINAL`) | `Only practice exams allow multiple attempts` |
| **422** | Đăng nhập sai tên đăng nhập hoặc mật khẩu | `Invalid username or password` |
| **429** | Vượt ngưỡng Rate Limit cửa sổ trượt 60 giây | `Rate limit exceeded. Please try again later.` |

---

### 6.4. Xử lý lỗi 401 (Access Token hết hạn) & Hướng dẫn tích hợp Frontend (Auto-Refresh Flow)

#### 1. Kịch bản phát sinh thực tế:
- Thời hạn hiệu lực của Access Token là **5 phút** (`security.jwt.token.expire-length: 300000ms`).
- Trong quá trình sinh viên đang làm bài thi trắc nghiệm dài 60 phút, xem tài liệu học tập hoặc thao tác thí nghiệm ảo 3D, Access Token **chắc chắn sẽ hết hạn**.
- Khi client gửi tiếp request (ví dụ: `POST /api/v1/exams/attempts/{attemptId}/answers` hoặc `GET /api/v1/exams/{examId}/my-attempt`) với Access Token đã hết hạn trong header `Authorization: Bearer <expiredToken>`:
  - `JwtTokenUtils.validateToken()` bắt được `ExpiredJwtException` và ném `CustomException("Expired or invalid JWT token", HttpStatus.UNAUTHORIZED)`.
  - `JwtTokenFilter` bắt ngoại lệ, xóa sạch `SecurityContextHolder` và gọi `httpServletResponse.sendError(401, "Expired or invalid JWT token")`.

#### 2. Cấu trúc phản hồi lỗi khi Access Token hết hạn (HTTP 401):
```json
{
  "timestamp": "2026-09-05T15:30:45.123+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Expired or invalid JWT token",
  "path": "/api/v1/exams/attempts/a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d/answers"
}
```

#### 3. Quy trình tự động làm mới và thử lại (Silent Refresh Pattern) dành cho Frontend:

> [!IMPORTANT]
> **Điểm mấu chốt cho Frontend Developer:** Endpoint `POST /api/v1/users/refresh` đã được cấu hình trong danh sách `UNAUTHENTICATED_PATHS` của `JwtTokenFilter`. Do đó, client **không cần phải gỡ bỏ Access Token cũ** ra khỏi header khi gọi `/refresh`. Backend sẽ bỏ qua việc kiểm tra Access Token trên endpoint này.

**Mã mẫu Axios Response Interceptor khuyến nghị cho đội Frontend:**
```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  headers: { 'Content-Type': 'application/json' }
});

// Request Interceptor: Luôn tự động gắn Bearer Token nếu có
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response Interceptor: Tự động bắt 401 -> Refresh Token -> Retry request ban đầu
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Kiểm tra mã 401 và không lặp vô tận trên chính endpoint refresh / signin
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (originalRequest.url.includes('/users/refresh') || originalRequest.url.includes('/users/signin')) {
        // Refresh token cũng đã hết hạn (quá 7 ngày) hoặc sai -> Đăng xuất bắt buộc
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      if (isRefreshing) {
        // Nếu đang có tiến trình refresh chạy dở, đẩy request này vào hàng đợi
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((token) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          return api(originalRequest);
        }).catch(err => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        const res = await axios.post('http://localhost:8080/api/v1/users/refresh', {
          refreshToken: refreshToken
        });

        const { accessToken, refreshToken: newRefreshToken } = res.data.data;
        localStorage.setItem('accessToken', accessToken);
        localStorage.setItem('refreshToken', newRefreshToken);

        api.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;

        processQueue(null, accessToken);
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        localStorage.clear();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

export default api;
```

---

## 7. Luồng Nghiệp Vụ Chính

### 7.1. Luồng Đăng Nhập và Làm Mới Token

```
Client → POST /users/signin (username, password)
       ← accessToken + refreshToken

Client (khi access token hết hạn) → POST /users/refresh (refreshToken)
       ← accessToken mới + refreshToken mới (cũ bị hủy)

Client → POST /users/logout (refreshToken)
       ← Refresh token bị thu hồi
```

### 7.2. Luồng Thi Trắc Nghiệm (PRACTICE — Multi-attempt)

```
1. POST /exams/{examId}/attempts
   → 201 Created (attempt, status = IN_PROGRESS, attemptNumber = N)
   
2. POST /exams/attempts/{attemptId}/answers (nhiều lần)
   → 200 OK (lưu từng câu trả lời)

3. PUT /exams/attempts/{attemptId}/submit
   → SELECT FOR UPDATE (chống race condition)
   → Tính điểm: Score = (Số đúng / Tổng) × 10
   → 200 OK (ExamAttemptDTO, status = GRADED, totalScore)

4. (PRACTICE) → Lượt mới: Quay lại bước 1
   (MIDTERM/FINAL/QUIZ) → 409 nếu thi lại
```

### 7.3. Luồng Thí Nghiệm Ảo

```
1. (INSTRUCTOR) POST /experiments/{id}/assign → Giao bài
2. (STUDENT) POST /experiments/assignments/{id}/submit → Nộp file + số liệu
3. (INSTRUCTOR/TA) POST /experiments/submissions/{id}/scores → Chấm Rubric
4. (INSTRUCTOR) POST /experiments/submissions/{id}/confirmation → Chốt điểm
```

### 7.4. Luồng Câu Hỏi → Đề Thi

```
1. POST /questions → Tạo câu hỏi (status: DRAFT/PENDING)
2. PUT /questions/{id}/approve → Phê duyệt (status: APPROVED)
3. POST /exams → Tạo kỳ thi (kèm matrixId)
4. POST /exams/{id}/generate-questions → Sinh đề tự động
   HOẶC POST /exams/{id}/questions → Thêm thủ công
```

---

## 8. Ví Dụ Postman

### 8.1. Đăng nhập

```
POST http://localhost:8080/api/v1/users/signin
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123456"
}
```

**Lưu token:**
```javascript
// Postman Test script
pm.environment.set("accessToken", pm.response.json().data.accessToken);
pm.environment.set("refreshToken", pm.response.json().data.refreshToken);
```

### 8.2. Tạo kỳ thi PRACTICE

```
POST http://localhost:8080/api/v1/exams
Authorization: Bearer {{accessToken}}
Content-Type: application/json

{
  "classId": "{{classId}}",
  "title": "Luyện tập Chương 1 - Cơ học",
  "examType": "PRACTICE",
  "durationMinutes": 30
}
```

### 8.3. Sinh viên bắt đầu làm bài

```
POST http://localhost:8080/api/v1/exams/{{examId}}/attempts
Authorization: Bearer {{studentToken}}
```

### 8.4. Nộp câu trả lời

```
POST http://localhost:8080/api/v1/exams/attempts/{{attemptId}}/answers
Authorization: Bearer {{studentToken}}
Content-Type: application/json

{
  "questionId": "{{questionId}}",
  "selectedOptionIds": ["{{optionId}}"],
  "answerText": null
}
```

### 8.5. Nộp bài thi

```
PUT http://localhost:8080/api/v1/exams/attempts/{{attemptId}}/submit
Authorization: Bearer {{studentToken}}
```

### 8.6. Nộp kết quả thí nghiệm ảo

```
POST http://localhost:8080/api/v1/experiments/assignments/{{assignmentId}}/submit
Authorization: Bearer {{studentToken}}
Content-Type: multipart/form-data

file: [chọn file]
rawDataJson: {"g": 9.81, "measurements": [9.78, 9.80, 9.83]}
```

### 8.7. Kích hoạt phân tích CTT

```
POST http://localhost:8080/api/v1/analytics/trigger
Authorization: Bearer {{adminToken}}
Content-Type: application/json

{
  "period": "2026-HK1"
}
```

---

## 9. Kiểm Định Tính Nhất Quán API & Khuyến Nghị Kiến Trúc

### 9.1. Điểm Mạnh Đã Xác Nhận

✅ **Định dạng response nhất quán** — Tất cả endpoint dùng `ApiResponse<T>` cùng cấu trúc  
✅ **Versioning rõ ràng** — `/api/v1/` trên toàn bộ  
✅ **JWT + Token Rotation** — Bảo mật tốt, Refresh Token bị thu hồi khi dùng  
✅ **Phân quyền chi tiết** — `@PreAuthorize` từng endpoint, phân biệt rõ ADMIN/INSTRUCTOR/TA/STUDENT  
✅ **Validation** — `@Valid` + `@NotNull`, `@NotBlank`, `@Email`, `@Size` trên tất cả DTO  
✅ **Race condition prevention** — Pessimistic Write Lock khi nộp bài thi  
✅ **Multi-attempt logic** — PRACTICE cho nhiều lượt, MIDTERM/FINAL/QUIZ khóa 1 lượt  
✅ **Error handling tập trung** — `GlobalExceptionHandlerController` xử lý đủ các loại exception  
✅ **CTT Analytics** — Tích hợp phân tích học thuật chuẩn (p-value, Discrimination Index)  

### 9.2. Vấn Đề Cần Cải Thiện & Trạng Thái Xử Lý

#### ✅ Các Vấn Đề Đã Giải Quyết (Resolved)

1. **Chuẩn hóa DTO chấm điểm bài nộp thí nghiệm ảo (`gradeSubmission`):**
   - **Hiện trạng trước:** `ExperimentController` nhận `Object scoreDTO` tự do không có schema.
   - **Giải pháp đã thực hiện:** Tạo mới `GradeSubmissionDTO` (`rubricId`, `score`, `feedback`, `comment`) chuẩn hóa, tích hợp đầy đủ vào Service và Controller. Bộ test `ExperimentControllerTest` (11/11) pass 100%.

2. **Chuẩn hóa DTO xác nhận bài nộp thí nghiệm ảo (`confirmSubmission`):**
   - **Hiện trạng trước:** Nhận `Map<String, Object> confirmDTO`.
   - **Giải pháp đã thực hiện:** Tạo mới `ConfirmSubmissionDTO` (`note: String`) chuẩn hóa.

3. **Bổ sung endpoint tra cứu toàn bộ lịch sử thi của sinh viên:**
   - **Hiện trạng trước:** Chỉ có `GET /api/v1/exams/{examId}/my-attempt` (trả về 1 lượt duy nhất).
   - **Giải pháp đã thực hiện:** Bổ sung endpoint `GET /api/v1/exams/{examId}/my-attempts` trả về `List<ExamAttemptDTO>` sắp xếp tăng dần theo `attemptNumber`. Hỗ trợ toàn diện các đề thi luyện tập `PRACTICE` cho phép làm nhiều lần. Bộ test `ExamControllerTest` (17/17) pass 100%.

---

#### ⚠️ Các Vấn Đề Đang Tồn Tại & Khuyến Nghị Nâng Cấp

##### 🔴 Mức độ Cao (Nên làm trước khi dữ liệu phình to)

**1. Chưa có phân trang (Pagination) cho một số endpoint trả danh sách lớn (`List<>`):**
- `GET /api/v1/experiments` → `List<ExperimentDTO>`
- `GET /api/v1/classes/{id}/activity-logs` → `List<ActivityLog>`
- `GET /api/v1/students/me/activity-logs` → `List<ActivityLog>`
- **Đánh giá rủi ro:** Nhật ký hoạt động của sinh viên trong một lớp có thể tăng lên hàng chục nghìn bản ghi chỉ sau một vài kỳ thi/bài thí nghiệm. Nếu không phân trang bằng `Pageable` (`page`, `size`), việc tải toàn bộ danh sách dễ gây nghẽn RAM máy chủ, chậm mạng và nguy cơ Crash/OOM trên trình duyệt Frontend.
- **Khuyến nghị:** Chuyển các endpoint trên sang nhận tham số `Pageable` và trả về `Page<T>` (giống như `AuditLog` và `QuestionBank`).

##### 🟡 Mức độ Trung bình (Cần chuẩn hóa trong giai đoạn tới)

**2. Không đồng nhất giữa mã HTTP Status 200 và 201 khi tạo mới tài nguyên:**
```java
// ExamController.java
return ResponseEntity.status(HttpStatus.CREATED).body(...) // HTTP 201, nhưng ApiResponse.status = 201
// UserController.java /signup
return ResponseEntity.ok(...) // HTTP 200
```
- **Đánh giá:** Một số API tạo mới trả `201 Created`, một số khác lại trả `200 OK`.
- **Khuyến nghị:** Chuẩn hóa quy ước toàn hệ thống: Mọi API `POST` tạo mới thực thể trả về `201 Created`, các API cập nhật/truy vấn trả về `200 OK`.

**3. Thuộc tính `SubmitExperimentDTO.rawDataJson` dạng `JsonNode` tự do chưa có ràng buộc schema:**
- Dữ liệu số liệu đo đạc thực nghiệm nhận JSON tự do, Frontend không có mẫu schema cụ thể để validate trước khi gửi.
- **Khuyến nghị:** Cung cấp JSON Schema mẫu trong tài liệu hoặc định nghĩa DTO chi tiết cho từng loại bài thí nghiệm (Cơ học, Nhiệt học, Điện từ).

**4. Bộ lọc Rate Limiting (`RateLimitFilter`) hoạt động trên RAM cục bộ (In-Memory Sliding Window):**
- Hiện tại sử dụng `ConcurrentHashMap<String, Queue<Long>>` trên bộ nhớ JVM của 1 Pod.
- **Đánh giá:** Hoàn toàn an toàn và đáp ứng tốt khi chạy 1 Pod đơn lẻ. Tuy nhiên, khi hệ thống mở rộng quy mô theo chiều ngang (Horizontal Pod Autoscaling - HPA) với nhiều Pod đằng sau Load Balancer, mỗi Pod sẽ có bộ đếm riêng làm tăng ngưỡng chịu tải thực tế lên `limit * N`.
- **Khuyến nghị:** Khi hệ thống thực sự scale đa Pod, chuyển dịch việc giới hạn tần suất lên tầng API Gateway (Nginx `limit_req_zone`, Cloudflare WAF) hoặc dùng Redis Token Bucket (`Bucket4j-Redis`).

##### 🟢 Mức độ Cải tiến nhỏ (Thẩm mỹ & Nhất quán phong cách mã)

**5. `EvidenceController` định nghĩa Base Path trực tiếp tại `/api/v1` thay vì có tiền tố riêng:**
- `EvidenceController` dùng các endpoint `/api/v1/students/{id}/evidence` và `/api/v1/evidence/{id}` thay vì một base prefix đồng nhất như các controller khác.
- **Khuyến nghị:** Giữ nguyên để bảo đảm tính tương thích với URL hiện hành hoặc gom nhóm có chủ đích tại tầng routing nếu cần tái cấu trúc.

**6. `AnalyticsController.triggerAggregation` trả về `ResponseEntity<Void>` thay vì bọc trong `ApiResponse<Void>`:**
- Khác biệt nhỏ so với toàn bộ 106 endpoints còn lại vốn luôn trả về thân JSON chuẩn `ApiResponse<T>`.
- **Khuyến nghị:** Đổi kiểu trả về thành `ResponseEntity<ApiResponse<Void>>` để bảo đảm 100% API đều có chung cấu trúc body `{status, message, data}`.

---

*Tài liệu này được tạo từ phân tích trực tiếp source code — không có thông tin hallucinate.*  
*Mọi endpoint, method, parameter và annotation đều được truy xuất từ controller và DTO thực tế.*
