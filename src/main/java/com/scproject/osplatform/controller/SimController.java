package com.scproject.osplatform.controller;

import com.scproject.osplatform.dto.sim.*;
import com.scproject.osplatform.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sim")
public class SimController {

    private final SchedulingService schedulingService;
    private final PagingService pagingService;
    private final BankersService bankersService;

    // ✅ 생성자 주입 (중요)
    public SimController(SchedulingService schedulingService,
                         PagingService pagingService,
                         BankersService bankersService) {
        this.schedulingService = schedulingService;
        this.pagingService = pagingService;
        this.bankersService = bankersService;
    }

    @PostMapping("/scheduling")
    public Map<String,Object> scheduling(@RequestBody SchedulingReq req){
        return schedulingService.run(req);
    }

    @PostMapping("/paging")
    public Map<String,Object> paging(@RequestBody PagingReq req){
        return pagingService.run(req);
    }

    @PostMapping("/deadlock")
    public Map<String,Object> deadlock(@RequestBody BankerReq req){
        return bankersService.run(req);
    }
}
