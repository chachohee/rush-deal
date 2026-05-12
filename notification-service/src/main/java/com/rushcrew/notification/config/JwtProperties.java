package com.rushcrew.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    AccessToken access
) {
    public record AccessToken(String secret) {}
}
