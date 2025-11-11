package com.scproject.osplatform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api")
public class ApiController {

    // 임시 메모리 저장소
    private final List<Map<String, Object>> records = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger seq = new AtomicInteger(1);

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "ts", System.currentTimeMillis());
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        if ("test@example.com".equals(email) && "1234".equals(password)) {
            return ResponseEntity.ok(Map.of("ok", true, "name", "ㅇㅇㅇ"));
        }
        return ResponseEntity.status(401).body(Map.of("ok", false, "message", "invalid credentials"));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("name", "ㅇㅇㅇ", "email", "test@example.com");
    }

    // 기록 생성 후 리스트 저장
    @PostMapping("/records")
    public Map<String, Object> createRecord(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "내 기록");
        int id = seq.getAndIncrement();

        Map<String, Object> rec = new HashMap<>();
        rec.put("id", id);
        rec.put("title", title);
        rec.put("created", System.currentTimeMillis());

        records.add(rec);
        return rec;
    }

    //기록목록 조회
    @GetMapping("/records")
    public List<Map<String, Object>> listRecords() {
        List<Map<String, Object>> copy = new ArrayList<>(records);
        copy.sort((a,b) -> Long.compare((Long)b.get("created"), (Long)a.get("created")));
        return copy;
    }

    //기록삭제
    @DeleteMapping("/records/{id}")
    public ResponseEntity<?> deleteRecord(@PathVariable int id) {
        boolean removed = records.removeIf(r -> Objects.equals(r.get("id"), id));
        if (removed) return ResponseEntity.ok(Map.of("ok", true));
        return ResponseEntity.status(404).body(Map.of("ok", false, "message", "not found"));
    }
}
