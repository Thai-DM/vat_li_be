# TÀI LIỆU TÍCH HỢP FRONTEND: HỆ THỐNG GIÁM SÁT THI & CẤM THI (EXAM PROCTORING & DISQUALIFICATION)

Tài liệu này được biên soạn đầy đủ và chi tiết dành riêng cho đội ngũ **Frontend (Web/Mobile)** nhằm tích hợp tính năng **Giám sát thi 3 chế độ**, **Ghi nhận vi phạm** và **Giảng viên cấm thi (Disqualification)**.

---

## I. TỔNG QUAN KIẾN TRÚC & CÁC CHẾ ĐỘ GIÁM SÁT

Hệ thống hỗ trợ 3 chế độ giám sát (`proctoringMode`) được cấu hình độc lập cho từng bài thi (`Exam`):

| Chế độ | Mã Enum | Ý nghĩa & Hành vi Frontend | Ghi Log & Bộ đếm |
| :--- | :--- | :--- | :--- |
| **Không giám sát** | `NONE` | Bài thi tự do, mở. Frontend **không cần** chặn phím tắt, không bắt sự kiện chuyển tab/focus. | Server tự động bỏ qua vi phạm, `violationCount = 0`. |
| **Cơ bản** | `BASIC` | Giám sát các thao tác cơ bản:<br>• Chuyển tab / ẩn trình duyệt (`visibilitychange`)<br>• Mất focus chuột ra ngoài cửa sổ (`blur`)<br>• Chặn chuột phải (`contextmenu`), phím in (`Ctrl+P`)<br>• Hiển thị Toast cảnh báo sinh viên. | Mỗi vi phạm gửi API về server, tăng `violationCount` và lưu chi tiết vào `exam_violation_logs`. |
| **Nghiêm ngặt** | `STRICT` | **Bao gồm toàn bộ của BASIC** cộng thêm:<br>• **Bắt buộc toàn màn hình (Fullscreen)**: Thoát toàn màn hình sẽ cảnh báo đếm ngược và ghi nhận vi phạm.<br>• **Chặn sao chép / dán**: Chặn `copy`, `cut`, `paste`.<br>• **Phát hiện mở DevTools**: Chặn `F12`, `Ctrl+Shift+I`, phát hiện resize viewport DevTools.<br>• **Phát hiện đa màn hình (Dual display)**.<br>• **Cấu hình tự động đình chỉ (`autoBanOnLimit`)**: Nếu bật, khi `violationCount >= maxViolationsAllowed`, bài thi tự động chuyển sang `DISQUALIFIED`. | Ghi log chi tiết mức độ nghiêm trọng (`HIGH`/`CRITICAL`). Nếu bật auto-ban, tự động khóa bài thi. |

---

## II. DANH SÁCH ENUMS & KIỂU DỮ LIỆU CHUẨN

### 1. `ProctoringMode` (Chế độ giám sát bài thi)
```typescript
export enum ProctoringMode {
  NONE = 'NONE',     // Không giám sát
  BASIC = 'BASIC',   // Giám sát cơ bản (chuyển tab, mất focus, chuột phải)
  STRICT = 'STRICT'  // Giám sát nghiêm ngặt (fullscreen, chặn devtools, copy/paste, dual monitor)
}
```

### 2. `ViolationType` (Loại vi phạm quy chế)
```typescript
export enum ViolationType {
  TAB_SWITCH = 'TAB_SWITCH',                       // Rời tab / chuyển tab khác
  WINDOW_BLUR = 'WINDOW_BLUR',                     // Mất focus chuột ra khỏi trình duyệt
  FULLSCREEN_EXIT = 'FULLSCREEN_EXIT',             // Thoát chế độ toàn màn hình
  DEVTOOLS_OPENED = 'DEVTOOLS_OPENED',             // Mở F12 / Inspect phần tử
  COPY_PASTE_ATTEMPT = 'COPY_PASTE_ATTEMPT',       // Cố tình copy hoặc paste nội dung
  RIGHT_CLICK = 'RIGHT_CLICK',                     // Click chuột phải
  SHORTCUT_RESTRICTED = 'SHORTCUT_RESTRICTED',     // Nhấn phím tắt cấm (PrintScreen, Ctrl+P, Ctrl+S)
  MULTIPLE_DISPLAY = 'MULTIPLE_DISPLAY',           // Phát hiện cắm nhiều màn hình
  INSTRUCTOR_DISQUALIFIED = 'INSTRUCTOR_DISQUALIFIED', // Giảng viên bấm nút cấm thi
  OTHER = 'OTHER'                                  // Vi phạm khác / Hệ thống tự động khóa
}
```

