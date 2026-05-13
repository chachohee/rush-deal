package com.rushcrew.order_service;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES;
    static final KafkaContainer KAFKA;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("rushdeal").withUsername("rushdeal").withPassword("rushdeal")
            .withInitScript("init-schemas.sql");
        KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
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
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("eureka.client.enabled", () -> "false");
        r.add("spring.flyway.create-schemas", () -> "true");
        r.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        r.add("spring.batch.jdbc.initialize-schema", () -> "always");
    }
}
