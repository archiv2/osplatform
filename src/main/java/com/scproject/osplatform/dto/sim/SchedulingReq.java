package com.scproject.osplatform.dto.sim;

import java.util.List;

public class SchedulingReq {
    public String algo;          // "FCFS" | "RR"
    public Integer quantum;      // RR일 때만 (타임 퀀텀)
    public List<Proc> processes; // 프로세스 목록

    public static class Proc {
        public String pid;
        public int arrival;
        public int burst;
    }
}
