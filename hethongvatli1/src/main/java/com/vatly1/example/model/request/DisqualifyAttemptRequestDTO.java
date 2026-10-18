package com.vatly1.example.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisqualifyAttemptRequestDTO {

    @NotBlank(message = "Lý do cấm thi là bắt buộc")
    private String reason;
}
