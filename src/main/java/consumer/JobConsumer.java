package consumer;

import client.BackendClient;
import dto.EvaluationPayload;
import dto.EvaluationResult;
import dto.JobMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import service.EvaluationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobConsumer {

    private final BackendClient backendClient;
    private final EvaluationService evaluationService;

    // ✅ SQS 리스너: 메시지가 오면 이 함수가 자동으로 실행됨
    // (큐 이름은 application.yml의 custom.sqs.queue-name에서 가져옴)
    @SqsListener("${custom.sqs.queue-name}")
    public void listen(JobMessage message) {
        log.info("🚀 SQS 메시지 수신: {}", message);

        try {
            // 1. Payload 요청 (Backend에서 데이터 가져오기)
            EvaluationPayload payload = backendClient.getPayload(message.evaluationId());
            log.info("✅ Payload 획득 완료: ID={}", payload.evaluationId());

            // 2. 평가 로직 실행 (Service)
            EvaluationResult result = evaluationService.evaluate(payload);
            log.info("✅ 평가 완료: 총점={}", result.totalScore());

            // 3. 결과 전송 (Backend로 점수 쏘기)
            backendClient.sendResult(result);
            log.info("✅ 결과 전송 완료. 작업 끝!");

            // (함수가 에러 없이 끝나면 SQS 메시지는 자동으로 삭제됨)

        } catch (Exception e) {
            log.error("❌ 작업 처리 중 에러 발생,, DLQ로 이동됨)", e);
            throw e; // 에러를 다시 던져야 SQS가 "실패"로 처리하고 재시도/DLQ 보냄
        }
    }
}

