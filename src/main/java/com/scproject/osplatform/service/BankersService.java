package com.scproject.osplatform.service;

import com.scproject.osplatform.dto.sim.BankerReq;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BankersService {

    public Map<String,Object> run(BankerReq req){
        int n = req.allocation.length; // 프로세스 수
        int m = req.available.length;  // 자원 종류 수

        // Need = Max - Allocation
        int[][] need = new int[n][m];
        for (int i=0;i<n;i++)
            for (int j=0;j<m;j++)
                need[i][j] = req.max[i][j] - req.allocation[i][j];

        int[] work = Arrays.copyOf(req.available, m);
        boolean[] finish = new boolean[n];
        List<Integer> sequence = new ArrayList<>();
        List<Map<String,Object>> trace = new ArrayList<>();

        boolean progress;
        do {
            progress = false;
            for (int i=0;i<n;i++){
                if (!finish[i] && canAllocate(need[i], work)){
                    trace.add(Map.of("pick", i, "workBefore", copy(work), "need", copy(need[i])));
                    for (int j=0;j<m;j++) work[j] += req.allocation[i][j];
                    finish[i] = true;
                    sequence.add(i);
                    progress = true;
                    trace.add(Map.of("finish", i, "workAfter", copy(work)));
                }
            }
        } while(progress);

        boolean safe = true;
        for (boolean f: finish) if (!f) { safe = false; break; }

        return Map.of("safe", safe, "sequence", sequence, "trace", trace);
    }

    private boolean canAllocate(int[] need, int[] work){
        for (int j=0;j<need.length;j++) if (need[j] > work[j]) return false;
        return true;
    }
    private List<Integer> copy(int[] a){ List<Integer> r=new ArrayList<>(a.length); for(int v:a) r.add(v); return r; }
}
