package com.vatly1.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsCronJob {

    private final IAnalyticsAggregationService aggregationService;

    // Run at 01:00 AM every day
    @Scheduled(cron = "0 0 1 * * *")
    public void runDailyAggregation() {
        String period = YearMonth.now().toString();
        log.info("[CRON] Starting daily analytics aggregation for period={}", period);
        try {
            aggregationService.triggerFullAggregation(period);
            log.info("[CRON] Analytics aggregation finished successfully for period={}", period);
        } catch (Exception e) {
            log.error("[CRON] Daily analytics aggregation failed: {}", e.getMessage(), e);
        }
    }
}