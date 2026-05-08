package com.rushcrew.payment_service.presentation;

import com.rushcrew.payment_service.infrastructure.config.PortOneSecretProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.UUID;

@Controller
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentPageController {

    private final PortOneSecretProperties secret;

    @GetMapping("/page/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public String getPaymentRequest(
            @PathVariable UUID orderId,
            Model model
    ) {
        // TODO: Order 서비스에서 주문 정보 조회

        // 임시로 더미 데이터 사용
        BigDecimal amount = BigDecimal.valueOf(10000);

        model.addAttribute("orderId", orderId);
        model.addAttribute("amount", amount);
        model.addAttribute("storeId", secret.storeId());
        model.addAttribute("channelKey", secret.channelKey());
        return "payment";
    }
}
