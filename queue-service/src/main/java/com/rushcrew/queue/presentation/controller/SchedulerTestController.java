package com.rushcrew.queue.presentation.controller;

import com.rushcrew.queue.infrastructure.scheduler.QueueScheduler;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test/scheduler")
public class SchedulerTestController {

    private final QueueScheduler queueScheduler;

    public SchedulerTestController(QueueScheduler queueScheduler) {
        this.queueScheduler = queueScheduler;
    }

    // 포스트맨에서 이 API를 호출하면 스케줄러 로직이 즉시 실행됨
    @PostMapping("/activation")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<String> triggerActivation() {
        queueScheduler.refreshPolicies();
        queueScheduler.scheduleActivation();
        return ResponseEntity.ok("스케줄러 수동 실행 요청됨");
    }
}