package com.scproject.osplatform.controller;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")

public class TestController {
    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("status", "ok", "ts", System.currentTimeMillis());
    }

}
