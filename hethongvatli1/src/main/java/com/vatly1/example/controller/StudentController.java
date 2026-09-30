package com.vatly1.example.controller;

import com.vatly1.example.exception.CustomException;
import com.vatly1.example.model.response.ApiResponse;
import com.vatly1.example.model.response.StudentImportResultDTO;
import com.vatly1.example.model.response.StudentResponseDTO;
import com.vatly1.example.service.IStudentExcelService;
import com.vatly1.example.service.IUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
@Tag(name = "Student Management", description = "APIs Tra cứu thông tin sinh viên, Nhập danh sách sinh viên từ Excel")
@SecurityRequirement(name = "bearerAuth")
public class StudentController {

    private final IUserService userService;
    private final IStudentExcelService studentExcelService;

    @Operation(summary = "Tìm kiếm sinh viên theo tên đăng nhập (username), mã sinh viên hoặc từ khóa",
            description = "Cho phép Giảng viên (INSTRUCTOR, TA) và Quản trị viên (ADMIN) tìm kiếm sinh viên bằng query param (username, keyword, hoặc studentCode).")
    @GetMapping({"/search", ""})
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<StudentResponseDTO>> searchStudent(
            @Parameter(description = "Tên đăng nhập (username)") @RequestParam(value = "username", required = false) String username,
            @Parameter(description = "Từ khóa tìm kiếm (username, studentCode, email)") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "Mã sinh viên (studentCode)") @RequestParam(value = "studentCode", required = false) String studentCode) {
        String query = username;
        if (query == null || query.isBlank()) {
            query = keyword;
        }
        if (query == null || query.isBlank()) {
            query = studentCode;
        }
        if (query == null || query.isBlank()) {
            throw new CustomException("Vui lòng cung cấp username, keyword hoặc studentCode để tìm kiếm sinh viên", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(ApiResponse.success(userService.getStudentByUsername(query)));
    }

    @Operation(summary = "Lấy thông tin chi tiết sinh viên theo tên đăng nhập hoặc mã sinh viên",
            description = "Tra cứu sinh viên bằng tên đăng nhập (username) hoặc mã sinh viên (studentCode) hoặc email. Trả về thông tin tài khoản, hồ sơ cá nhân và danh sách các lớp học phần đã tham gia.")
    @GetMapping({"/{username}", "/by-username/{username}"})
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA') or authentication.name == #username")
    public ResponseEntity<ApiResponse<StudentResponseDTO>> getStudentByUsername(
            @Parameter(description = "Tên đăng nhập (username) hoặc mã sinh viên (studentCode)")
            @PathVariable String username) {
        return ResponseEntity.ok(ApiResponse.success(userService.getStudentByUsername(username)));
    }

    @Operation(summary = "Lấy thông tin chi tiết sinh viên theo mã sinh viên",
            description = "Tra cứu sinh viên trực tiếp bằng mã sinh viên (studentCode, ví dụ: SV202601).")
    @GetMapping("/by-code/{studentCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA') or authentication.name == #studentCode")
    public ResponseEntity<ApiResponse<StudentResponseDTO>> getStudentByCode(
            @Parameter(description = "Mã sinh viên (studentCode)")
            @PathVariable String studentCode) {
        return ResponseEntity.ok(ApiResponse.success(userService.getStudentByUsername(studentCode)));
    }

    @Operation(summary = "Tải file mẫu Excel nhập danh sách sinh viên",
            description = "Tải xuống file Excel (.xlsx) chuẩn hóa để quản trị viên / giảng viên điền danh sách sinh viên cần tạo tài khoản hàng loạt. Mật khẩu mặc định tự động sinh theo Ngày sinh (ddMMyyyy).")
    @GetMapping("/import-excel/template")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<byte[]> downloadStudentTemplate() {
        byte[] excelBytes = studentExcelService.downloadStudentExcelTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mau_import_sinh_vien.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    @Operation(summary = "Tạo tài khoản sinh viên hàng loạt từ file Excel",
            description = "Tải lên tệp Excel (.xlsx, .xls) chứa danh sách sinh viên để tạo/cập nhật hàng loạt tài khoản người dùng và hồ sơ sinh viên. Mật khẩu mặc định tự động sinh từ Ngày sinh (ddMMyyyy, ví dụ sinh ngày 20/08/2004 sẽ có mật khẩu là 20082004). Có thể tự động ghi danh vào lớp học phần.")
    @PostMapping(value = "/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR', 'TA')")
    public ResponseEntity<ApiResponse<StudentImportResultDTO>> importStudentsFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "defaultPassword", required = false) String defaultPassword,
            @RequestParam(value = "classId", required = false) UUID classId) {
        StudentImportResultDTO result = studentExcelService.importStudentsFromExcel(file, defaultPassword, classId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Xử lý nhập danh sách sinh viên từ Excel hoàn tất"));
    }
}
