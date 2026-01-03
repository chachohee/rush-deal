package com.rushcrew.auth_service.auth.infrastructure.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "auth.jwt")
@Validated
public record JwtProperties(
    @NotNull
    TokenConfig access,

    @NotNull
    TokenConfig refresh
) {
    public record TokenConfig(
        @NotBlank
        String secret,

        @Positive
        Long expiration
    ) {}
}