### 3. `ViolationSeverity` (Mức độ nghiêm trọng của vi phạm)
```typescript
export enum ViolationSeverity {
  LOW = 'LOW',         // Nhẹ (VD: Mất focus trong 1-2 giây, click ra ngoài)
  MEDIUM = 'MEDIUM',   // Trung bình (VD: Chuyển tab rời trang thi)
  HIGH = 'HIGH',       // Nặng (VD: Mở DevTools, thoát Fullscreen cố tình, cắm màn hình phụ)
  CRITICAL = 'CRITICAL'// Nghiêm trọng (VD: Vượt quá giới hạn vi phạm, Giảng viên cấm thi trực tiếp)
}
```

### 4. `AttemptStatus` (Trạng thái lượt thi)
```typescript
export enum AttemptStatus {
  IN_PROGRESS = 'IN_PROGRESS',   // Đang làm bài
  SUBMITTED = 'SUBMITTED',       // Đã nộp bài
  GRADED = 'GRADED',             // Đã chấm điểm
  DISQUALIFIED = 'DISQUALIFIED'  // ĐÃ BỊ ĐÌNH CHỈ / CẤM THI
}
```

---

## III. DANH SÁCH REST APIS CHI TIẾT

> **Base URL:** `/api/v1/exams`  
> **Headers bắt buộc:** `Authorization: Bearer <access_token>`

---

### 1. Cấu hình chế độ giám sát khi Tạo / Cập nhật bài thi
- **Role:** `ADMIN`, `INSTRUCTOR`
- **Method & Endpoints:**
  - `POST /api/v1/exams` (Tạo bài thi)
  - `PUT /api/v1/exams/{examId}` (Cập nhật bài thi)

#### Request Body bổ sung:
```json
{
  "classId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "title": "Kiểm tra Giữa kỳ Vật lý 1",
  "examType": "QUIZ",
  "durationMinutes": 60,
  "startTime": "2026-10-18T08:00:00Z",
  "endTime": "2026-10-18T10:00:00Z",
  "proctoringMode": "STRICT",
  "maxViolationsAllowed": 5,
  "autoBanOnLimit": false
}
```
* **Ghi chú trường:**
  - `proctoringMode`: `'NONE'` | `'BASIC'` | `'STRICT'` (mặc định `'NONE'`).
  - `maxViolationsAllowed`: Số lần vi phạm tối đa (mặc định `5`).
  - `autoBanOnLimit`: `true` nếu muốn hệ thống tự khóa bài thi ngay khi đạt ngưỡng `maxViolationsAllowed`; `false` nếu muốn để Giảng viên tự quyết định ấn nút cấm thi.

---

### 2. Sinh viên gửi báo cáo sự kiện vi phạm (Log Violation)
- **Role:** `STUDENT`, `ADMIN`
- **Method:** `POST`
- **Endpoint:** `/api/v1/exams/attempts/{attemptId}/violations`

#### Request Body:
```json
{
  "violationType": "TAB_SWITCH",
  "severity": "MEDIUM",
  "description": "Sinh viên rời tab thi sang cửa sổ khác trong 12 giây",
  "metadataJson": "{\"awayDurationSeconds\": 12, \"activeWindow\": \"Google Chrome\"}"
}
```

