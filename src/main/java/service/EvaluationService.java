package service;

import dto.EvaluationPayload;
import dto.EvaluationResult;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {

    public EvaluationResult evaluate(EvaluationPayload payload) {
        // TODO: 여기에 진짜 알고리즘 구현 (Compatibility, Convenience 등)

        // 지금은 더미(Dummy) 데이터 리턴
        int score1 = 80;
        int score2 = 70;
        int score3 = 90;
        int score4 = 60;
        int total = (score1 + score2 + score3 + score4) / 4;

        return new EvaluationResult(
                payload.evaluationId(),
                total,
                score1,
                score2,
                score3,
                score4
        );
    }
}

