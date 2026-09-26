package com.vatly1.example.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferStudentDTO {

    @NotNull(message = "ID sinh viên không được để trống")
    @Schema(description = "ID của sinh viên cần chuyển ca thi", example = "33333333-3333-3333-3333-333333333333")
    private UUID studentId;

    @Schema(description = "ID lớp học gốc của sinh viên (nếu để trống hệ thống sẽ tự tìm lớp cùng môn)", example = "cccccccc-cccc-cccc-cccc-cccccccccccc")
    private UUID originalClassId;

    @Schema(description = "Lý do chuyển ca thi", example = "Trùng lịch thi môn Giải tích 1, xin thi bù ca chiều")
    private String reason;
}
