package client;

import dto.EvaluationPayload;
import dto.EvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class BackendClient {

    private final RestClient restClient;

    @Value("${custom.api.token}") // .env에서 가져온 비밀 토큰
    private String apiToken;

    // 1. 평가에 필요한 데이터(Payload) 받아오기
    public EvaluationPayload getPayload(Long evaluationId) {
        return restClient.get()
                .uri("/internal/evaluations/" + evaluationId + "/payload")
                .header("Authorization", "Bearer " + apiToken) // 보안 토큰
                .retrieve()
                .body(EvaluationPayload.class);
    }

    // 2. 계산된 결과(Result) 보내기
    public void sendResult(EvaluationResult result) {
        restClient.post()
                .uri("/internal/evaluations/result")
                .header("Authorization", "Bearer " + apiToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(result)
                .retrieve()
                .toBodilessEntity(); // 응답 본문 필요 없음
    }
}


