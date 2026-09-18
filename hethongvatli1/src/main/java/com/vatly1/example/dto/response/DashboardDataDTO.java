package com.vatly1.example.dto.response;

import com.vatly1.example.dto.dto.*;
import com.vatly1.example.dto.request.*;
import com.vatly1.example.dto.response.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDataDTO {
    private Double avgScore;
    private Integer completedTopics;
    private Integer totalTopics;
    private Integer labsConfirmed;
    private Integer aiSessionsCount;
    private Integer totalExamsTaken;
    private Instant lastUpdated;
}