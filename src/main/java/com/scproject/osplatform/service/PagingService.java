package com.scproject.osplatform.service;

import com.scproject.osplatform.dto.sim.PagingReq;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PagingService {

    /**
     * 시뮬레이터에서 넘겨준 algo / frames / refs 를 받아서
     * 알고리즘별로 분기하고, 프론트가 기대하는 형태의 Map 을 돌려줍니다.
     *
     * 반환 형태:
     * {
     *   "steps": [ { "ref": 7, "frame": [7,-1,-1], "fault": true }, ... ],
     *   "faults": 10
     * }
     */
    public Map<String, Object> run(PagingReq req) {
        String algo = (req.algo == null ? "FIFO" : req.algo.toUpperCase(Locale.ROOT));
        int frames = req.frames;
        List<Integer> refs = req.refs != null ? req.refs : Collections.emptyList();

        switch (algo) {
            case "LRU":
                return simulateLRU(frames, refs);
            case "OPT":
                return simulateOPT(frames, refs);
            // 아직 LFU / NUR 는 구현 안 했으므로 일단 FIFO 로 처리
            case "LFU":
            case "NUR":
            case "FIFO":
            default:
                return simulateFIFO(frames, refs);
        }
    }

    /**
     * FIFO 페이지 교체
     */
    private Map<String, Object> simulateFIFO(int frames, List<Integer> refs) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (frames <= 0) {
            return Map.of("steps", steps, "faults", 0);
        }

        int[] mem = new int[frames];
        Arrays.fill(mem, -1);
        int faults = 0;
        int nextIdx = 0; // 교체할 프레임 인덱스 (원형 큐)

        for (int r : refs) {
            boolean hit = contains(mem, r);
            if (!hit) {
                // 빈 프레임이 있으면 먼저 채우기
                int emptyIndex = indexOf(mem, -1);
                if (emptyIndex != -1) {
                    mem[emptyIndex] = r;
                } else {
                    // 빈 프레임 없으면 FIFO 방식으로 교체
                    mem[nextIdx] = r;
                    nextIdx = (nextIdx + 1) % frames;
                }
                faults++;
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("ref", r);
            step.put("frame", copy(mem));
            step.put("fault", !hit);
            // victim 은 프론트에서 옵션이므로 생략해도 됨 (없으면 '-' 로 표시)
            steps.add(step);
        }

        return Map.of("steps", steps, "faults", faults);
    }

    /**
     * LRU 페이지 교체
     */
    private Map<String, Object> simulateLRU(int frames, List<Integer> refs) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (frames <= 0) {
            return Map.of("steps", steps, "faults", 0);
        }

        int[] mem = new int[frames];
        Arrays.fill(mem, -1);
        int faults = 0;
        Map<Integer, Integer> lastUsed = new HashMap<>(); // page -> 마지막 사용 시점

        for (int time = 0; time < refs.size(); time++) {
            int r = refs.get(time);
            boolean hit = contains(mem, r);

            if (!hit) {
                int emptyIndex = indexOf(mem, -1);
                if (emptyIndex != -1) {
                    mem[emptyIndex] = r;
                } else {
                    // 가장 오래 전에 사용된 페이지의 인덱스를 찾기
                    int victimIdx = 0;
                    int oldestTime = Integer.MAX_VALUE;
                    for (int i = 0; i < frames; i++) {
                        int page = mem[i];
                        int usedTime = lastUsed.getOrDefault(page, -1);
                        if (usedTime < oldestTime) {
                            oldestTime = usedTime;
                            victimIdx = i;
                        }
                    }
                    mem[victimIdx] = r;
                }
                faults++;
            }

            // 현재 페이지 r 의 마지막 사용 시점 갱신
            lastUsed.put(r, time);

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("ref", r);
            step.put("frame", copy(mem));
            step.put("fault", !hit);
            steps.add(step);
        }

        return Map.of("steps", steps, "faults", faults);
    }

    /**
     * OPT(Optimal) 페이지 교체 – 미래 참조 정보를 이용해서
     * 앞으로 가장 늦게 사용될 페이지를 교체
     */
    private Map<String, Object> simulateOPT(int frames, List<Integer> refs) {
        List<Map<String, Object>> steps = new ArrayList<>();
        if (frames <= 0) {
            return Map.of("steps", steps, "faults", 0);
        }

        int[] mem = new int[frames];
        Arrays.fill(mem, -1);
        int faults = 0;

        for (int i = 0; i < refs.size(); i++) {
            int r = refs.get(i);
            boolean hit = contains(mem, r);

            if (!hit) {
                int emptyIndex = indexOf(mem, -1);
                if (emptyIndex != -1) {
                    mem[emptyIndex] = r;
                } else {
                    // 각 프레임에 들어있는 페이지가 "다음에 언제" 다시 사용되는지 확인
                    int victimIdx = 0;
                    int farthestNextUse = -1;

                    for (int f = 0; f < frames; f++) {
                        int page = mem[f];
                        int nextUse = findNextUse(refs, i + 1, page); // 없다면 -1
                        if (nextUse == -1) {
                            // 앞으로 다시 안 쓰이면 바로 희생 페이지로
                            victimIdx = f;
                            farthestNextUse = Integer.MAX_VALUE;
                            break;
                        }
                        if (nextUse > farthestNextUse) {
                            farthestNextUse = nextUse;
                            victimIdx = f;
                        }
                    }

                    mem[victimIdx] = r;
                }
                faults++;
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("ref", r);
            step.put("frame", copy(mem));
            step.put("fault", !hit);
            steps.add(step);
        }

        return Map.of("steps", steps, "faults", faults);
    }

    // ====== 공용 유틸 메서드 ======

    private boolean contains(int[] arr, int x) {
        for (int v : arr) if (v == x) return true;
        return false;
    }

    private int indexOf(int[] arr, int x) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == x) return i;
        return -1;
    }

    private List<Integer> copy(int[] arr) {
        List<Integer> out = new ArrayList<>(arr.length);
        for (int v : arr) out.add(v);
        return out;
    }

    private int findNextUse(List<Integer> refs, int startIdx, int targetPage) {
        for (int i = startIdx; i < refs.size(); i++) {
            if (Objects.equals(refs.get(i), targetPage)) {
                return i;
            }
        }
        return -1; // 다시 사용되지 않음
    }
}
