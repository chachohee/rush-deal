package com.rushcrew.payment_service.presentation;

import com.rushcrew.payment_service.application.PaymentService;
import com.rushcrew.payment_service.application.command.PaymentCommand;
import com.rushcrew.payment_service.application.result.PaymentPrepareResult;
import com.rushcrew.payment_service.application.result.PaymentResult;
import com.rushcrew.payment_service.presentation.dto.request.CancelPaymentRequest;
import com.rushcrew.payment_service.presentation.dto.request.CompletePaymentRequest;
import com.rushcrew.payment_service.presentation.dto.request.PaymentRequest;
import com.rushcrew.payment_service.presentation.dto.response.PaymentPrepareResponse;
import com.rushcrew.payment_service.presentation.dto.response.PaymentResponse;
import jakarta.validation.Valid;
import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<PaymentPrepareResponse> preparePayment(
            @Valid @RequestBody PaymentRequest request
    ) {
        PaymentCommand command = request.toCommand();

        PaymentPrepareResult result = paymentService.preparePayment(command);

        return ResponseEntity.ok(PaymentPrepareResponse.from(result));
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public Mono<PaymentResponse> completePayment(
            @RequestBody CompletePaymentRequest completeRequest
    ) {
        Mono<PaymentResult> result = paymentService.completePayment(completeRequest.portOnePaymentId());

        return result.map(PaymentResponse::from);
    }

    @PostMapping("/{paymentId}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public Mono<PaymentResponse> cancelPayment(
            @PathVariable("paymentId") UUID paymentId,
            @Valid @RequestBody CancelPaymentRequest request
            ) {
        Mono<PaymentResult> result = paymentService.cancelPayment(paymentId, request.cancelReason());
        return result.map(PaymentResponse::from);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<PaymentResponse> getPaymentByPaymentId(@PathVariable("paymentId") UUID paymentId) {
        PaymentResult result = paymentService.findPaymentByPaymentId(paymentId);

        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable("orderId") UUID orderId) {
        PaymentResult result = paymentService.findPaymentByOrderId(orderId);

        return ResponseEntity.ok(PaymentResponse.from(result));
    }

    @PostMapping("/webhook")
    public Mono<Unit> handleWebhook(
            @RequestBody String body,
            @RequestHeader("webhook-id") String webhookId,
            @RequestHeader("webhook-timestamp") String webhookTimestamp,
            @RequestHeader("webhook-signature") String webhookSignature
    ) throws Exception {
        return paymentService.handleWebhook(body, webhookId, webhookTimestamp, webhookSignature);
    }
}