package dto;
public record EvaluationResult(
        Long evaluationId,
        int totalScore,
        int compatibilityScore,
        int convenienceScore,
        int lifestyleScore,
        int colorScore
) {}

