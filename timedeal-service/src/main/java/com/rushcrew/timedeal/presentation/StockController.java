package com.rushcrew.timedeal.presentation;

import com.rushcrew.timedeal.application.command.CreateStockCommand;
import com.rushcrew.timedeal.application.command.RestoreStockCommand;
import com.rushcrew.timedeal.application.command.UpdateStockCountCommand;
import com.rushcrew.timedeal.application.result.CreateStockResult;
import com.rushcrew.timedeal.application.result.StockLogResult;
import com.rushcrew.timedeal.application.result.StockResult;
import com.rushcrew.timedeal.application.result.UpdateStockCountResult;
import com.rushcrew.timedeal.application.service.StockService;
import com.rushcrew.timedeal.domain.vo.TimeDealStockStatus;
import com.rushcrew.timedeal.global.security.model.UserDetailsImpl;
import com.rushcrew.timedeal.presentation.dto.request.CreateStockRequest;
import com.rushcrew.timedeal.presentation.dto.request.RestoreStockRequest;
import com.rushcrew.timedeal.presentation.dto.request.UpdateStockCountRequest;
import com.rushcrew.timedeal.presentation.dto.response.CreateStockResponse;
import com.rushcrew.timedeal.presentation.dto.response.StockLogResponse;
import com.rushcrew.timedeal.presentation.dto.response.StockResponse;
import com.rushcrew.timedeal.presentation.dto.response.UpdateStockCountResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<CreateStockResponse> createStock(
        @RequestBody @Valid CreateStockRequest request
    ) {
        CreateStockCommand command = request.toCommand();
        CreateStockResult result = stockService.createStock(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateStockResponse.from(result));
    }

    @PostMapping("/{stockId}/change")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<UpdateStockCountResponse> changeStockCount(
        @PathVariable UUID stockId,
        @RequestBody @Valid UpdateStockCountRequest request
    ) {
        UpdateStockCountCommand command = request.toCommand();
        UpdateStockCountResult result = stockService.changeStockCount(stockId, command);
        return ResponseEntity.ok(UpdateStockCountResponse.from(result));
    }

    @GetMapping
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Page<StockResponse>> getStocks(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) UUID productId,
        @RequestParam(required = false) TimeDealStockStatus status,
        @SortDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        Page<StockResult> result = stockService.getStocks(keyword, productId, status, pageable);
        Page<StockResponse> response = result.map(StockResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{stockId}")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<StockResponse> getStock(
        @PathVariable UUID stockId,
        @AuthenticationPrincipal UserDetailsImpl principle
    ) {
        StockResult result = stockService.getStock(principle.userId(), principle.role(), stockId);
        return ResponseEntity.ok(StockResponse.from(result));
    }

    @GetMapping("/seller/me/low")
    @PreAuthorize("hasAnyRole('MASTER', 'SELLER')")
    public ResponseEntity<java.util.List<StockResponse>> getLowStockForMe(
        @AuthenticationPrincipal UserDetailsImpl principle,
        @RequestParam(defaultValue = "10") Long threshold,
        @RequestParam(defaultValue = "5") int limit
    ) {
        java.util.List<StockResponse> response = stockService
            .getLowStock(principle.userId(), threshold, limit).stream()
            .map(StockResponse::from)
            .toList();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{stockId}")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Void> deleteStock(
        @PathVariable UUID stockId,
        @AuthenticationPrincipal UserDetailsImpl principle
    ) {
        stockService.deleteStock(stockId, principle.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/restore")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> restoreStock(
        @RequestBody @Valid RestoreStockRequest request
    ) {
        RestoreStockCommand command = request.toCommand();
        stockService.restoreStock(command);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Page<StockLogResponse>> getStockLogs(
        @RequestParam(required = false) UUID stockId,
        @RequestParam(required = false) String eventType,
        @SortDefault(sort = "createdAt", direction = Direction.DESC) Pageable pageable
    ) {
        Page<StockLogResult> result = stockService.getStockLogs(stockId, eventType, pageable);
        Page<StockLogResponse> response = result.map(StockLogResponse::from);
        return ResponseEntity.ok(response);
    }
}
