package com.rushcrew.timedeal.search.presentation;

import com.rushcrew.timedeal.search.document.TimeDealDocument;
import com.rushcrew.timedeal.search.service.TimeDealSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timedeals/search")
@RequiredArgsConstructor
public class TimeDealSearchController {

    private final TimeDealSearchService searchService;

    @GetMapping
    public ResponseEntity<Page<TimeDealDocument>> search(
        @RequestParam("q") String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(Page.empty());
        }
        Page<TimeDealDocument> result = searchService.search(q.trim(), PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }
}
