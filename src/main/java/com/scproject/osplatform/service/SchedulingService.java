package com.scproject.osplatform.service;

import com.scproject.osplatform.dto.sim.SchedulingReq;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchedulingService {

    /**
     * algo 값에 따라 FCFS / RR 로 분기하고
     * 프론트가 기대하는 형태의 Map 을 반환합니다.
     *
     * 반환 형태:
     * {
     *   "gantt": [ { "pid": "P1", "start": 0, "end": 3 }, ... ],
     *   "metrics": { "avgWaiting": 1.5, "avgTurnaround": 4.2, "avgResponse": 1.5 }
     * }
     */
    public Map<String, Object> run(SchedulingReq req) {
        String algo = (req.algo == null ? "FCFS" : req.algo.toUpperCase(Locale.ROOT));
        List<SchedulingReq.Proc> processes = req.processes != null ? req.processes : Collections.emptyList();

        if ("RR".equals(algo)) {
            int q = (req.quantum == null || req.quantum <= 0) ? 1 : req.quantum;
            return simulateRR(processes, q);
        } else {
            // 기본은 FCFS
            return simulateFCFS(processes);
        }
    }

    /**
     * FCFS (First Come First Served) – 도착 순서대로 비선점 실행
     */
    private Map<String, Object> simulateFCFS(List<SchedulingReq.Proc> processes) {
        List<SchedulingReq.Proc> procs = new ArrayList<>(processes);
        // 도착 시간 기준 정렬
        procs.sort(Comparator.comparingInt(p -> p.arrival));

        List<Map<String, Object>> gantt = new ArrayList<>();

        Map<String, Integer> arrival = new HashMap<>();
        Map<String, Integer> burst = new HashMap<>();
        Map<String, Integer> firstStart = new HashMap<>();
        Map<String, Integer> endTime = new HashMap<>();

        int time = 0;
        for (SchedulingReq.Proc p : procs) {
            arrival.put(p.pid, p.arrival);
            burst.put(p.pid, p.burst);

            // CPU 가 비어 있고, 프로세스가 나중에 도착하면 그때까지 점프
            if (time < p.arrival) {
                time = p.arrival;
            }

            int start = time;
            int end = start + p.burst;

            firstStart.put(p.pid, start);
            endTime.put(p.pid, end);

            gantt.add(segment(p.pid, start, end));

            time = end;
        }

        Map<String, Object> metrics = calcMetrics(procs, arrival, burst, firstStart, endTime);
        return Map.of("gantt", gantt, "metrics", metrics);
    }

    /**
     * RR (Round Robin) – 타임퀀텀 기반 선점 스케줄링
     */
    private Map<String, Object> simulateRR(List<SchedulingReq.Proc> processes, int quantum) {
        List<SchedulingReq.Proc> procs = new ArrayList<>(processes);
        if (procs.isEmpty()) {
            return Map.of("gantt", List.of(), "metrics", Map.of(
                    "avgWaiting", 0.0,
                    "avgTurnaround", 0.0,
                    "avgResponse", 0.0
            ));
        }

        // 도착 시간 순으로 정렬
        procs.sort(Comparator.comparingInt(p -> p.arrival));

        Map<String, Integer> arrival = new HashMap<>();
        Map<String, Integer> burst = new HashMap<>();
        Map<String, Integer> remaining = new HashMap<>();
        Map<String, Integer> firstStart = new HashMap<>();
        Map<String, Integer> endTime = new HashMap<>();

        for (SchedulingReq.Proc p : procs) {
            arrival.put(p.pid, p.arrival);
            burst.put(p.pid, p.burst);
            remaining.put(p.pid, p.burst);
        }

        List<Map<String, Object>> gantt = new ArrayList<>();
        Queue<String> ready = new ArrayDeque<>();

        int time = procs.get(0).arrival;
        int idx = 0; // 아직 도착하지 않은 프로세스 인덱스

        // 초기 도착 프로세스 큐에 추가
        while (idx < procs.size() && procs.get(idx).arrival <= time) {
            ready.add(procs.get(idx).pid);
            idx++;
        }

        while (!ready.isEmpty() || idx < procs.size()) {
            if (ready.isEmpty()) {
                // 다음 도착 프로세스까지 점프
                time = Math.max(time, procs.get(idx).arrival);
                while (idx < procs.size() && procs.get(idx).arrival <= time) {
                    ready.add(procs.get(idx).pid);
                    idx++;
                }
                continue;
            }

            String pid = ready.poll();
            int rem = remaining.get(pid);
            if (rem <= 0) {
                continue;
            }

            int run = Math.min(rem, quantum);
            int start = time;
            int end = time + run;

            // 간트차트 조각 추가
            gantt.add(segment(pid, start, end));

            // 첫 응답 시간 기록
            firstStart.putIfAbsent(pid, start);

            time = end;
            remaining.put(pid, rem - run);

            // 새로 도착한 프로세스를 그 동안 ready 큐에 추가
            while (idx < procs.size() && procs.get(idx).arrival <= time) {
                ready.add(procs.get(idx).pid);
                idx++;
            }

            // 아직 남은 시간이 있으면 다시 큐에 넣기
            if (remaining.get(pid) > 0) {
                ready.add(pid);
            } else {
                endTime.put(pid, time);
            }
        }

        Map<String, Object> metrics = calcMetrics(procs, arrival, burst, firstStart, endTime);
        return Map.of("gantt", gantt, "metrics", metrics);
    }

    // 간트차트 한 칸을 Map 으로 만들기
    private Map<String, Object> segment(String pid, int start, int end) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pid", pid);
        m.put("start", start);
        m.put("end", end);
        return m;
    }

    /**
     * 평균 대기시간 / 반환시간 / 응답시간 계산
     */
    private Map<String, Object> calcMetrics(
            List<SchedulingReq.Proc> procs,
            Map<String, Integer> arrival,
            Map<String, Integer> burst,
            Map<String, Integer> firstStart,
            Map<String, Integer> endTime
    ) {
        if (procs.isEmpty()) {
            return Map.of(
                    "avgWaiting", 0.0,
                    "avgTurnaround", 0.0,
                    "avgResponse", 0.0
            );
        }

        double sumW = 0, sumT = 0, sumR = 0;
        int cnt = procs.size();

        for (SchedulingReq.Proc p : procs) {
            String pid = p.pid;
            int a = arrival.get(pid);
            int b = burst.get(pid);
            int fs = firstStart.get(pid);
            int e = endTime.get(pid);

            int turnaround = e - a;
            int waiting = turnaround - b;
            int response = fs - a;

            sumW += waiting;
            sumT += turnaround;
            sumR += response;
        }

        return Map.of(
                "avgWaiting", round(sumW / cnt),
                "avgTurnaround", round(sumT / cnt),
                "avgResponse", round(sumR / cnt)
        );
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
