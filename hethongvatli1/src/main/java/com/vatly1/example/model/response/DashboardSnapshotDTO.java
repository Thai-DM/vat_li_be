package com.vatly1.example.model.response;

import com.vatly1.example.model.dto.*;
import com.vatly1.example.model.request.*;
import com.vatly1.example.model.response.*;

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