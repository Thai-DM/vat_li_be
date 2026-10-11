package com.vatly1.example.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCloDTO {

    @NotBlank(message = "Mã CLO không được để trống")
    @Size(max = 50, message = "Mã CLO không được vượt quá 50 ký tự")
    private String cloCode;

    @NotBlank(message = "Mô tả CLO không được để trống")
    private String description;

    @NotNull(message = "Thứ tự sắp xếp không được để trống")
    private Integer orderIndex;

    @Builder.Default
    private Boolean isActive = true;
}
