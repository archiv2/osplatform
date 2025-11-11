package com.scproject.osplatform.dto.sim;

import java.util.List;

public class PagingReq {
    public String algo;      // FIFO, LRU
    public int frames;       // 프레임 수
    public List<Integer> refs; // 참조열
}