#### Response Success (`200 OK`):
```json
{
  "status": 200,
  "message": "Ghi nhận vi phạm thành công. Lần vi phạm thứ: 3",
  "data": {
    "currentViolationCount": 3,
    "maxViolationsAllowed": 5,
    "isDisqualified": false,
    "message": "Ghi nhận vi phạm thành công. Lần vi phạm thứ: 3"
  }
}
```
* **Khi bị Auto-Ban (nếu bật `autoBanOnLimit`):**
```json
{
  "status": 200,
  "message": "Bạn đã vượt quá số lần vi phạm cho phép. Bài thi đã bị tự động đình chỉ.",
  "data": {
    "currentViolationCount": 5,
    "maxViolationsAllowed": 5,
    "isDisqualified": true,
    "message": "Bạn đã vượt quá số lần vi phạm cho phép. Bài thi đã bị tự động đình chỉ."
  }
}
```

---

### 3. Xem danh sách lịch sử vi phạm chi tiết của một lượt thi
- **Role:** `ADMIN`, `INSTRUCTOR`, `TA`, `STUDENT` (Sinh viên chỉ xem được của chính mình)
- **Method:** `GET`
- **Endpoint:** `/api/v1/exams/attempts/{attemptId}/violations`

#### Response Success (`200 OK`):
```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "logId": "7b0a701a-828d-4f18-a6b1-cf5dbb6368d3",
      "attemptId": "ef6a88b7-197a-45e3-b0c6-ef0276e18b1c",
      "examId": "d65904d2-04ed-4bf8-9010-a37c2f5ef297",
      "studentId": "5d8a8a5e-112c-481f-95f5-0e6886abc39b",
      "studentName": "Nguyễn Văn An",
      "studentCode": "SV2026001",
      "violationType": "TAB_SWITCH",
      "severity": "MEDIUM",
      "violationCountAtTime": 1,
      "description": "Sinh viên rời tab thi sang cửa sổ khác trong 12 giây",
      "metadataJson": "{\"awayDurationSeconds\": 12}",
      "createdAt": "2026-10-18T14:15:30Z"
    },
    {
      "logId": "8f3b201a-939e-4f29-b7c2-df6ebb7479e4",
      "attemptId": "ef6a88b7-197a-45e3-b0c6-ef0276e18b1c",
      "examId": "d65904d2-04ed-4bf8-9010-a37c2f5ef297",
      "studentId": "5d8a8a5e-112c-481f-95f5-0e6886abc39b",
      "studentName": "Nguyễn Văn An",
      "studentCode": "SV2026001",
      "violationType": "INSTRUCTOR_DISQUALIFIED",
      "severity": "CRITICAL",
      "violationCountAtTime": 2,
      "description": "Giảng viên cấm thi: Gian lận chuyển tab tra cứu tài liệu",
      "metadataJson": "{\"instructorId\": \"267061fd-62d5-4105-ad1e-2531096b44aa\"}",
      "createdAt": "2026-10-18T14:16:00Z"
    }
  ]
}
```

---

### 4. Giảng viên xem danh sách thí sinh và số lần vi phạm (Live Monitor)
- **Role:** `ADMIN`, `INSTRUCTOR`, `TA`
- **Method:** `GET`
- **Endpoint:** `/api/v1/exams/{examId}/attempts`

#### Response Success (`200 OK`):
```json
{
  "status": 200,
  "message": "Success",
  "data": [
    {
      "attemptId": "ef6a88b7-197a-45e3-b0c6-ef0276e18b1c",
      "examId": "d65904d2-04ed-4bf8-9010-a37c2f5ef297",
      "examTitle": "Kiểm tra Giữa kỳ Vật lý 1",
      "studentId": "5d8a8a5e-112c-481f-95f5-0e6886abc39b",
      "studentUsername": "sv_an",
      "studentName": "Nguyễn Văn An",
      "studentCode": "SV2026001",
      "attemptNumber": 1,
      "status": "DISQUALIFIED",
      "startedAt": "2026-10-18T14:00:00Z",
      "submittedAt": null,
      "totalScore": null,
      "totalQuestions": 40,
      "correctAnswersCount": 15,
      "violationCount": 4,
      "isDisqualified": true,
      "disqualifiedReason": "Gian lận chuyển tab tra cứu tài liệu",
      "disqualifiedAt": "2026-10-18T14:16:00Z"
    }
  ]
}
```

---

