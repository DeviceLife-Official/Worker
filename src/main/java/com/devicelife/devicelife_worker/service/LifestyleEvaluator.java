package com.devicelife.devicelife_worker.service;

import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LifestyleEvaluator {

    private static final int BASE_SCORE = 65;

    public int calculate(EvaluationPayload payload) {
        List<String> lifestyles = payload.lifestyles();
        if (lifestyles == null || lifestyles.isEmpty()) {
            return BASE_SCORE;
        }

        // 태그 중복 방지
        List<String> normalizedTags = lifestyles.stream()
                .map(this::normalizeTag)
                .distinct()
                .toList();

        int sum = 0;

        for (String tag : normalizedTags) {
            int score = BASE_SCORE;

            switch (tag) {
                case "OFFICE" -> score += evaluateOffice(payload);
                case "STUDY" -> score += evaluateStudy(payload);
                case "DEVELOPER" -> score += evaluateDeveloper(payload);
                case "VIDEO_EDITING" -> score += evaluateVideoEditing(payload);
                case "GAME" -> score += evaluateGame(payload);
                case "TOUR" -> score += evaluateTour(payload);
                default -> {
                    // 알 수 없는 태그 → 영향 없음
                }
            }

            sum += clamp(score);
        }

        return clamp(sum / normalizedTags.size());
    }

    /* ===================== 태그 정규화 ===================== */

    private String normalizeTag(String tagLabel) {
        // "# Office/portability" → "OFFICE"
        String cleaned = tagLabel
                .replace("#", "")
                .trim();

        if (cleaned.contains("/")) {
            cleaned = cleaned.split("/")[0];
        }

        return cleaned
                .toUpperCase()
                .replace("-", "_")
                .replace(" ", "_");
    }

    /* ===================== OFFICE ===================== */

    private int evaluateOffice(EvaluationPayload payload) {
        int score = 0;

        var keyboard = findDevice(payload, "KEYBOARD");
        var laptop = findDevice(payload, "LAPTOP");
        var mouse = findDevice(payload, "MOUSE");

        // 1. 키보드 배열
        if (keyboard != null) {
            String size = getStringSpec(keyboard, "size");
            if ("FULL".equalsIgnoreCase(size)) score += 12;
            else if ("TKL".equalsIgnoreCase(size)) score += 6;
            else if ("MINI".equalsIgnoreCase(size)) score -= 10;
        }

        // 2. 노트북 HDMI
        if (laptop != null) {
            List<String> ports = getListSpec(laptop, "displayPorts");
            if (ports != null && ports.contains("HDMI")) score += 8;
            else score -= 8;
        }

        // 3. 마우스 인체공학
        if (mouse != null) {
            String shape = getStringSpec(mouse, "shape");
            if ("VERTICAL".equalsIgnoreCase(shape)) score += 6;
        }

        return score;
    }

    /* ===================== STUDY ===================== */

    private int evaluateStudy(EvaluationPayload payload) {
        int score = 0;

        var tablet = findDevice(payload, "TABLET");
        var laptop = findDevice(payload, "LAPTOP");
        var keyboard = findDevice(payload, "KEYBOARD");
        var mouse = findDevice(payload, "MOUSE");

        // 태블릿 펜
        if (tablet != null) {
            Boolean stylus = getBooleanSpec(tablet, "stylus");
            if (Boolean.TRUE.equals(stylus)) score += 12;
            else score -= 15;
        }

        // 키보드 소음
        if (keyboard != null) {
            String sw = getStringSpec(keyboard, "switch");
            if ("BLUE".equalsIgnoreCase(sw)) score -= 20;
            else score += 6;
        }

        // 마우스 소음
        if (mouse != null) {
            String sw = getStringSpec(mouse, "switch");
            if ("BLUE".equalsIgnoreCase(sw)) score -= 10;
            else score += 3;
        }

        // 휴대 무게
        if (laptop != null && tablet != null) {
            Double lw = getDoubleSpec(laptop, "weight");
            Double tw = getDoubleSpec(tablet, "weight");
            if (lw != null && tw != null) {
                if (lw + tw <= 2.0) score += 8;
                else score -= 8;
            }
        }

        return score;
    }

    /* ===================== DEVELOPER ===================== */

    private int evaluateDeveloper(EvaluationPayload payload) {
        int score = 0;

        var laptop = findDevice(payload, "LAPTOP");
        var mouse = findDevice(payload, "MOUSE");

        if (laptop != null) {
            String os = getStringSpec(laptop, "os");
            if ("MACOS".equalsIgnoreCase(os) || "LINUX".equalsIgnoreCase(os)) score += 10;
            else if ("WINDOWS".equalsIgnoreCase(os)) score += 8;
            else if ("CHROMEOS".equalsIgnoreCase(os)) score += 5;

            List<String> ports = getListSpec(laptop, "ports");
            if (ports != null && !ports.isEmpty()) score += 6;
            else score -= 6;
        }

        if (mouse != null) {
            String shape = getStringSpec(mouse, "shape");
            if ("VERTICAL".equalsIgnoreCase(shape)) score += 6;
        }

        return score;
    }

    /* ===================== VIDEO EDITING ===================== */

    private int evaluateVideoEditing(EvaluationPayload payload) {
        int score = 0;

        var laptop = findDevice(payload, "LAPTOP");
        var mouse = findDevice(payload, "MOUSE");

        if (laptop != null) {
            Integer ram = getIntSpec(laptop, "ram");
            if (ram != null) score += (ram >= 16 ? 12 : -12);

            Integer storage = getIntSpec(laptop, "storage");
            if (storage != null) score += (storage >= 512 ? 10 : -10);

            String gamut = getStringSpec(laptop, "colorGamut");
            if ("P3".equalsIgnoreCase(gamut) || "SRGB_100".equalsIgnoreCase(gamut)) score += 8;
        }

        if (mouse != null) {
            String shape = getStringSpec(mouse, "shape");
            if ("VERTICAL".equalsIgnoreCase(shape)) score += 6;
        }

        return score;
    }

    /* ===================== GAME ===================== */

    private int evaluateGame(EvaluationPayload payload) {
        int score = 0;

        var laptop = findDevice(payload, "LAPTOP");
        var keyboard = findDevice(payload, "KEYBOARD");
        var mouse = findDevice(payload, "MOUSE");

        if (laptop != null) {
            String gpu = getStringSpec(laptop, "gpu");
            if ("DEDICATED".equalsIgnoreCase(gpu)) score += 15;
            else score -= 25;
        }

        boolean lowLatency = false;

        if (keyboard != null) {
            String conn = getStringSpec(keyboard, "connection");
            lowLatency |= "WIRED".equalsIgnoreCase(conn) || "DONGLE".equalsIgnoreCase(conn);
        }

        if (mouse != null) {
            String conn = getStringSpec(mouse, "connection");
            lowLatency |= "WIRED".equalsIgnoreCase(conn) || "DONGLE".equalsIgnoreCase(conn);
        }

        score += lowLatency ? 10 : -12;
        return score;
    }

    /* ===================== TOUR ===================== */

    private int evaluateTour(EvaluationPayload payload) {
        int score = 0;

        var laptop = findDevice(payload, "LAPTOP");
        var tablet = findDevice(payload, "TABLET");
        var charger = findDevice(payload, "CHARGER");
        var keyboard = findDevice(payload, "KEYBOARD");
        var mouse = findDevice(payload, "MOUSE");

        if (laptop != null && tablet != null) {
            Double lw = getDoubleSpec(laptop, "weight");
            Double tw = getDoubleSpec(tablet, "weight");
            if (lw != null && tw != null) {
                if (lw + tw <= 3.0) score += 10;
                else score -= 10;
            }
        }

        if (charger != null && laptop != null) {
            Boolean canCharge = getBooleanSpec(charger, "canChargeLaptop");
            if (Boolean.TRUE.equals(canCharge)) score += 5;
            else score -= 12;
        }

        if (keyboard != null || mouse != null) {
            String conn = keyboard != null
                    ? getStringSpec(keyboard, "connection")
                    : getStringSpec(mouse, "connection");

            if ("BLUETOOTH".equalsIgnoreCase(conn)) score += 6;
            else score -= 6;
        }

        return score;
    }

    /* ===================== 공통 유틸 ===================== */

    private EvaluationPayload.DeviceDto findDevice(EvaluationPayload payload, String type) {
        return payload.devices().stream()
                .filter(d -> type.equalsIgnoreCase(d.type()))
                .findFirst()
                .orElse(null);
    }

    private String getStringSpec(EvaluationPayload.DeviceDto d, String key) {
        Object v = d.specs().get(key);
        return v instanceof String ? (String) v : null;
    }

    private Integer getIntSpec(EvaluationPayload.DeviceDto d, String key) {
        Object v = d.specs().get(key);
        return v instanceof Number ? ((Number) v).intValue() : null;
    }

    private Double getDoubleSpec(EvaluationPayload.DeviceDto d, String key) {
        Object v = d.specs().get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : null;
    }

    private Boolean getBooleanSpec(EvaluationPayload.DeviceDto d, String key) {
        Object v = d.specs().get(key);
        return v instanceof Boolean ? (Boolean) v : null;
    }

    private List<String> getListSpec(EvaluationPayload.DeviceDto d, String key) {
        Object v = d.specs().get(key);
        return v instanceof List<?> list
                ? list.stream().map(Object::toString).toList()
                : null;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }
}
