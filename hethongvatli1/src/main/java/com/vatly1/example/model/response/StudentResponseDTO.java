package com.vatly1.example.model.response;

import com.vatly1.example.entity.enums.GenderType;
import com.vatly1.example.entity.enums.UserRole;
import com.vatly1.example.entity.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin chi tiết của Sinh viên (Tài khoản, Hồ sơ cá nhân và Lớp học phần)")
public class StudentResponseDTO {

    @Schema(description = "ID người dùng (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;

    @Schema(description = "Tên đăng nhập", example = "sv_an01")
    private String username;

    @Schema(description = "Địa chỉ email", example = "an.nguyen@student.edu.vn")
    private String email;

    @Schema(description = "Mã sinh viên", example = "SV202601")
    private String studentCode;

    @Schema(description = "Họ và tên sinh viên", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Ngày sinh", example = "2004-08-20")
    private LocalDate dateOfBirth;

    @Schema(description = "Giới tính (MALE, FEMALE, OTHER)", example = "MALE")
    private GenderType gender;

    @Schema(description = "Số điện thoại liên lạc", example = "0987654321")
    private String phone;

    @Schema(description = "Đường dẫn ảnh đại diện", example = "https://example.com/avatars/an.jpg")
    private String avatarUrl;

    @Schema(description = "Tiểu sử / Ghi chú", example = "Sinh viên lớp Vật lí 1 - K68")
    private String bio;

    @Schema(description = "Vai trò người dùng trong hệ thống", example = "STUDENT")
    private UserRole role;

    @Schema(description = "Trạng thái tài khoản (ACTIVE, INACTIVE, LOCKED)", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "Danh sách lớp học phần sinh viên đã ghi danh")
    private List<StudentClassItemDTO> enrolledClasses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Thông tin lớp học phần sinh viên đã ghi danh")
    public static class StudentClassItemDTO {
        @Schema(description = "ID lớp học phần (UUID)")
        private UUID classId;

        @Schema(description = "Mã lớp học phần", example = "PHY101-01")
        private String classCode;

        @Schema(description = "Trạng thái ghi danh (ACTIVE, DROPPED, COMPLETED)", example = "ACTIVE")
        private String status;

        @Schema(description = "Thời gian ghi danh")
        private Instant enrolledAt;
    }
}
