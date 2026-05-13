package com.rushcrew.timedeal.interest.presentation;

import com.rushcrew.timedeal.application.result.TimeDealResult;
import com.rushcrew.timedeal.interest.application.InterestedDealService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InterestedDealController {

    private final InterestedDealService service;

    @PostMapping("/timedeals/{timeDealId}/interest")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<Void> register(
        @PathVariable UUID timeDealId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        service.register(userId, timeDealId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/timedeals/{timeDealId}/interest")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<Void> unregister(
        @PathVariable UUID timeDealId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        service.unregister(userId, timeDealId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/timedeals/{timeDealId}/interest")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<Map<String, Boolean>> isInterested(
        @PathVariable UUID timeDealId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(Map.of("interested", service.isInterested(userId, timeDealId)));
    }

    @GetMapping("/timedeals/me/interested")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<List<TimeDealResult>> listMine(
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(service.listMyInterested(userId));
    }
}
