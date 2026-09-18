package com.vatly1.example.dto;

import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import com.vatly1.example.entity.enums.EvidenceSourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceDTO {
    private UUID evidenceId;
    private UUID studentId;
    private EvidenceSourceType sourceType;
    private UUID sourceId;
    private UUID fileId;
    private String fileUrl;
    private Instant createdAt;
}