### 5. Giảng viên ấn nút CẤM THI (Đình chỉ bài thi)
- **Role:** `ADMIN`, `INSTRUCTOR`
- **Method:** `POST`
- **Endpoint:** `/api/v1/exams/{examId}/attempts/{attemptId}/disqualify`

#### Request Body:
```json
{
  "reason": "Phát hiện gian lận: Chuyển tab tra cứu tài liệu liên tục"
}
```

#### Response Success (`200 OK`):
```json
{
  "status": 200,
  "message": "Đã đình chỉ bài làm của sinh viên thành công",
  "data": {
    "attemptId": "ef6a88b7-197a-45e3-b0c6-ef0276e18b1c",
    "examId": "d65904d2-04ed-4bf8-9010-a37c2f5ef297",
    "studentId": "5d8a8a5e-112c-481f-95f5-0e6886abc39b",
    "status": "DISQUALIFIED",
    "isDisqualified": true,
    "disqualifiedReason": "Phát hiện gian lận: Chuyển tab tra cứu tài liệu liên tục",
    "disqualifiedAt": "2026-10-18T14:16:00Z",
    "disqualifiedBy": "267061fd-62d5-4105-ad1e-2531096b44aa",
    "violationCount": 5
  }
}
```

---

### 6. Giảng viên mở lại bài thi (Hủy cấm thi / Reinstate)
- **Role:** `ADMIN`, `INSTRUCTOR`
- **Method:** `POST`
- **Endpoint:** `/api/v1/exams/{examId}/attempts/{attemptId}/reinstate`
- **Request Body:** Không cần body (`{}`)

#### Response Success (`200 OK`):
```json
{
  "status": 200,
  "message": "Đã khôi phục quyền làm bài cho sinh viên thành công",
  "data": {
    "attemptId": "ef6a88b7-197a-45e3-b0c6-ef0276e18b1c",
    "examId": "d65904d2-04ed-4bf8-9010-a37c2f5ef297",
    "studentId": "5d8a8a5e-112c-481f-95f5-0e6886abc39b",
    "status": "IN_PROGRESS",
    "isDisqualified": false,
    "disqualifiedReason": null,
    "disqualifiedAt": null,
    "disqualifiedBy": null,
    "violationCount": 5
  }
}
```

---

### 7. Tiến độ bài thi & Trạng thái đình chỉ thời gian thực
- **Role:** `STUDENT`, `INSTRUCTOR`, `ADMIN`
- **Method:** `GET`
- **Endpoint:** `/api/v1/exams/attempts/{attemptId}/progress`

#### Response Success (`200 OK`):
```json
{
  "status": 200,
  "message": "Success",
  "data": {
    "attemptId": "ef6a88b7-197a-45e3-b0c6-ef0276e18b1c",
    "examId": "d65904d2-04ed-4bf8-9010-a37c2f5ef297",
    "examTitle": "Kiểm tra Giữa kỳ Vật lý 1",
    "status": "IN_PROGRESS",
    "totalQuestions": 40,
    "answeredQuestions": 15,
    "startedAt": "2026-10-18T14:00:00Z",
    "durationMinutes": 60,
    "expiresAt": "2026-10-18T15:00:00Z",
    "remainingSeconds": 2640,
    "isExpired": false,
    "violationCount": 2,
    "isDisqualified": false,
    "disqualifiedReason": null,
    "proctoringMode": "STRICT",
    "maxViolationsAllowed": 5
  }
}
```

---

## IV. XỬ LÝ LỖI & MÃ PHẢN HỒI KHI SINH VIÊN BỊ CẤM THI

Khi sinh viên đã bị đình chỉ bài thi (`isDisqualified = true` hoặc `status = DISQUALIFIED`), **TẤT CẢ các API làm bài sau đây của sinh viên sẽ trả về HTTP `403 FORBIDDEN`**:
- `POST /api/v1/exams/attempts/{attemptId}/answers` (Lưu câu trả lời)
- `POST /api/v1/exams/attempts/{attemptId}/autosave` (Lưu nháp hàng loạt)
- `POST /api/v1/exams/attempts/{attemptId}/submit` (Nộp bài thi)
- `GET /api/v1/exams/attempts/{attemptId}/questions` (Lấy đề thi)

