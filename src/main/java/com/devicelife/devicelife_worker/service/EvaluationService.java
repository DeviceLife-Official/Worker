package com.devicelife.devicelife_worker.service;

import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import com.devicelife.devicelife_worker.dto.EvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final CompatibilityEvaluator compatibilityEvaluator;

    public EvaluationResult evaluate(EvaluationPayload payload) {
        // --- 1. 연동성 평가 (구현 완료) ---
        int compScore = compatibilityEvaluator.calculate(payload);
        String compGrade = getGrade(compScore);

        // --- 2. 편의성 평가 (아직 로직 없음 -> 0점 처리) ---
        // int convScore = convenienceEvaluator.calculate(payload); // 나중에 구현
        int convScore = 0;
        String convGrade = getGrade(convScore);

        // --- 3. 라이프스타일 평가 (아직 로직 없음 -> 0점 처리) ---
        // int lifeScore = lifestyleEvaluator.calculate(payload); // 나중에 구현
        int lifeScore = 0;
        String lifeGrade = getGrade(lifeScore);

        // 최종 성적표 발송 (3개 분야)
        return new EvaluationResult(
                payload.evaluationId(),
                compScore, compGrade,
                convScore, convGrade,
                lifeScore, lifeGrade
        );
    }

    // ⭐ 만능 등급 판독기 (모든 분야 공통 사용)
    private String getGrade(int score) {
        if (score >= 90) return "최상";
        if (score >= 80) return "상";
        if (score >= 60) return "중"; // 65점(기본점수)은 여기에 포함
        if (score >= 40) return "하";
        return "최하"; // 0점은 여기에 포함
    }
}

