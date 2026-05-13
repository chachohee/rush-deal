package com.rushcrew.api_gateway.filter;

import com.rushcrew.api_gateway.blacklist.BlacklistService;
import com.rushcrew.api_gateway.config.GatewayProperties;
import com.rushcrew.api_gateway.exception.GatewayErrorCode;
import com.rushcrew.api_gateway.jwt.JwtDecoder;
import com.rushcrew.api_gateway.jwt.RequestTokenExtractor;
import com.rushcrew.api_gateway.model.UserInfo;
import com.rushcrew.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtDecoder jwtDecoder;
    private final BlacklistService blacklistService;
    private final GatewayProperties gatewayProperties;

    private static final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    public Mono<Void> filter(
        ServerWebExchange exchange,
        GatewayFilterChain chain
    ) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod() != null
            ? exchange.getRequest().getMethod().name() : "";

        if (isPublic(method, path)) {
            return chain.filter(exchange);
        }

        Optional<String> extracted = RequestTokenExtractor.extractAccessToken(
            exchange.getRequest()
        );

        if (extracted.isEmpty()) {
            return Mono.error(new BusinessException(GatewayErrorCode.TOKEN_MISSING));
        }

        String accessToken = extracted.get();

        return validateToken(exchange, chain, accessToken);
    }

    // TOKEN 검증
    private Mono<Void> validateToken(
        ServerWebExchange exchange,
        GatewayFilterChain chain,
        String accessToken
    ) {
        return Mono.fromCallable(() ->
            jwtDecoder.validateAndGetClaims(accessToken)
        )
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(ExpiredJwtException.class, e ->
                Mono.error(new BusinessException(GatewayErrorCode.TOKEN_EXPIRED))
            )
            .onErrorResume(SignatureException.class, e ->
                Mono.error(
                    new BusinessException(GatewayErrorCode.TOKEN_SIGNATURE_INVALID)
                )
            )
            .onErrorResume(MalformedJwtException.class, e ->
                Mono.error(new BusinessException(GatewayErrorCode.TOKEN_INVALID))
            )
            .flatMap(claims ->
                processClaims(exchange, chain, accessToken, claims)
            );
    }

    // BLACKLIST 체크
    private Mono<Void> processClaims(
        ServerWebExchange exchange,
        GatewayFilterChain chain,
        String accessToken,
        Claims claims
    ) {
        return validateClaims(exchange, claims)
            .flatMap(userInfo ->
                validateBlacklist(exchange, accessToken, userInfo)
            )
            .flatMap(userInfo ->
                forwardRequestWithHeaders(exchange, chain, userInfo)
            );
    }

    // Claim 필수값 검증
    private Mono<UserInfo> validateClaims(
        ServerWebExchange exchange,
        Claims claims
    ) {
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        if (userId == null || email == null || role == null) {
            return Mono.error(new BusinessException(GatewayErrorCode.TOKEN_INVALID));
        }

        return Mono.just(new UserInfo(userId, email, role));
    }

    // 블랙리스트 검증
    private Mono<UserInfo> validateBlacklist(
        ServerWebExchange exchange,
        String accessToken,
        UserInfo info
    ) {
        return blacklistService.isTokenBlacklisted(accessToken)
            .flatMap(isTokenBlacklisted -> {
                if (Boolean.TRUE.equals(isTokenBlacklisted)) {
                    return Mono.error(
                        new BusinessException(GatewayErrorCode.TOKEN_BLACKLISTED)
                    );
                }
                return blacklistService.isUserBlacklisted(
                    Long.valueOf(info.userId())
                );
            })
            .flatMap(isUserBlacklisted -> {
                if (Boolean.TRUE.equals(isUserBlacklisted)) {
                    return Mono.error(
                        new BusinessException(GatewayErrorCode.TOKEN_BLACKLISTED)
                    );
                }
                return Mono.just(info);
            })
            .onErrorResume(e -> {
                // Redis 연결 실패 등의 에러는 블랙리스트 검증을 스킵하고 진행
                if (!(e instanceof BusinessException)) {
                    return Mono.just(info);
                }
                return Mono.error(e);
            });
    }

    // Header 주입 후 요청 전달
    private Mono<Void> forwardRequestWithHeaders(
        ServerWebExchange exchange,
        GatewayFilterChain chain,
        UserInfo userInfo
    ) {
        ServerHttpRequest authorizedRequest = exchange
            .getRequest()
            .mutate()
            .header(USER_ID_HEADER, userInfo.userId())
            .header(
                USER_EMAIL_HEADER,
                URLEncoder.encode(userInfo.email(), StandardCharsets.UTF_8)
            )
            .header(USER_ROLE_HEADER, userInfo.role())
            .build();

        return chain.filter(
            exchange.mutate().request(authorizedRequest).build()
        );
    }

    private boolean isPublic(String method, String path) {
        return gatewayProperties
            .publicPaths()
            .stream()
            .anyMatch(rule -> matches(rule, method, path));
    }

    // 룰 포맷:
    //   "/api/v1/foo"        → 모든 메서드 매칭
    //   "GET:/api/v1/foo"    → GET 만 매칭 (콤마 구분으로 여러 메서드도 허용: "GET,HEAD:/path")
    private boolean matches(String rule, String method, String path) {
        int colon = rule.indexOf(':');
        if (colon < 0) {
            return pathMatcher.match(rule, path);
        }
        String methodsPart = rule.substring(0, colon);
        String pattern = rule.substring(colon + 1);
        for (String m : methodsPart.split(",")) {
            if (m.trim().equalsIgnoreCase(method)) {
                return pathMatcher.match(pattern, path);
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
