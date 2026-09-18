package com.vatly1.example.model.request;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendAiMessageDTO {

    @NotBlank(message = "Message content is required")
    private String content;
}