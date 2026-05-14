package com.rushcrew.payment_service.presentation;

import com.rushcrew.payment_service.infrastructure.client.OrderClient;
import com.rushcrew.payment_service.infrastructure.client.dto.OrderResponse;
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
    private final OrderClient orderClient;

    @GetMapping("/page/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'SELLER', 'MASTER')")
    public String getPaymentRequest(
            @PathVariable UUID orderId,
            Model model
    ) {
        OrderResponse order = orderClient.getOrder(orderId).data();
        BigDecimal amount = order.totalAmount();

        model.addAttribute("orderId", orderId);
        model.addAttribute("amount", amount);
        model.addAttribute("storeId", secret.storeId());
        model.addAttribute("channelKey", secret.channelKey());
        return "payment";
    }
}
