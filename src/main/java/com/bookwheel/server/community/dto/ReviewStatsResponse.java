package com.bookwheel.server.community.dto;

public record ReviewStatsResponse(
    int recommendedRatio,   // 추천 비율 (%)
    int notRecommendedRatio // 비추천 비율 (%)
) {}
