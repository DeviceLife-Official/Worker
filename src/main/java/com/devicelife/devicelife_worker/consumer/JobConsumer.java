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

    // ✅ SQS 리스너
    @SqsListener("${custom.sqs.queue-name}")
    public void listen(String messageBody) {
        log.info("🚀 SQS raw 메시지 수신: {}", messageBody);

        try {
            // 0) JSON -> JobMessage (JavaType 헤더 무시)
            JobMessage message = objectMapper.readValue(messageBody, JobMessage.class);
            log.info("✅ JobMessage 변환 성공: {}", message);

            // 1. Payload 요청
            EvaluationPayload payload = backendClient.getPayload(message.evaluationId());
            log.info("✅ Payload 획득 완료: ID={}", payload.evaluationId());

            // 2. 평가 로직 실행
            EvaluationResult result = evaluationService.evaluate(payload);

            //  각 분야별 점수/등급 로그 출력
            log.info("✅ 평가 완료: 연동성={} ({}), 편의성={} ({}), 라이프스타일={} ({})",
                    result.compatibilityScore(), result.compatibilityGrade(),
                    result.convenienceScore(), result.convenienceGrade(),
                    result.lifestyleScore(), result.lifestyleGrade());

            // 3. 결과 전송
            backendClient.sendResult(result);
            log.info("✅ 결과 전송 완료. 작업 끝!");

        } catch (Exception e) {
            log.error("❌ 작업 처리 중 에러 발생 (DLQ로 이동됨) raw={}",messageBody, e);
            throw new RuntimeException(e);
        }
    }
}