package com.vatly1.example.model.request;

import com.vatly1.example.entity.enums.ViolationSeverity;
import com.vatly1.example.entity.enums.ViolationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogViolationRequestDTO {

    @NotNull(message = "Violation type is required")
    private ViolationType violationType;

    private ViolationSeverity severity;

    private String description;

    private String metadataJson;
}
