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
            int base = BASE_SCORE;
            int delta = 0;

            switch (tag) {
                case "OFFICE" -> delta = evaluateOffice(payload);
                case "STUDY" -> delta = evaluateStudy(payload);
                case "DEVELOPER" -> delta = evaluateDeveloper(payload);
                case "VIDEO_EDITING" -> delta = evaluateVideoEditing(payload);
                case "GAME" -> delta = evaluateGame(payload);
                case "TOUR" -> delta = evaluateTour(payload);
                default -> {
                    // 알 수 없는 태그 → 영향 없음
                    delta = 0;
                }
            }

            int beforeClamp = base + delta;
            int afterClamp = clamp(beforeClamp);

            sum += afterClamp;
        }

        int avgBeforeClamp = sum / normalizedTags.size();
        int finalScore = clamp(avgBeforeClamp);

        return finalScore;
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
            String size = getStringSpec(keyboard, "keyboardSize");
            if ("FULL".equalsIgnoreCase(size)) score += 12;
            else if ("TKL".equalsIgnoreCase(size)) score += 6;
            else if ("MINI_60".equalsIgnoreCase(size)) score -= 10;
        }

        // 2. 노트북 HDMI
        if (laptop != null) {
            Boolean hasHdmi = getBooleanSpec(laptop, "hasHdmi");

            // null이면 데이터 부족이라 판단 보류(0점)로 두는 게 안전
            if (hasHdmi != null) {
                if (hasHdmi) {
                    score += 8;
                } else {
                    score -= 8;
                }
            }
        }

        // 3. 마우스 인체공학
        if (mouse != null) {
            String shape = getStringSpec(mouse, "mouseType");
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
            String stylusType = getStringSpec(tablet, "stylusType");

            if (stylusType != null) {
                if (!"NONE".equalsIgnoreCase(stylusType)) {
                    // NONE이 아니면 → 펜 지원
                    score += 12;
                } else {
                    // 명시적으로 NONE
                    score -= 15;
                }
            }
        }

        // 키보드 소음
        if (keyboard != null) {
            String sw = getStringSpec(keyboard, "switchType");
            if ("BLUE".equalsIgnoreCase(sw)) score -= 20;
            else score += 6;
        }

        // 마우스 소음
        if (mouse != null) {
            Boolean hasClick = getBooleanSpec(mouse, "hasClientClick");

            if (hasClick != null) {
                if (hasClick) {
                    // 클릭음 있음 → 도서관/공부 환경에 불리
                    score -= 10;
                } else {
                    // 무소음/저소음
                    score += 3;
                }
            }
        }

        // 휴대 무게
        if (laptop != null && tablet != null) {
            Double laptopKg = getDoubleSpec(laptop, "weightKg");
            Double tabletGram = getDoubleSpec(tablet, "weightGram");

            if (laptopKg != null && tabletGram != null) {
                double tabletKg = tabletGram / 1000.0;
                double totalKg = laptopKg + tabletKg;

                if (totalKg <= 2.0) {
                    score += 8;
                } else {
                    score -= 8;
                }
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

            Boolean hasHdmi = getBooleanSpec(laptop, "hasHdmi");
            Boolean hasUsbA = getBooleanSpec(laptop, "hasUsbA");
            Boolean hasThunderbolt = getBooleanSpec(laptop, "hasThunderbolt");

            // 포트 정보가 하나도 없으면 → 판단 보류
            if (hasHdmi == null && hasUsbA == null && hasThunderbolt == null) {
                // score += 0;
            } else {
                boolean hasAnyPort =
                        Boolean.TRUE.equals(hasHdmi)
                                || Boolean.TRUE.equals(hasUsbA)
                                || Boolean.TRUE.equals(hasThunderbolt);

                if (hasAnyPort) {
                    score += 6;
                } else {
                    score -= 6;
                }
            }
        }

        if (mouse != null) {
            String shape = getStringSpec(mouse, "mouseType");
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
            Integer ram = getIntSpec(laptop, "ramGb");
            if (ram != null) score += (ram >= 16 ? 12 : -12);

            Integer storage = getIntSpec(laptop, "storageGb");
            if (storage != null) score += (storage >= 512 ? 10 : -10);

            //String gamut = getStringSpec(laptop, "colorGamut");
            //if ("P3".equalsIgnoreCase(gamut) || "SRGB_100".equalsIgnoreCase(gamut)) score += 8;
        }

        if (mouse != null) {
            String shape = getStringSpec(mouse, "mouseType");
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
            String gpuRaw = getStringSpec(laptop, "gpu");

            // gpu 값이 아예 없으면 "판단 보류"로 0점 처리 추천
            if (gpuRaw != null && !gpuRaw.isBlank()) {
                if (isDedicatedGpu(gpuRaw)) score += 15;
                else score -= 25;
            }
        }

        boolean lowLatency = false;

        if (keyboard != null) {
            String conn = getStringSpec(keyboard, "connectionType");
            lowLatency |= "WIRED_USB".equalsIgnoreCase(conn) || "BLUETOOTH_AND_DONGLE".equalsIgnoreCase(conn);
        }

        if (mouse != null) {
            String conn = getStringSpec(mouse, "connectionType");
            lowLatency |= "WIRED_USB".equalsIgnoreCase(conn) || "BLUETOOTH_AND_DONGLE".equalsIgnoreCase(conn);
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
            Double laptopKg = getDoubleSpec(laptop, "weightKg");
            Double tabletGram = getDoubleSpec(tablet, "weightGram");

            if (laptopKg != null && tabletGram != null) {
                double tabletKg = tabletGram / 1000.0;
                double totalKg = laptopKg + tabletKg;

                if (totalKg <= 3.0) {
                    score += 10;
                } else {
                    score -= 10;
                }
            }
            // 하나라도 null이면 판단 보류 (0점)
        }

        if (charger != null && laptop != null) {
            String chargingMethod = getStringSpec(laptop, "chargingMethod");
            Integer minRequired = getIntSpec(laptop, "minRequiredPowerW");
            Integer maxSingle = getIntSpec(charger, "maxSinglePortPowerW");

            // 1) DC 어댑터 노트북이면 충전기 커버 불가
            if ("DC_ADAPTER".equalsIgnoreCase(chargingMethod)) {
                score -= 12;
            }
            // 2) USB-C 노트북이면 W 비교로 판단
            else if ("USB_C".equalsIgnoreCase(chargingMethod)) {
                if (minRequired != null && maxSingle != null) {
                    if (maxSingle >= minRequired) score += 5;
                    else score -= 12;
                }
                // 스펙 없으면 판단 보류 (0점)
            }
        }

        if (keyboard != null || mouse != null) {
            String conn = keyboard != null
                    ? getStringSpec(keyboard, "connectionType")
                    : getStringSpec(mouse, "connectionType");

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

    private boolean isDedicatedGpu(String gpuRaw) {
        if (gpuRaw == null || gpuRaw.isBlank()) return false; // 판단보류/기본값: 외장 아님

        String g = gpuRaw.trim().toLowerCase();

        // 1) 명확한 내장 키워드
        if (g.contains("integrated") || g.contains("iris") || g.contains("uhd") || g.contains("hd graphics")) {
            return false;
        }

        // 2) 명확한 외장 키워드
        if (g.contains("rtx") || g.contains("gtx") || g.contains("geforce") || g.contains("quadro") || g.contains("nvidia")) {
            return true;
        }
        if (g.contains("arc")) { // intel arc
            return true;
        }

        // 3) AMD Radeon은 케이스 분기
        // - "Radeon RX ..." or "RX 6600M" 같은 건 외장 가능성이 높음
        if (g.contains("radeon")) {
            if (g.contains(" rx ") || g.startsWith("rx") || g.contains("xt") || g.endsWith("m")) {
                return true;
            }
            // "Radeon Amd" 같은 뭉뚱그림 -> 내장 쪽으로 기울이거나 판단보류
            return false;
        }

        // 4) 여기까지 왔으면 애매한 값 -> 외장 아님(보수적으로)
        return false;
    }
}
