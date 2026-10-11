package com.vatly1.example.model.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderTopicsDTO {

    @NotEmpty(message = "Danh sách ID bài học không được để trống")
    private List<UUID> topicIds;
}
