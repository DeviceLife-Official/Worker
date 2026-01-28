package com.devicelife.devicelife_worker.service;

import com.devicelife.devicelife_worker.dto.EvaluationPayload;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ConvenienceEvaluator {

    private static final int BASE_SCORE = 65;

    // 가중치(권장)
    private static final double W_SIM = 0.20;    // 동시충전율
    private static final double W_LAPTOP = 0.25; // 노트북 충전 가능
    private static final double W_USB_C = 0.20;   // USB-C 통일감
    private static final double W_QI = 0.10;     // 스마트폰 무선충전
    private static final double W_BAT = 0.25;    // 배터리 수명

    /**
     * 편의성 점수 (0~100)
     * - 가능한 지표만 가중합 후 가중치 재정규화
     * - 아무 지표도 계산 불가면 BASE_SCORE(65)
     */
    public int calculate(EvaluationPayload payload) {

        if (payload == null || payload.devices() == null || payload.devices().isEmpty()) {
            return BASE_SCORE;
        }

        // type -> DeviceDto
        Map<String, EvaluationPayload.DeviceDto> byType = payload.devices().stream()
                .filter(Objects::nonNull)
                .filter(d -> d.type() != null)
                .collect(Collectors.toMap(
                        d -> normalizeType(d.type()),
                        d -> d,
                        (a, b) -> a
                ));

        EvaluationPayload.DeviceDto smartphone = byType.get("SMARTPHONE");
        EvaluationPayload.DeviceDto laptop = byType.get("LAPTOP");
        EvaluationPayload.DeviceDto tablet = byType.get("TABLET");
        EvaluationPayload.DeviceDto watch = byType.get("WATCH");
        EvaluationPayload.DeviceDto audio = byType.get("AUDIO");
        EvaluationPayload.DeviceDto keyboard = byType.get("KEYBOARD");
        EvaluationPayload.DeviceDto mouse = byType.get("MOUSE");
        EvaluationPayload.DeviceDto charger = byType.get("CHARGER");

        Score sSim = scoreSimultaneous(charger, smartphone, laptop, tablet, watch, audio, keyboard, mouse);
        Score sLaptop = scoreLaptopChargeable(laptop, charger);
        Score sUsbC = scoreUsbCUniformity(charger, smartphone, laptop, tablet, audio, keyboard, mouse);
        Score sQi = scoreSmartphoneWireless(smartphone);
        Score sBat = scoreBatteryLife(smartphone, laptop, tablet);

        double weightedSum = 0.0;
        double weightSum = 0.0;

        if (sSim.available) { weightedSum += W_SIM * sSim.value; weightSum += W_SIM; }
        if (sLaptop.available) { weightedSum += W_LAPTOP * sLaptop.value; weightSum += W_LAPTOP; }
        if (sUsbC.available) { weightedSum += W_USB_C * sUsbC.value; weightSum += W_USB_C; }
        if (sQi.available) { weightedSum += W_QI * sQi.value; weightSum += W_QI; }
        if (sBat.available) { weightedSum += W_BAT * sBat.value; weightSum += W_BAT; }

        if (weightSum <= 0.0) return BASE_SCORE;

        double raw = weightedSum / weightSum; // 0~1
        int score = (int) Math.round(100.0 * clamp01(raw));
        return clampInt(score, 0, 100);
    }

    // =========================================================
    // (A) 동시충전율 s_sim
    // =========================================================
    private Score scoreSimultaneous(
            EvaluationPayload.DeviceDto charger,
            EvaluationPayload.DeviceDto smartphone,
            EvaluationPayload.DeviceDto laptop,
            EvaluationPayload.DeviceDto tablet,
            EvaluationPayload.DeviceDto watch,
            EvaluationPayload.DeviceDto audio,
            EvaluationPayload.DeviceDto keyboard,
            EvaluationPayload.DeviceDto mouse
    ) {
        if (charger == null) return Score.na();

        int need = countChargeTargets(smartphone, laptop, tablet, watch, audio, keyboard, mouse);
        if (need <= 0) return Score.na();

        int slots = 0;

        // ports = chargers.portConfiguration 길이
        // 예) ["C","C","A"] → 3
        List<?> portConfig = getList(charger.specs(), "portConfiguration");
        if (portConfig != null) slots += portConfig.size();

        // chargerType == WIRELESS_STAND → slots += 1
        String chargerType = getString(charger.specs(), "chargerType");
        if ("WIRELESS_STAND".equalsIgnoreCase(chargerType)) {
            slots += 1;
        }

        double s = Math.min(1.0, (double) slots / (double) need);
        return Score.of(clamp01(s));
    }

    private int countChargeTargets(
            EvaluationPayload.DeviceDto smartphone,
            EvaluationPayload.DeviceDto laptop,
            EvaluationPayload.DeviceDto tablet,
            EvaluationPayload.DeviceDto watch,
            EvaluationPayload.DeviceDto audio,
            EvaluationPayload.DeviceDto keyboard,
            EvaluationPayload.DeviceDto mouse
    ) {
        int cnt = 0;

        // smartphone(항상)
        if (smartphone != null) cnt++;

        // laptop/tablet/watch/audio (존재하면)
        if (laptop != null) cnt++;
        if (tablet != null) cnt++;
        if (watch != null) cnt++;
        if (audio != null) cnt++;

        // keyboard — batteryMah != null이면 충전 대상
        if (keyboard != null) {
            Number batteryMah = getNumber(keyboard.specs(), "batteryMah");
            if (batteryMah != null) cnt++;
        }

        // mouse — powerSource == USB_C_RECHARGEABLE이면 충전 대상
        if (mouse != null) {
            String powerSource = getString(mouse.specs(), "powerSource");
            if ("USB_C_RECHARGEABLE".equalsIgnoreCase(powerSource)) cnt++;
        }

        return cnt;
    }

    // =========================================================
    // (B) 노트북 충전 가능 s_laptop
    // =========================================================
    private Score scoreLaptopChargeable(EvaluationPayload.DeviceDto laptop, EvaluationPayload.DeviceDto charger) {
        if (laptop == null || charger == null) return Score.na();

        String chargingMethod = getString(laptop.specs(), "chargingMethod"); // DC_ADAPTER / USB_C
        if (chargingMethod == null) return Score.na();

        // DC 어댑터면 0
        if ("DC_ADAPTER".equalsIgnoreCase(chargingMethod)) {
            return Score.of(0.0);
        }

        // USB-C 충전
        if (!"USB_C".equalsIgnoreCase(chargingMethod)) {
            return Score.of(0.0);
        }

        Number minRequiredPowerW = getNumber(laptop.specs(), "minRequiredPowerW");
        Number maxSinglePortPowerW = getNumber(charger.specs(), "maxSinglePortPowerW");
        if (minRequiredPowerW == null || maxSinglePortPowerW == null) return Score.na();

        double minReq = minRequiredPowerW.doubleValue();
        double maxSingle = maxSinglePortPowerW.doubleValue();
        if (minReq <= 0 || maxSingle <= 0) return Score.na();

        double ratio = maxSingle / minReq;

        double s;
        if (ratio >= 1.0) s = 1.0;
        else if (ratio >= 0.8) s = 0.5;
        else s = 0.0;

        // (선택옵션) supportedProtocols에 PD 없으면 0.5로 제한
        if (s > 0.0) {
            List<?> protocols = getList(charger.specs(), "supportedProtocols"); // ["PD","PPS"...]
            boolean hasPd = false;
            if (protocols != null) {
                for (Object p : protocols) {
                    if (p != null && "PD".equalsIgnoreCase(String.valueOf(p))) {
                        hasPd = true;
                        break;
                    }
                }
            }
            if (!hasPd) s = Math.min(s, 0.5);
        }

        return Score.of(clamp01(s));
    }

    // =========================================================
    // (C) USB-C 단자 통일감 s_usbc
    // =========================================================
    private Score scoreUsbCUniformity(
            EvaluationPayload.DeviceDto charger,
            EvaluationPayload.DeviceDto smartphone,
            EvaluationPayload.DeviceDto laptop,
            EvaluationPayload.DeviceDto tablet,
            EvaluationPayload.DeviceDto audio,
            EvaluationPayload.DeviceDto keyboard,
            EvaluationPayload.DeviceDto mouse
    ) {
        // 케이블로 충전하는 기기가 1개 이상일 때만 계산
        List<Boolean> cableIsUsbC = new ArrayList<>();

        // smartphone: chargingPort
        if (smartphone != null) {
            String port = getString(smartphone.specs(), "chargingPort"); // USB_C/LIGHTNING/...
            if (port != null) cableIsUsbC.add("USB_C".equalsIgnoreCase(port));
        }

        // tablet: chargingPort
        if (tablet != null) {
            String port = getString(tablet.specs(), "chargingPort");
            if (port != null) cableIsUsbC.add("USB_C".equalsIgnoreCase(port));
        }

        // laptop: chargingMethod == USB_C
        if (laptop != null) {
            String method = getString(laptop.specs(), "chargingMethod");
            if ("USB_C".equalsIgnoreCase(method)) {
                cableIsUsbC.add(true);
            }
            // DC_ADAPTER는 "케이블(USB-C) 통일감" 산정에서 제외(네 설명 기준)
        }

        // audio: caseChargingType (USB_C / LIGHTNING / WIRELESS)
        if (audio != null) {
            String t = getString(audio.specs(), "caseChargingType");
            if (t != null && !"WIRELESS".equalsIgnoreCase(t)) {
                cableIsUsbC.add("USB_C".equalsIgnoreCase(t));
            }
        }

        // keyboard: batteryMah != null이면 충전 대상이긴 한데 "포트 타입" 정보가 없으면 USB-C 판단 불가
        // -> keyboard.specs에 chargingPort 같은게 있다면 아래 주석 풀어써라
        // if (keyboard != null) {
        //     String port = getString(keyboard.specs(), "chargingPort");
        //     if (port != null) cableIsUsbC.add("USB_C".equalsIgnoreCase(port));
        // }

        // mouse: powerSource == USB_C_RECHARGEABLE면 USB-C로 봄
        if (mouse != null) {
            String ps = getString(mouse.specs(), "powerSource");
            if ("USB_C_RECHARGEABLE".equalsIgnoreCase(ps)) {
                cableIsUsbC.add(true);
            }
        }

        if (cableIsUsbC.isEmpty()) return Score.na();

        long usbCCount = cableIsUsbC.stream().filter(Boolean::booleanValue).count();
        double deviceUsbCRatio = (double) usbCCount / (double) cableIsUsbC.size();

        // 충전기 있지만 C타입 지원 안함 -> 0.7 패널티
        boolean chargerHasC = false;
        if (charger != null) {
            List<?> portConfig = getList(charger.specs(), "portConfiguration");
            if (portConfig != null) {
                for (Object p : portConfig) {
                    if (p != null && "C".equalsIgnoreCase(String.valueOf(p))) {
                        chargerHasC = true;
                        break;
                    }
                }
            }
        }

        double s = deviceUsbCRatio;
        if (charger != null && !chargerHasC) {
            s = deviceUsbCRatio * 0.7;
        }

        return Score.of(clamp01(s));
    }

    // =========================================================
    // (D) 스마트폰 무선충전 s_qi
    // =========================================================
    private Score scoreSmartphoneWireless(EvaluationPayload.DeviceDto smartphone) {
        if (smartphone == null) return Score.na();

        String wc = getString(smartphone.specs(), "wirelessCharging"); // MAGSAFE / QI / NONE
        if (wc == null) return Score.na();

        if ("MAGSAFE".equalsIgnoreCase(wc)) return Score.of(1.0);
        if ("QI".equalsIgnoreCase(wc)) return Score.of(0.8);
        if ("NONE".equalsIgnoreCase(wc)) return Score.of(0.0);

        return Score.na();
    }

    // =========================================================
    // (E) 배터리 수명 s_bat
    // =========================================================
    private Score scoreBatteryLife(
            EvaluationPayload.DeviceDto smartphone,
            EvaluationPayload.DeviceDto laptop,
            EvaluationPayload.DeviceDto tablet
    ) {
        // 조건: 스마트폰/노트북/태블릿 중 하나라도 존재
        if (smartphone == null && laptop == null && tablet == null) return Score.na();

        List<Double> parts = new ArrayList<>();

        // smartphone batteryMah: 3000~5500
        if (smartphone != null) {
            Number mah = getNumber(smartphone.specs(), "batteryMah");
            if (mah != null) parts.add(norm(mah.doubleValue(), 3000, 5500));
        }

        // tablet batteryMah: 6000~11000
        if (tablet != null) {
            Number mah = getNumber(tablet.specs(), "batteryMah");
            if (mah != null) parts.add(norm(mah.doubleValue(), 6000, 11000));
        }

        // laptop batteryWh: 40~100
        if (laptop != null) {
            Number wh = getNumber(laptop.specs(), "batteryWh");
            if (wh != null) parts.add(norm(wh.doubleValue(), 40, 100));
        }

        if (parts.isEmpty()) return Score.na();

        double avg = parts.stream().mapToDouble(d -> d).average().orElse(0.0);
        return Score.of(clamp01(avg));
    }

    // =========================================================
    // Map<String,Object> 안전 파서들
    // =========================================================
    private String normalizeType(String type) {
        return type.trim().toUpperCase(Locale.ROOT);
    }

    private String getString(Map<String, Object> specs, String key) {
        if (specs == null) return null;
        Object v = specs.get(key);
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private Number getNumber(Map<String, Object> specs, String key) {
        if (specs == null) return null;
        Object v = specs.get(key);
        if (v == null) return null;

        if (v instanceof Number n) return n;

        // Jackson이 문자열로 들어오는 경우 방어
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) return null;
            try {
                if (t.contains(".")) return Double.parseDouble(t);
                return Long.parseLong(t);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<?> getList(Map<String, Object> specs, String key) {
        if (specs == null) return null;
        Object v = specs.get(key);
        if (v == null) return null;

        if (v instanceof List<?> list) return list;

        // 배열로 들어온 경우도 방어
        if (v.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(v);
            List<Object> out = new ArrayList<>(len);
            for (int i = 0; i < len; i++) out.add(java.lang.reflect.Array.get(v, i));
            return out;
        }
        return null;
    }

    // =========================================================
    // 유틸
    // =========================================================
    private static double norm(double x, double min, double max) {
        if (max <= min) return 0.0;
        return clamp01((x - min) / (max - min));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static class Score {
        final boolean available;
        final double value;

        private Score(boolean available, double value) {
            this.available = available;
            this.value = value;
        }

        static Score of(double v) { return new Score(true, v); }
        static Score na() { return new Score(false, 0.0); }
    }
}
