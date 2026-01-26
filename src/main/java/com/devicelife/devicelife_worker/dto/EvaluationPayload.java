package com.devicelife.devicelife_worker.dto;

import java.util.Map;

public record EvaluationPayload(
        Long evaluationId,
        Map<String, Object> specs, // 기기 스펙 (RAM, CPU 등)
        Map<String, Integer> userWeights // 사용자 가중치
) {}


