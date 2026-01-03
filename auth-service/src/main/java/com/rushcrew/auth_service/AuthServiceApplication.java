package com.rushcrew.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

import com.rushcrew.auth_service.auth.infrastructure.properties.JwtProperties;

@EnableFeignClients
@EnableConfigurationProperties(JwtProperties.class)
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = {
    "com.rushcrew.auth_service",
    "com.rushcrew.common.exception"
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
