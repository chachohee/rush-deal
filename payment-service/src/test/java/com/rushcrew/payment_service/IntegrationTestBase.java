package com.rushcrew.payment_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES;
    static final KafkaContainer KAFKA;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("rushdeal").withUsername("rushdeal").withPassword("rushdeal")
            .withInitScript("init-schemas.sql");
        KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
        POSTGRES.start();
        KAFKA.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("spring.jpa.properties.hibernate.dialect",
            () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("eureka.client.enabled", () -> "false");
        r.add("spring.flyway.create-schemas", () -> "true");
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        // PortOne 환경 변수 (테스트용 더미)
        r.add("portone.secret.api", () -> "test-api-secret");
        // WebhookVerifier가 base64 디코딩하므로 base64 문자만 사용
        r.add("portone.secret.webhook", () -> "dGVzdHdlYmhvb2tzZWNyZXQxMjM0NTY3OA==");
        r.add("portone.secret.store-id", () -> "test-store");
        r.add("portone.secret.channel-key", () -> "test-channel");
    }
}
