package com.vatly1.example.dto.request;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import com.vatly1.example.entity.enums.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLearningMaterialDTO {

    private UUID topicId;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Material type is required")
    private MaterialType type;

    private String contentText;

    private String sourceCitation;
    
    private MultipartFile file;

}