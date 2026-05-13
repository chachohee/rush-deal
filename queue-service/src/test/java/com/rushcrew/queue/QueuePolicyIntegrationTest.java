package com.rushcrew.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.rushcrew.common.exception.BusinessException;
import com.rushcrew.queue.application.command.policy.CreatePolicyCommand;
import com.rushcrew.queue.application.service.QueuePolicyService;
import com.rushcrew.queue.common.QueueErrorCode;
import com.rushcrew.queue.domain.entity.QueuePolicy;
import com.rushcrew.queue.domain.enums.QueuePolicyStatus;
import com.rushcrew.queue.domain.repository.QueuePolicyRepository;
import com.rushcrew.queue.domain.vo.TimePeriod;
import com.rushcrew.queue.domain.vo.TrafficSetting;
import com.rushcrew.queue.infrastructure.repository.jpa.JpaQueuePolicyRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

class QueuePolicyIntegrationTest extends IntegrationTestBase {

    @Autowired QueuePolicyService service;
    @Autowired QueuePolicyRepository repository;
    @Autowired JpaQueuePolicyRepository jpaRepository;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    @AfterEach
    void clean() {
        jpaRepository.deleteAll();
    }

    private CreatePolicyCommand cmd(UUID productId, String name, QueuePolicyStatus status) {
        return new CreatePolicyCommand(productId, name, status,
            new TimePeriod(LocalDateTime.now(), LocalDateTime.now().plusHours(1)),
            new TrafficSetting(100, 10, 5, 300));
    }

    @Test
    @DisplayName("createQueuePolicy: 같은 상품에 두 번째 호출 시 활성 상태면 POLICY_ALREADY_EXISTS")
    void createPolicy_duplicateActive_throws() {
        UUID productId = UUID.randomUUID();
        service.createQueuePolicy(cmd(productId, "FIRST", QueuePolicyStatus.RUNNING), 1L);

        assertThatThrownBy(() ->
            service.createQueuePolicy(cmd(productId, "SECOND", QueuePolicyStatus.RUNNING), 1L))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(QueueErrorCode.POLICY_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("createQueuePolicy: 기존 STOPPED 정책이 있으면 새로 생성하지 않고 같은 row를 upsert")
    void createPolicy_existingStopped_isUpserted() {
        UUID productId = UUID.randomUUID();
        UUID firstId = service.createQueuePolicy(cmd(productId, "FIRST", QueuePolicyStatus.RUNNING), 1L)
            .policyId();
        service.deactivateByProductId(productId);

        UUID secondId = service.createQueuePolicy(cmd(productId, "REUSED", QueuePolicyStatus.RUNNING), 1L)
            .policyId();

        assertThat(secondId).isEqualTo(firstId);
        QueuePolicy reused = jpaRepository.findById(firstId).orElseThrow();
        assertThat(reused.getStatus()).isEqualTo(QueuePolicyStatus.RUNNING);
        assertThat(reused.getTimeDealName()).isEqualTo("REUSED");
    }

    @Test
    @DisplayName("time-deal-end Kafka 이벤트 수신 시 해당 productId의 정책이 STOPPED로 전환된다")
    void timeDealEnd_deactivatesPolicy() {
        UUID productId = UUID.randomUUID();
        UUID policyId = service.createQueuePolicy(cmd(productId, "ACTIVE", QueuePolicyStatus.RUNNING), 1L)
            .policyId();

        Map<String, Object> payload = Map.of(
            "timeDealId", UUID.randomUUID().toString(),
            "productId", productId.toString(),
            "endAt", "2026-05-12T12:00:00Z"
        );

        // 컨슈머 구독이 완료되기 전 발행되면 latest offset 모드에서 메시지가 손실됨.
        // 컨슈머가 받을 때까지 주기적으로 재전송.
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
            kafkaTemplate.send("time-deal-end", productId.toString(), payload);
            QueuePolicy after = jpaRepository.findById(policyId).orElseThrow();
            assertThat(after.getStatus()).isEqualTo(QueuePolicyStatus.STOPPED);
        });
    }

    @Test
    @DisplayName("deactivateByProductId: 정책이 없는 productId면 조용히 종료한다")
    void deactivate_missingProduct_noop() {
        service.deactivateByProductId(UUID.randomUUID());
        assertThat(jpaRepository.count()).isZero();
    }
}
