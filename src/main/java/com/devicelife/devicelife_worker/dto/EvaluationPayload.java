package com.devicelife.devicelife_worker.dto;

import java.util.Map;

import java.util.List;
import java.util.Map;

public record EvaluationPayload(
        Long combinationId,      // 백엔드의 필드명과 일치시켜야 함
        Long evaluationVersion,
        String jobId,
        List<DeviceDto> devices,
        List<String> lifestyles
) {
    public record DeviceDto(
            Long deviceId,
            String type,         // Enum 대신 일단 String으로 받으면 편함
            Map<String, Object> specs
    ) {}
}

