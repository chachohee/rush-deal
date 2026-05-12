package com.rushcrew.notification.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtDecoder {

    private final JwtProperties jwtProperties;

    private SecretKey accessSecretKey;

    @PostConstruct
    public void init() {
        this.accessSecretKey = Keys.hmacShaKeyFor(
            jwtProperties.access().secret().getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(accessSecretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
