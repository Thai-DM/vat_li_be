package com.vatly1.example.service;

public interface IAnalyticsAggregationService {
    void aggregateTopicDifficulty(String period);
    void aggregateQuestionStats(String period);
    void aggregateAiGaps(String period);
    void aggregateMaterialEffectiveness(String period);
    void triggerFullAggregation(String period);
}