#### Định dạng lỗi trả về từ Backend (`403 Forbidden`):
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Bạn đã bị giảng viên đình chỉ bài thi do vi phạm quy chế. Lý do: Gian lận chuyển tab tra cứu tài liệu"
}
```

---

## V. HƯỚNG DẪN TÍCH HỢP CODE FRONTEND (JAVASCRIPT / REACT)

Dưới đây là đoạn code hook hoàn chỉnh giúp Frontend lắng nghe và báo cáo vi phạm theo từng chế độ:

```javascript
/**
 * useExamProctoring Hook dành cho màn hình làm bài của Sinh viên
 * @param {string} attemptId - ID lượt thi
 * @param {'NONE' | 'BASIC' | 'STRICT'} proctoringMode - Chế độ giám sát
 * @param {number} maxViolations - Số lần vi phạm tối đa
 * @param {function} onDisqualified - Callback kích hoạt khi bị cấm thi
 */
export function useExamProctoring({ attemptId, proctoringMode, maxViolations, onDisqualified }) {
  const [violationCount, setViolationCount] = useState(0);
  const lastReportTimeRef = useRef(0);

  // Hàm gửi log vi phạm về Backend (có debounce 2 giây chống spam)
  const reportViolation = useCallback(async (violationType, severity, description) => {
    if (proctoringMode === 'NONE') return;

    const now = Date.now();
    if (now - lastReportTimeRef.current < 2000) {
      return; // Bỏ qua nếu vừa gửi trong vòng 2 giây
    }
    lastReportTimeRef.current = now;

    try {
      const response = await api.post(`/exams/attempts/${attemptId}/violations`, {
        violationType,
        severity,
        description,
        metadataJson: JSON.stringify({ timestamp: new Date().toISOString() })
      });

      const data = response.data?.data;
      if (data) {
        setViolationCount(data.currentViolationCount);

        // Hiển thị cảnh báo Toast cho sinh viên
        toast.warning(`Cảnh báo vi phạm (${data.currentViolationCount}/${data.maxViolationsAllowed}): ${description}`);

        // Nếu bị cấm thi tự động
        if (data.isDisqualified) {
          onDisqualified('Bạn đã vượt quá số lần vi phạm cho phép và bị đình chỉ làm bài thi.');
        }
      }
    } catch (err) {
      if (err.response?.status === 403) {
        onDisqualified(err.response?.data?.message || 'Bạn đã bị đình chỉ bài thi.');
      }
    }
  }, [attemptId, proctoringMode, onDisqualified]);

  useEffect(() => {
    if (proctoringMode === 'NONE') return;

    // 1. Giám sát chuyển tab / ẩn cửa sổ (áp dụng cho cả BASIC & STRICT)
    const handleVisibilityChange = () => {
      if (document.hidden) {
        reportViolation('TAB_SWITCH', 'MEDIUM', 'Rời khỏi tab thi hoặc chuyển sang ứng dụng khác');
      }
    };

    // 2. Giám sát mất focus chuột (chỉ ghi nhận khi cửa sổ mất focus)
    const handleWindowBlur = () => {
      reportViolation('WINDOW_BLUR', 'LOW', 'Mất focus chuột khỏi màn hình bài thi');
    };

    // 3. Chặn chuột phải (Context Menu)
    const handleContextMenu = (e) => {
      e.preventDefault();
      reportViolation('RIGHT_CLICK', 'LOW', 'Cố tình nhấn chuột phải');
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('blur', handleWindowBlur);
    document.addEventListener('contextmenu', handleContextMenu);

    // 4. Các cơ chế nâng cao chỉ bật trong chế độ STRICT
    let handleFullscreenChange = null;
    let handleKeyDown = null;
    let handleCopyPaste = null;

    if (proctoringMode === 'STRICT') {
      // Bắt buộc Fullscreen
      handleFullscreenChange = () => {
        if (!document.fullscreenElement) {
          reportViolation('FULLSCREEN_EXIT', 'HIGH', 'Thoát khỏi chế độ toàn màn hình');
        }
      };

      // Chặn copy / paste / cut
      handleCopyPaste = (e) => {
        e.preventDefault();
        reportViolation('COPY_PASTE_ATTEMPT', 'HIGH', 'Cố tình sao chép hoặc dán nội dung');
      };

      // Chặn phím F12, Ctrl+Shift+I, PrintScreen, Ctrl+P, Ctrl+S
      handleKeyDown = (e) => {
        if (
          e.key === 'F12' ||
          (e.ctrlKey && e.shiftKey && (e.key === 'I' || e.key === 'J' || e.key === 'C')) ||
          (e.ctrlKey && (e.key === 'u' || e.key === 'U' || e.key === 'p' || e.key === 's')) ||
          e.key === 'PrintScreen'
        ) {
          e.preventDefault();
          reportViolation('DEVTOOLS_OPENED', 'HIGH', `Cố tình nhấn phím cấm: ${e.key}`);
        }
      };

      document.addEventListener('fullscreenchange', handleFullscreenChange);
      document.addEventListener('copy', handleCopyPaste);
      document.addEventListener('paste', handleCopyPaste);
      document.addEventListener('cut', handleCopyPaste);
      window.addEventListener('keydown', handleKeyDown);
    }

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('blur', handleWindowBlur);
      document.removeEventListener('contextmenu', handleContextMenu);
      if (handleFullscreenChange) document.removeEventListener('fullscreenchange', handleFullscreenChange);
      if (handleCopyPaste) {
        document.removeEventListener('copy', handleCopyPaste);
        document.removeEventListener('paste', handleCopyPaste);
        document.removeEventListener('cut', handleCopyPaste);
      }
      if (handleKeyDown) window.removeEventListener('keydown', handleKeyDown);
    };
  }, [proctoringMode, reportViolation]);

  return { violationCount };
}
```

---

## VI. THIẾT KẾ GIAO DIỆN KHUYẾN NGHỊ (UX/UI SPECIFICATION)

### 1. Màn hình Sinh viên khi bị Cấm thi (Disqualified Screen Overlay)
- Khi nhận lỗi `403` hoặc `isDisqualified = true`:
  - Khóa toàn bộ form làm bài thi bằng một màn hình **Overlay màu đỏ đậm** không thể tắt.
  - Hiển thị thông báo lớn:
    > 🚫 **BÀI THI CỦA BẠN ĐÃ BỊ ĐÌNH CHỈ**  
    > **Lý do:** [Nội dung từ `disqualifiedReason`]  
    > **Thời điểm:** [Thời gian `disqualifiedAt`]  
    > *Vui lòng dừng làm bài và liên hệ trực tiếp với Giảng viên coi thi để được giải quyết.*
  - Vô hiệu hóa tất cả các nút: Lưu bài, Nộp bài, Chọn đáp án.

### 2. Màn hình Giám sát của Giảng viên (Live Exam Monitor)
- **Huy hiệu số lần vi phạm (`violationCount`):**
  - `0`: Badge màu xanh lá (`badge-success`)
  - `1 - 2`: Badge màu vàng cam (`badge-warning`)
  - `>= 3`: Badge màu đỏ rực rỡ (`badge-danger`)
- **Nút "Xem log" (Icon mắt/Lịch sử):**
  - Mở Drawer/Modal hiển thị danh sách dòng thời gian vi phạm (`timeline view`) lấy từ API `GET /attempts/{attemptId}/violations`.
- **Nút "CẤM THI" (Màu đỏ):**
  - Hiển thị modal:
    - *Tiêu đề:* Đình chỉ bài thi của sinh viên [Tên sinh viên] ([MSSV])
    - *Input:* Nhập lý do đình chỉ (bắt buộc)
    - *Nút xác nhận:* Gửi `POST /exams/{examId}/attempts/{attemptId}/disqualify`
- **Nút "MỞ LẠI BÀI THI" (Màu xanh dương / Un-ban):**
  - Chỉ hiển thị khi sinh viên đang ở trạng thái `DISQUALIFIED`.
  - Giảng viên nhấn xác nhận -> Gửi `POST /exams/{examId}/attempts/{attemptId}/reinstate` -> Đưa bài thi về lại `IN_PROGRESS` cho sinh viên tiếp tục làm bài.
