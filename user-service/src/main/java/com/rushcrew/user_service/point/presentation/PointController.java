package com.rushcrew.user_service.point.presentation;

import com.rushcrew.user_service.point.application.PointService;
import com.rushcrew.user_service.point.application.command.CancelOrderCommand;
import com.rushcrew.user_service.point.application.command.CreatePendingPointCommand;
import com.rushcrew.user_service.point.application.command.UsePointCommand;
import com.rushcrew.user_service.point.presentation.dto.request.CancelOrderRequest;
import com.rushcrew.user_service.point.presentation.dto.request.CreatePendingPointRequest;
import com.rushcrew.user_service.point.presentation.dto.request.UsePointRequest;
import com.rushcrew.user_service.point.presentation.dto.response.PointBalanceResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/balance")
    public ResponseEntity<PointBalanceResponse> getBalance(
        @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(new PointBalanceResponse(pointService.getBalance(userId)));
    }

    @PostMapping("/pending")
    public ResponseEntity<Void> createPendingPoint(
        @Valid @RequestBody CreatePendingPointRequest request
    ) {
        CreatePendingPointCommand command = request.toCommand();
        pointService.createPendingPoint(command);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/use")
    public ResponseEntity<Void> usePoint(
        @Valid @RequestBody UsePointRequest request
    ) {
        UsePointCommand command = request.toCommand();
        pointService.usePoints(command);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/order/cancel")
    public ResponseEntity<Void> cancelOrder(
        @Valid @RequestBody CancelOrderRequest request
    ) {
        CancelOrderCommand command = request.toCommand();
        pointService.cancelOrder(command);

        return ResponseEntity.ok().build();
    }
}
