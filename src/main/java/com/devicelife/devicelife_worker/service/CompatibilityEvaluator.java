package com.devicelife.devicelife_worker.service;

import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CompatibilityEvaluator {

    // 점수 계산 상수
    private static final double WEIGHT_HUB = 0.6;
    private static final double WEIGHT_QUALITY = 0.3;
    private static final double WEIGHT_ISOLATED = 0.1;

    public int calculate(EvaluationPayload payload) {
        // 1. 기기 리스트 추출 및 방어 로직
        if (payload.devices() == null || payload.devices().isEmpty()) {
            return 0;
        }

        // 2. 기기 분류 (DTO의 record 구조 활용)
        List<EvaluationPayload.DeviceDto> allDevices = payload.devices();

        List<EvaluationPayload.DeviceDto> phones = filterByType(allDevices, "SMARTPHONE");
        List<EvaluationPayload.DeviceDto> watches = filterByType(allDevices, "SMARTWATCH");
        List<EvaluationPayload.DeviceDto> laptops = filterByType(allDevices, "LAPTOP");
        List<EvaluationPayload.DeviceDto> tablets = filterByType(allDevices, "TABLET");
        List<EvaluationPayload.DeviceDto> keyboards = filterByType(allDevices, "KEYBOARD");
        List<EvaluationPayload.DeviceDto> mice = filterByType(allDevices, "MOUSE");
        List<EvaluationPayload.DeviceDto> audios = filterByType(allDevices, "AUDIO");

        List<EvaluationPayload.DeviceDto> hosts = new ArrayList<>();
        hosts.addAll(laptops);
        hosts.addAll(tablets);

        // 3. 변수 초기화
        double targetEdges = 0;
        double successEdges = 0;
        double totalQuality = 0;

        Set<Long> targetedDevices = new HashSet<>();
        Set<Long> connectedDevices = new HashSet<>();

        // (A) 스마트워치 ↔ 스마트폰
        if (!phones.isEmpty()) {
            for (EvaluationPayload.DeviceDto watch : watches) {
                Long wId = watch.deviceId();
                targetedDevices.add(wId);
                targetEdges++;

                double bestQ = 0.0;
                boolean connected = false;

                for (EvaluationPayload.DeviceDto phone : phones) {
                    // specs 맵에서 데이터 추출 (백엔드 필드명 일치 확인)
                    Object compatibleOSObj = watch.specs().get("compatiblePhoneOs");
                    List<String> compatibleOS = (compatibleOSObj instanceof List) ? (List<String>) compatibleOSObj : Collections.emptyList();
                    String phoneOS = (String) phone.specs().get("os");

                    if (phoneOS != null && compatibleOS.contains(phoneOS)) {
                        bestQ = 1.0;
                        connected = true;
                        connectedDevices.add(phone.deviceId());
                    }
                }

                if (connected) {
                    successEdges++;
                    totalQuality += bestQ;
                    connectedDevices.add(wId);
                }
            }
        }

        // (B) 키보드 ↔ 노트북 / 태블릿
        if (!hosts.isEmpty()) {
            for (EvaluationPayload.DeviceDto kb : keyboards) {
                Long kId = kb.deviceId();
                targetedDevices.add(kId);
                targetEdges++;
                successEdges++;
                connectedDevices.add(kId);

                double currentMax = 0.0;
                for (EvaluationPayload.DeviceDto host : hosts) {
                    String hostType = host.type();
                    String hostOS = (String) host.specs().get("os");

                    Object layoutObj = kb.specs().get("supportedLayouts");
                    List<String> supportedLayouts = (layoutObj instanceof List) ? (List<String>) layoutObj : Collections.emptyList();

                    boolean isLaptop = "LAPTOP".equalsIgnoreCase(hostType);
                    double score;

                    if (hostOS != null && supportedLayouts.contains(hostOS)) {
                        score = 1.0;
                    } else {
                        score = isLaptop ? 0.8 : 0.5;
                    }
                    currentMax = Math.max(currentMax, score);
                    connectedDevices.add(host.deviceId());
                }
                totalQuality += currentMax;
            }
        }

        // (C) 마우스 ↔ 노트북 / 태블릿
        if (!hosts.isEmpty()) {
            for (EvaluationPayload.DeviceDto mouse : mice) {
                Long mId = mouse.deviceId();
                targetedDevices.add(mId);
                targetEdges++;
                successEdges++;
                connectedDevices.add(mId);

                double currentMax = 0.0;
                for (EvaluationPayload.DeviceDto host : hosts) {
                    String hostOS = (String) host.specs().get("os");

                    Object gestureObj = mouse.specs().get("gestureSupport");
                    List<String> gestureSupports = (gestureObj instanceof List) ? (List<String>) gestureObj : Collections.emptyList();

                    double score = (hostOS != null && gestureSupports.contains(hostOS)) ? 1.0 : 0.5;
                    currentMax = Math.max(currentMax, score);
                    connectedDevices.add(host.deviceId());
                }
                totalQuality += currentMax;
            }
        }

        // (D) 오디오 ↔ 스마트폰
        if (!phones.isEmpty()) {
            for (EvaluationPayload.DeviceDto audio : audios) {
                Long aId = audio.deviceId();
                targetedDevices.add(aId);
                targetEdges++;
                successEdges++;
                connectedDevices.add(aId);

                double currentMax = 0.0;
                for (EvaluationPayload.DeviceDto phone : phones) {
                    String phoneOS = (String) phone.specs().get("os");

                    Object codecObj = audio.specs().get("supportedCodecs");
                    List<String> audioCodecs = (codecObj instanceof List) ? (List<String>) codecObj : Collections.emptyList();

                    boolean highQuality = false;
                    if ("iOS".equalsIgnoreCase(phoneOS)) {
                        if (audioCodecs.contains("AAC")) highQuality = true;
                    } else {
                        if (audioCodecs.contains("LDAC") || audioCodecs.contains("aptX")) highQuality = true;
                    }

                    double score = highQuality ? 1.0 : 0.6;
                    currentMax = Math.max(currentMax, score);
                    connectedDevices.add(phone.deviceId());
                }
                totalQuality += currentMax;
            }
        }

        // 4. 최종 점수 산출
        if (targetEdges == 0) return 65;
        if (successEdges == 0) return 0;

        double hubConnectivity = successEdges / targetEdges;
        double avgQuality = totalQuality / successEdges;

        long targetDevCount = targetedDevices.size();
        long isolatedCount = targetedDevices.stream()
                .filter(id -> !connectedDevices.contains(id))
                .count();
        double isolatedRatio = (targetDevCount == 0) ? 0.0 : (double) isolatedCount / targetDevCount;

        // 최종 점수 (Case E3)
        return (int) (100 * (WEIGHT_HUB * hubConnectivity + WEIGHT_QUALITY * avgQuality + WEIGHT_ISOLATED * (1.0 - isolatedRatio)));
    }

    private List<EvaluationPayload.DeviceDto> filterByType(List<EvaluationPayload.DeviceDto> devices, String type) {
        return devices.stream()
                .filter(d -> type.equalsIgnoreCase(d.type()))
                .collect(Collectors.toList());
    }
}