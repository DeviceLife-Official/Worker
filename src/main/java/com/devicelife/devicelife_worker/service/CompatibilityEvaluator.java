package com.devicelife.devicelife_worker.service;

import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class

CompatibilityEvaluator {

    // 점수 계산 상수
    private static final double WEIGHT_HUB = 0.6;
    private static final double WEIGHT_QUALITY = 0.3;
    private static final double WEIGHT_ISOLATED = 0.1;

    public int calculate(EvaluationPayload payload) {
        List<Map<String, Object>> specs = (List<Map<String, Object>>) payload.specs().get("devices");
        if (specs == null || specs.isEmpty()) return 0; // HF-1 (기기 없음)

        // 1. 기기 분류 (DTO 변환 없이 Map으로 바로 처리)
        List<Map<String, Object>> phones = filterByType(specs, "SMARTPHONE");
        List<Map<String, Object>> watches = filterByType(specs, "SMART_WATCH");
        List<Map<String, Object>> laptops = filterByType(specs, "LAPTOP");
        List<Map<String, Object>> tablets = filterByType(specs, "TABLET");
        List<Map<String, Object>> keyboards = filterByType(specs, "KEYBOARD");
        List<Map<String, Object>> mice = filterByType(specs, "MOUSE");
        List<Map<String, Object>> audios = filterByType(specs, "AUDIO");

        // 호스트 기기 그룹 (노트북 + 태블릿)
        List<Map<String, Object>> hosts = new ArrayList<>();
        hosts.addAll(laptops);
        hosts.addAll(tablets);

        // 2. 변수 초기화
        double targetEdges = 0;   // 연결 시도해야 하는 선의 개수 (분모)
        double successEdges = 0;  // 실제 연결된 선의 개수 (분자)
        double totalQuality = 0;  // 품질 점수 합계

        // 고립 비율 계산용 Set (ID 저장)
        Set<Long> targetedDevices = new HashSet<>();  // 연결 대상이 존재했던 기기들
        Set<Long> connectedDevices = new HashSet<>(); // 실제로 연결에 성공한 기기들

        // -------------------------------------------------------
        // (A) 스마트워치 ↔ 스마트폰
        // -------------------------------------------------------
        if (!phones.isEmpty()) {
            for (Map<String, Object> watch : watches) {
                Long wId = getId(watch);
                targetedDevices.add(wId); // 폰이 있으므로 워치는 타겟임
                targetEdges++; // 선 하나 그어야 함

                // 가장 잘 맞는 폰 하나랑 연결되면 성공으로 간주
                double bestQ = 0.0;
                boolean connected = false;

                for (Map<String, Object> phone : phones) {
                    List<String> compatibleOS = getList(watch, "compatiblePhoneOs");
                    String phoneOS = getString(phone, "os");

                    if (compatibleOS.contains(phoneOS)) {
                        bestQ = 1.0; // 호환되면 1점
                        connected = true;
                        connectedDevices.add(getId(phone)); // 폰도 연결됨
                    }
                }

                if (connected) {
                    successEdges++;
                    totalQuality += bestQ;
                    connectedDevices.add(wId); // 워치 연결됨
                }
            }
        }

        // -------------------------------------------------------
        // (B) 키보드 ↔ 노트북 / 태블릿
        // -------------------------------------------------------
        if (!hosts.isEmpty()) {
            for (Map<String, Object> kb : keyboards) {
                Long kId = getId(kb);
                targetedDevices.add(kId);
                targetEdges++;

                double bestQ = 0.0;
                boolean connected = false; // "호환 아님"도 연결은 된 것(0.8점 등)으로 칠지, 아예 끊긴걸로 칠지?
                // -> 기획서상: "호환 아님"은 점수(0.8/0.5)를 부여하므로 "연결 성공"으로 간주해야 함!
                // -> 단, 연결 자체가 불가능한 경우(ex: 포트 없음 등)는 고려 안 함. 여기선 무조건 연결은 된다고 가정.

                successEdges++; // 키보드는 일단 연결은 됨 (점수가 깎일 뿐)
                connectedDevices.add(kId);
                connected = true;

                // 최고 점수 찾기 (노트북 우선)
                double currentMax = 0.0;
                for (Map<String, Object> host : hosts) {
                    String hostType = getString(host, "type");
                    String hostOS = getString(host, "os");
                    List<String> supportedLayouts = getList(kb, "layoutSupports");

                    boolean isLaptop = "LAPTOP".equalsIgnoreCase(hostType);
                    double score;

                    if (supportedLayouts.contains(hostOS)) {
                        score = 1.0; // 호환 완벽
                    } else {
                        score = isLaptop ? 0.8 : 0.5; // 불호환 패널티
                    }
                    currentMax = Math.max(currentMax, score);
                    connectedDevices.add(getId(host));
                }
                totalQuality += currentMax;
            }
        }

        // -------------------------------------------------------
        // (C) 마우스 ↔ 노트북 / 태블릿
        // -------------------------------------------------------
        if (!hosts.isEmpty()) {
            for (Map<String, Object> mouse : mice) {
                Long mId = getId(mouse);
                targetedDevices.add(mId);
                targetEdges++;
                successEdges++; // 마우스도 일단 연결은 됨
                connectedDevices.add(mId);

                double currentMax = 0.0;
                for (Map<String, Object> host : hosts) {
                    String hostOS = getString(host, "os");
                    List<String> gestureSupports = getList(mouse, "gestureSupports");

                    double score = gestureSupports.contains(hostOS) ? 1.0 : 0.5;
                    currentMax = Math.max(currentMax, score);
                    connectedDevices.add(getId(host));
                }
                totalQuality += currentMax;
            }
        }

        // -------------------------------------------------------
        // (D) 오디오 ↔ 스마트폰
        // -------------------------------------------------------
        if (!phones.isEmpty()) {
            for (Map<String, Object> audio : audios) {
                Long aId = getId(audio);
                targetedDevices.add(aId);
                targetEdges++;
                successEdges++; // 오디오 연결 성공
                connectedDevices.add(aId);

                double currentMax = 0.0;
                for (Map<String, Object> phone : phones) {
                    String phoneOS = getString(phone, "os"); // iOS or Android
                    List<String> audioCodecs = getList(audio, "codecs");

                    boolean highQuality = false;
                    if ("iOS".equalsIgnoreCase(phoneOS)) {
                        if (audioCodecs.contains("AAC")) highQuality = true;
                    } else {
                        if (audioCodecs.contains("LDAC") || audioCodecs.contains("aptX")) highQuality = true;
                    }

                    double score = highQuality ? 1.0 : 0.6;
                    currentMax = Math.max(currentMax, score);
                    connectedDevices.add(getId(phone));
                }
                totalQuality += currentMax;
            }
        }

        // =======================================================
        // 4. Case 판별 및 점수 계산
        // =======================================================

        // Case E0 : 평가할 관계 자체가 없음
        if (targetEdges == 0) {
            return 65; // 기본 점수 (중간)
        }

        // Case E1 : 관계는 있는데 성공 엣지가 0개 (최악)
        if (successEdges == 0) {
            return 0;
        }

        // 지표 계산
        double hubConnectivity = successEdges / targetEdges;
        double avgQuality = totalQuality / successEdges;

        // 고립 비율 (Isolated Ratio)
        // 정의: (타겟이 되었으나 연결되지 않은 기기 수) / (타겟이 된 전체 기기 수)
        double isolatedRatio = 0.0;
        long targetDevCount = targetedDevices.size();

        // 타겟이 된 기기가 없으면(사실 targetEdges=0에서 걸러지지만 안전장치) E2 로직
        boolean isE2 = (targetDevCount == 0);

        if (!isE2) {
            long isolatedCount = 0;
            for (Long id : targetedDevices) {
                if (!connectedDevices.contains(id)) {
                    isolatedCount++;
                }
            }
            isolatedRatio = (double) isolatedCount / targetDevCount;
        }

        // Case E2 vs E3 분기
        if (isE2) {
            // 고립 계산 제외 (재정규화: 0.6 + 0.3 = 0.9)
            return (int) (100 * (0.6 * hubConnectivity + 0.3 * avgQuality) / 0.9);
        } else {
            // Case E3 (Normal)
            return (int) (100 * (0.6 * hubConnectivity + 0.3 * avgQuality + 0.1 * (1.0 - isolatedRatio)));
        }
    }

    // --- Helper Methods ---
    private List<Map<String, Object>> filterByType(List<Map<String, Object>> specs, String type) {
        return specs.stream()
                .filter(d -> type.equalsIgnoreCase((String) d.get("type")))
                .collect(Collectors.toList());
    }

    private Long getId(Map<String, Object> device) {
        return ((Number) device.get("id")).longValue();
    }

    private String getString(Map<String, Object> device, String key) {
        return (String) device.getOrDefault(key, "");
    }

    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> device, String key) {
        Object val = device.get(key);
        if (val instanceof List) {
            return (List<String>) val;
        }
        return Collections.emptyList();
    }
}