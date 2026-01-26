package com.devicelife.devicelife_worker.dto;

public record EvaluationResult(
        Long combinationId,      // [수정] evaluationId -> combinationId
        Long evaluationVersion,  // [추가] 버전 정보 필수 포함
        Integer totalScore,      // [추가] 백엔드 DTO와 이름 맞춤
        Integer compatibilityScore,
        Integer convenienceScore,
        Integer lifestyleScore,
        String compatibilityGrade,
        String convenienceGrade,
        String lifestyleGrade
) {}