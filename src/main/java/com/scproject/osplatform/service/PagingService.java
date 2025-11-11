package com.scproject.osplatform.service;

import com.scproject.osplatform.dto.sim.PagingReq;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PagingService {

    public Map<String,Object> run(PagingReq req){
        String algo = req.algo == null ? "FIFO" : req.algo.toUpperCase();
        if ("LRU".equals(algo)) return lru(req.frames, req.refs);
        return fifo(req.frames, req.refs);
    }

    private Map<String,Object> fifo(int frames, List<Integer> refs){
        int[] mem = new int[frames]; Arrays.fill(mem, -1);
        int ptr = 0, faults = 0;
        List<Map<String,Object>> steps = new ArrayList<>();

        for (int r: refs){
            boolean hit = contains(mem, r);
            if (!hit){ mem[ptr] = r; ptr = (ptr+1)%frames; faults++; }
            steps.add(Map.of("ref", r, "frame", copy(mem), "fault", !hit));
        }
        return Map.of("steps", steps, "faults", faults);
    }

    private Map<String,Object> lru(int frames, List<Integer> refs){
        int[] mem = new int[frames]; Arrays.fill(mem, -1);
        int faults = 0; List<Map<String,Object>> steps = new ArrayList<>();
        Map<Integer,Integer> lastUsed = new HashMap<>();

        for (int t=0; t<refs.size(); t++){
            int r = refs.get(t);
            boolean hit = contains(mem, r);
            if (!hit){
                int empty = indexOf(mem, -1);
                if (empty >= 0) mem[empty] = r;
                else {
                    int victimIdx = 0, oldest = Integer.MAX_VALUE;
                    for (int i=0;i<frames;i++){
                        int p = mem[i], lu = lastUsed.getOrDefault(p, -1);
                        if (lu < oldest){ oldest = lu; victimIdx = i; }
                    }
                    mem[victimIdx] = r;
                }
                faults++;
            }
            lastUsed.put(r, t);
            steps.add(Map.of("ref", r, "frame", copy(mem), "fault", !hit));
        }
        return Map.of("steps", steps, "faults", faults);
    }

    private boolean contains(int[] a, int x){ for (int v: a) if (v==x) return true; return false; }
    private int indexOf(int[] a, int x){ for (int i=0;i<a.length;i++) if (a[i]==x) return i; return -1; }
    private List<Integer> copy(int[] a){ List<Integer> out=new ArrayList<>(a.length); for(int v:a) out.add(v); return out; }
}
