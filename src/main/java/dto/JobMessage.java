package dto;
public record JobMessage(
        Long evaluationId, // 평가 ID
        Long deviceId,     // 어떤 기기인지
        String messageType // (선택) CREATE, UPDATE 등
) {}

