package com.vatly1.example.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateClassSettingsDTO {

    private Boolean practiceEnabled;

    private Boolean quizEnabled;

    @Min(value = 1, message = "Số lượt luyện tập tối đa phải từ 1 trở lên")
    @Max(value = 100, message = "Số lượt luyện tập tối đa không vượt quá 100")
    private Integer maxPracticeAttempts;

    private Boolean practiceShowSolution;
}
