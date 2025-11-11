package com.scproject.osplatform.service;

import com.scproject.osplatform.dto.sim.SchedulingReq;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchedulingService {

    public Map<String,Object> run(SchedulingReq req){
        String algo = req.algo == null ? "FCFS" : req.algo.toUpperCase();
        if ("RR".equals(algo)) {
            int q = (req.quantum == null || req.quantum <= 0) ? 1 : req.quantum;
            return rr(req.processes, q);
        }
        return fcfs(req.processes);
    }

    private Map<String,Object> fcfs(List<SchedulingReq.Proc> procs){
        procs.sort(Comparator.comparingInt(p -> p.arrival));
        List<Map<String,Object>> gantt = new ArrayList<>();
        int time = 0;

        Map<String,Integer> firstStart = new HashMap<>();
        Map<String,Integer> endTime = new HashMap<>();
        Map<String,Integer> burstMap = new HashMap<>();
        Map<String,Integer> arrivalMap = new HashMap<>();

        for (var p : procs){
            int start = Math.max(time, p.arrival);
            int end = start + p.burst;
            gantt.add(Map.of("pid", p.pid, "start", start, "end", end));
            time = end;

            arrivalMap.put(p.pid, p.arrival);
            burstMap.put(p.pid, p.burst);
            endTime.put(p.pid, end);
            firstStart.putIfAbsent(p.pid, start);
        }

        Map<String,Double> metrics = calcMetrics(arrivalMap, burstMap, firstStart, endTime);
        return Map.of("gantt", gantt, "metrics", metrics);
    }

    private Map<String,Object> rr(List<SchedulingReq.Proc> procs, int q){
        procs.sort(Comparator.comparingInt(p -> p.arrival));
        Queue<SchedulingReq.Proc> ready = new ArrayDeque<>();
        int n = procs.size(), idx = 0, time = 0;

        Map<String,Integer> remaining = new HashMap<>();
        Map<String,Integer> firstStart = new HashMap<>();
        Map<String,Integer> endTime = new HashMap<>();
        Map<String,Integer> burstMap = new HashMap<>();
        Map<String,Integer> arrivalMap = new HashMap<>();
        for (var p: procs){
            remaining.put(p.pid, p.burst);
            burstMap.put(p.pid, p.burst);
            arrivalMap.put(p.pid, p.arrival);
        }

        List<Map<String,Object>> gantt = new ArrayList<>();

        while(true){
            while (idx < n && procs.get(idx).arrival <= time){
                ready.offer(procs.get(idx++));
            }
            if (ready.isEmpty()){
                if (idx < n) { time = procs.get(idx).arrival; continue; }
                else break;
            }
            var cur = ready.poll();
            int rem = remaining.get(cur.pid);
            int slice = Math.min(q, rem);
            int start = time, end = time + slice;
            gantt.add(Map.of("pid", cur.pid, "start", start, "end", end));
            firstStart.putIfAbsent(cur.pid, start);
            time = end;
            rem -= slice;
            remaining.put(cur.pid, rem);

            while (idx < n && procs.get(idx).arrival <= time){
                ready.offer(procs.get(idx++));
            }
            if (rem > 0) ready.offer(cur);
            else endTime.put(cur.pid, end);
        }

        Map<String,Double> metrics = calcMetrics(arrivalMap, burstMap, firstStart, endTime);
        return Map.of("gantt", gantt, "metrics", metrics);
    }

    private Map<String,Double> calcMetrics(Map<String,Integer> arrival,
                                           Map<String,Integer> burst,
                                           Map<String,Integer> firstStart,
                                           Map<String,Integer> endTime){
        double sumW=0, sumT=0, sumR=0;
        int cnt = arrival.size();
        for (var pid: arrival.keySet()){
            int a = arrival.get(pid), b = burst.get(pid);
            int fs = firstStart.get(pid), e = endTime.get(pid);
            int turnaround = e - a;
            int waiting = turnaround - b;
            int response = fs - a;
            sumW += waiting; sumT += turnaround; sumR += response;
        }
        return Map.of(
                "avgWaiting", round(sumW/cnt),
                "avgTurnaround", round(sumT/cnt),
                "avgResponse", round(sumR/cnt)
        );
    }
    private double round(double v){ return Math.round(v*100.0)/100.0; }
}
