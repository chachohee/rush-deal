package com.rushcrew.payment_service.application;

import java.math.BigDecimal;
import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.payment_service.application.command.PaymentCommand;
import com.rushcrew.payment_service.application.result.PaymentPrepareResult;
import com.rushcrew.payment_service.application.result.PaymentResult;
import com.rushcrew.payment_service.domain.exception.PaymentErrorCode;
import com.rushcrew.payment_service.domain.model.Payment;
import com.rushcrew.payment_service.domain.model.PaymentTransaction;
import com.rushcrew.payment_service.domain.repository.PaymentRepository;
import com.rushcrew.payment_service.domain.repository.PaymentTransactionRepository;
import com.rushcrew.payment_service.domain.vo.Amount;
import com.rushcrew.payment_service.domain.vo.Card;
import com.rushcrew.payment_service.infrastructure.client.OrderClient;
import com.rushcrew.payment_service.infrastructure.client.dto.OrderResponse;
import com.rushcrew.payment_service.infrastructure.event.PaymentCompletedEvent;
import com.rushcrew.payment_service.infrastructure.event.PaymentEventProducer;
import feign.FeignException;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.payment.PaymentMethodCard;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookVerifier;
import kotlin.Unit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    private final PaymentClient portone;
    private final WebhookVerifier portoneWebhook;

    private final PaymentEventProducer paymentEventProducer;
    private final OrderClient orderClient;

    @Transactional
    public PaymentPrepareResult preparePayment(PaymentCommand command) {
        try {
            OrderResponse orderResponse = orderClient.getOrder(command.orderId()).data();

            // 결제 요청 금액(command.totalAmount = order.finalAmount, 포인트 차감 후) 과
            // order 의 finalAmount 를 비교. (orderResponse.totalAmount 는 포인트 차감 전 합계라 다를 수 있음)
            BigDecimal expected = orderResponse != null ? orderResponse.finalAmount() : null;
            if (expected == null || command.totalAmount() == null
                || expected.compareTo(command.totalAmount()) != 0) {
                throw new BusinessException(PaymentErrorCode.AMOUNT_MISMATCH);
            }
        } catch (FeignException.NotFound e) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        } catch (FeignException e) {
            throw new BusinessException(PaymentErrorCode.ORDER_NOT_FOUND);
        }

        String portOnePaymentId = UUID.randomUUID().toString();

        Payment payment = Payment.create(
                command.orderId(),
                command.totalAmount(),
                portOnePaymentId
        );

        Payment savedPayment = paymentRepository.save(payment);

        return PaymentPrepareResult.of(portOnePaymentId, savedPayment);
    }

    @Transactional
    public Mono<PaymentResult> completePayment(String portOnePaymentId) {
        return syncPayment(portOnePaymentId);
    }
    @Transactional
    public Mono<PaymentResult> cancelPayment(UUID paymentId, String cancelReason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_PAYMENT));

        String portonePaymentId = payment.getPortonePaymentId();

        return Mono.fromFuture(portone.cancelPayment(
                portonePaymentId,
                null,
                null,
                null,
                        cancelReason,
                null,
                null,
                null
        ))
                .flatMap(cancelResponse -> {
                    payment.cancelPayment();
                    paymentRepository.save(payment);

                    return Mono.just(PaymentResult.from(payment));
                })
                .onErrorMap(e -> new BusinessException(PaymentErrorCode.FAILED_CANCEL_PAYMENT));
    }

    public PaymentResult findPaymentByPaymentId(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_PAYMENT));

        return PaymentResult.from(payment);
    }

    public PaymentResult findPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_PAYMENT));

        return PaymentResult.from(payment);
    }
    public Mono<Unit> handleWebhook(String body, String webhookId, String webhookTimestamp, String webhookSignature) throws Exception {
        Webhook webhook;
        try {
            webhook = portoneWebhook.verify(body, webhookId, webhookSignature, webhookTimestamp);
        } catch (Exception e) {
            throw new Exception();
        }
        if (webhook instanceof WebhookTransaction transaction) {
            return syncPayment(transaction.getData().getPaymentId()).map(payment -> Unit.INSTANCE);
        }
        return Mono.empty();
    }

    @Transactional
    public Mono<PaymentResult> syncPayment(String portOnePaymentId) {
        Payment payment = paymentRepository.findByPortonePaymentId(portOnePaymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.INVALID_PAYMENT));

        return Mono.fromFuture(portone.getPayment(portOnePaymentId))
                .flatMap(actualPayment -> {
                    switch (actualPayment) {
                        case PaidPayment paidPayment:
                            try {
                                payment.verifyPaymentOrThrow(paidPayment.getAmount().getPaid(), paidPayment.getCurrency().getValue());
                            } catch (IllegalArgumentException e) {
                                return Mono.error(new BusinessException(PaymentErrorCode.FAILED_VERIFYING_PAYMENT));
                            }

                            payment.completePayment();
                            paymentRepository.save(payment);

                            if (paidPayment.getMethod() instanceof PaymentMethodCard paymentMethodCard) {
                                PaymentTransaction transaction = PaymentTransaction.create(
                                        payment,
                                        paidPayment.getId(),
                                        paidPayment.getTransactionId(),
                                        paidPayment.getStoreId(),
                                        paidPayment.getRequestedAt(),
                                        paidPayment.getUpdatedAt(),
                                        paidPayment.getStatusChangedAt()
                                );

                                Card card = new Card(
                                        paymentMethodCard.getCard().getPublisher(),
                                        paymentMethodCard.getCard().getIssuer(),
                                        paymentMethodCard.getCard().getBrand().toString(),
                                        paymentMethodCard.getCard().getType().toString(),
                                        paymentMethodCard.getCard().getOwnerType().toString(),
                                        paymentMethodCard.getCard().getBin(),
                                        paymentMethodCard.getCard().getName(),
                                        paymentMethodCard.getCard().getNumber()
                                );
                                transaction.addCard(card);

                                Amount amount = new Amount(
                                        paidPayment.getAmount().getTotal(),
                                        paidPayment.getAmount().getTaxFree(),
                                        paidPayment.getAmount().getVat(),
                                        paidPayment.getAmount().getSupply(),
                                        paidPayment.getAmount().getDiscount(),
                                        paidPayment.getAmount().getPaid()
                                );
                                transaction.addAmount(amount);

                                paymentTransactionRepository.save(transaction);
                            }

                            // Kafka 이벤트 발행
                            PaymentCompletedEvent event = PaymentCompletedEvent.of(
                                    payment.getPaymentId(),
                                    payment.getOrderId(),
                                    payment.getAmount(),
                                    paidPayment.getCurrency().getValue()
                            );
                            paymentEventProducer.publishPaymentCompleted(event);

                            return Mono.just(PaymentResult.from(payment));
                        default:
                            return Mono.error(new BusinessException(PaymentErrorCode.NOT_COMPLETED_PAYMENT));
                    }
                });
    }
}
