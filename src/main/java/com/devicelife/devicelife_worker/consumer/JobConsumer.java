package com.devicelife.devicelife_worker.consumer;

import com.devicelife.devicelife_worker.client.BackendClient;
import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import com.devicelife.devicelife_worker.dto.EvaluationResult;
import com.devicelife.devicelife_worker.dto.JobMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.devicelife.devicelife_worker.service.EvaluationService;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobConsumer {

    private final BackendClient backendClient;
    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SqsListener("${custom.sqs.queue-name}")
    public void listen(String messageBody) {
        log.info("🚀 SQS raw 메시지 수신: {}", messageBody);

        try {
            JobMessage message = objectMapper.readValue(messageBody, JobMessage.class);
            log.info("✅ JobMessage 변환 성공: {}", message);

            // 1. Payload 요청
            EvaluationPayload payload = backendClient.getPayload(message.evaluationId());
            // 필드명을 evaluationId에서 combinationId로 변경
            log.info("✅ Payload 획득 완료: ComboID={}, Version={}",
                    payload.combinationId(), payload.evaluationVersion());

            // 2. 평가 로직 실행
            EvaluationResult result = evaluationService.evaluate(payload);

            log.info("✅ 평가 완료: 연동성={} ({}), 편의성={} ({}), 라이프스타일={} ({})",
                    result.compatibilityScore(), result.compatibilityGrade(),
                    result.convenienceScore(), result.convenienceGrade(),
                    result.lifestyleScore(), result.lifestyleGrade());

            // 3. 결과 전송
            backendClient.sendResult(result);
            log.info("✅ 결과 전송 완료. 작업 끝!");

        } catch (Exception e) {
            log.error("❌ 작업 처리 중 에러 발생 (DLQ로 이동됨) raw={}", messageBody, e);
            throw new RuntimeException(e);
        }
    }
}