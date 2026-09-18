package com.vatly1.example.dto.response;

import com.vatly1.example.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSnapshotDTO {
    private UUID snapshotId;
    private UUID classId;
    private UUID studentId;
    private String period;
    private DashboardDataDTO data;
    private Instant generatedAt;
}
