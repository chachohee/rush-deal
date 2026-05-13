package com.rushcrew.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushcrew.user_service.audit.application.AdminAuditLogService;
import com.rushcrew.user_service.audit.domain.AdminAction;
import com.rushcrew.user_service.audit.domain.AdminAuditLogRepository;
import com.rushcrew.user_service.audit.presentation.AdminAuditLogResponse;
import com.rushcrew.user_service.user.application.UserService;
import com.rushcrew.user_service.user.application.command.UserCreateCommand;
import com.rushcrew.user_service.user.domain.entity.User;
import com.rushcrew.user_service.user.infrastructure.repository.UserJpaRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Import(AdminActionIntegrationTest.AccountEventCapture.class)
class AdminActionIntegrationTest extends IntegrationTestBase {

    @Autowired UserService userService;
    @Autowired AdminAuditLogService auditLogService;
    @Autowired AdminAuditLogRepository auditLogRepo;
    @Autowired UserJpaRepository userRepo;
    @Autowired AccountEventCapture capture;

    @AfterEach
    void clean() {
        capture.clear();
        auditLogRepo.deleteAll();
        userRepo.deleteAll();
    }

    private Long createUser(String email, String role) {
        return userService.createUser(new UserCreateCommand(email, "Pass1234!", "name-" + email, role))
            .userId();
    }

    @Test
    @DisplayName("blockUser는 유저 상태 변경 + audit log 저장 + Kafka 이벤트 발행을 모두 수행한다")
    void blockUser_persistsAuditAndPublishesEvent() {
        Long adminId = createUser("admin1@test.com", "MASTER");
        Long targetId = createUser("victim1@test.com", "USER");

        userService.blockUser(targetId, adminId);

        User after = userRepo.findById(targetId).orElseThrow();
        assertThat(after.isBlocked()).isTrue();

        Page<AdminAuditLogResponse> logs = auditLogService.list(null, PageRequest.of(0, 10));
        assertThat(logs.getContent())
            .anySatisfy(log -> {
                assertThat(log.action()).isEqualTo(AdminAction.BLOCKED);
                assertThat(log.targetUserId()).isEqualTo(targetId);
                assertThat(log.adminId()).isEqualTo(adminId);
                assertThat(log.targetEmail()).isEqualTo("victim1@test.com");
            });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(capture.events()).anySatisfy(node ->
                assertThat(node.get("type").asText()).isEqualTo("ACCOUNT_BLOCKED")));
    }

    @Test
    @DisplayName("changeRole은 이전 역할→새 역할을 audit log details에 기록하고 ROLE_CHANGED 이벤트를 발행한다")
    void changeRole_recordsTransitionAndPublishes() {
        Long adminId = createUser("admin2@test.com", "MASTER");
        Long targetId = createUser("user2@test.com", "USER");

        userService.changeRole(targetId, "SELLER", adminId);

        assertThat(userRepo.findById(targetId).orElseThrow().getRole().name()).isEqualTo("SELLER");

        Page<AdminAuditLogResponse> logs = auditLogService.list(AdminAction.ROLE_CHANGED,
            PageRequest.of(0, 10));
        assertThat(logs.getContent()).anySatisfy(log -> {
            assertThat(log.targetUserId()).isEqualTo(targetId);
            assertThat(log.details()).isEqualTo("USER → SELLER");
        });

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(capture.events()).anySatisfy(node -> {
                assertThat(node.get("type").asText()).isEqualTo("ROLE_CHANGED");
                assertThat(node.get("newRole").asText()).isEqualTo("SELLER");
            }));
    }

    @Test
    @DisplayName("audit log 응답에 관리자/대상 이메일이 enrichment 되어 반환된다")
    void auditLog_enrichesEmails() {
        Long adminId = createUser("admin3@test.com", "MASTER");
        Long t1 = createUser("a@test.com", "USER");
        Long t2 = createUser("b@test.com", "USER");

        userService.blockUser(t1, adminId);
        userService.unblockUser(t1, adminId);
        userService.changeRole(t2, "SELLER", adminId);

        Page<AdminAuditLogResponse> logs = auditLogService.list(null, PageRequest.of(0, 10));
        assertThat(logs.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(logs.getContent())
            .allSatisfy(log -> assertThat(log.adminEmail()).isEqualTo("admin3@test.com"))
            .anySatisfy(log -> assertThat(log.targetEmail()).isEqualTo("a@test.com"))
            .anySatisfy(log -> assertThat(log.targetEmail()).isEqualTo("b@test.com"));
    }

    @Component
    static class AccountEventCapture {
        private final List<JsonNode> events = new CopyOnWriteArrayList<>();
        private final ObjectMapper mapper = new ObjectMapper();

        @KafkaListener(topics = "user.account.event", groupId = "test-capture")
        public void onEvent(String message) throws Exception {
            events.add(mapper.readTree(message));
        }

        public List<JsonNode> events() { return events; }
        public void clear() { events.clear(); }
    }
}
