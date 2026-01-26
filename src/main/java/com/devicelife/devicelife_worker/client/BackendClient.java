package com.devicelife.devicelife_worker.client;

import com.devicelife.devicelife_worker.dto.ApiResponse;
import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import com.devicelife.devicelife_worker.dto.EvaluationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class BackendClient {

    private final RestClient restClient;

    @Value("${INTERNAL_API_TOKEN}")
    private String apiToken;

    // 1. 평가에 필요한 데이터(Payload) 받아오기
    public EvaluationPayload getPayload(Long evaluationId) {
        log.info("🚀 백엔드로 보내는 토큰 확인: [{}]", apiToken);

        //  [수정] ApiResponse로 감싸서 받은 뒤 .result()만 꺼냄
        ApiResponse<EvaluationPayload> response = restClient.get()
                .uri("/internal/evaluations/" + evaluationId + "/payload")
                .header("X-Internal-Token", apiToken)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<EvaluationPayload>>() {}); // 👈 제네릭 타입 명시

        if (response != null && response.result() != null) {
            return response.result(); // 알맹이 반환
        }

        throw new RuntimeException("백엔드 응답이 비어있습니다. ID=" + evaluationId);
    }

    // 2. 계산된 결과(Result) 보내기
    public void sendResult(EvaluationResult result) {
        log.info("📤 백엔드로 결과 전송 시작: ComboID={}", result.combinationId());

        restClient.post()
                .uri("/internal/evaluations/" + result.combinationId() + "/result")
                .header("X-Internal-Token", apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(result)
                .retrieve()
                .toBodilessEntity();
    }
}