package com.devicelife.devicelife_worker.dto;
public record EvaluationResult(
        Long evaluationId,

        // 1. 연동성 (Compatibility)
        int compatibilityScore,
        String compatibilityGrade,

        // 2. 편의성 (Convenience)
        int convenienceScore,
        String convenienceGrade,

        // 3. 라이프스타일 (Lifestyle)
        int lifestyleScore,
        String lifestyleGrade
      )
    {}

