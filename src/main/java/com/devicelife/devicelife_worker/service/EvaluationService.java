package com.devicelife.devicelife_worker.service;

import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import com.devicelife.devicelife_worker.dto.EvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final CompatibilityEvaluator compatibilityEvaluator;
    private final ConvenienceEvaluator convenienceEvaluator;
    private final LifestyleEvaluator lifestyleEvaluator;

    public EvaluationResult evaluate(EvaluationPayload payload) {
        // --- 1. 연동성 평가 (payload 내부의 devices 리스트를 사용하도록 Evaluator 수정 필요) ---
        int compScore = compatibilityEvaluator.calculate(payload);
        String compGrade = getGrade(compScore);

        // --- 2. 편의성 평가 (0점 처리) ---
        int convScore = convenienceEvaluator.calculate(payload);
        String convGrade = getGrade(convScore);

        // --- 3. 라이프스타일 평가 (0점 처리) ---
        int lifeScore = lifestyleEvaluator.calculate(payload);
        String lifeGrade = getGrade(lifeScore);

        int totalScore = compScore + convScore + lifeScore;

        // 최종 성적표 발송 (ID와 버전을 payload에서 정확히 추출)
        return new EvaluationResult(
                payload.combinationId(),
                payload.evaluationVersion(),
                totalScore,
                compScore,
                convScore,
                lifeScore,
                compGrade,
                convGrade,
                lifeGrade
        );
    }

    private String getGrade(int score) {
        if (score >= 90) return "최상";
        if (score >= 80) return "상";
        if (score >= 60) return "중";
        if (score >= 40) return "하";
        return "최하";
    }
}