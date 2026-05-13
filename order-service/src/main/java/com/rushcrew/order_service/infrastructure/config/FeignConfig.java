package com.rushcrew.order_service.infrastructure.config;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FeignConfig {

	private static final String[] PROPAGATED_HEADERS = {
		"X-User-Id", "X-User-Role", "X-User-Email", "X-Queue-Token"
	};

	/**
	 * 게이트웨이가 주입한 사용자 헤더를 다운스트림 Feign 호출에 전파.
	 * 없으면 timedeal-service / payment-service 의 @PreAuthorize 가 403 처리.
	 */
	@Bean
	public RequestInterceptor headerForwardingInterceptor() {
		return template -> {
			ServletRequestAttributes attrs =
				(ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
			if (attrs == null) return;
			HttpServletRequest req = attrs.getRequest();
			for (String h : PROPAGATED_HEADERS) {
				String v = req.getHeader(h);
				if (v != null && !v.isBlank()) {
					template.header(h, v);
				}
			}
		};
	}

	@Bean
	public Logger.Level feignLoggerLevel() {
		return Logger.Level.BASIC;
	}

	@Bean
	public Request.Options requestOptions() {
		return new Request.Options(
			5, TimeUnit.SECONDS,   // connect timeout
			10, TimeUnit.SECONDS,  // read timeout
			true                  // follow redirects
		);
	}

	@Bean
	public Retryer retryer() {
		return new Retryer.Default(
			1000,   // period (ms)
			2000,   // maxPeriod (ms)
			3       // maxAttempts
		);
	}

	/**
	 * Feign 에러 디코더
	 * - 포인트 서비스 등 외부 서비스 호출 시 에러 핸들링
	 */
	@Bean
	public ErrorDecoder errorDecoder() {
		return (methodKey, response) -> {
			log.error("Feign 호출 실패: method={}, status={}, reason={}",
				methodKey, response.status(), response.reason());

			switch (response.status()) {
				case 400:
					// 잔액 부족, 유효성 검증 실패 등
					return new IllegalArgumentException(
						"요청이 유효하지 않습니다: " + response.reason()
					);
				case 404:
					// 리소스 없음
					return new IllegalArgumentException(
						"리소스를 찾을 수 없습니다: " + response.reason()
					);
				case 409:
					// 중복 요청 등
					return new IllegalStateException(
						"요청 처리 중 충돌이 발생했습니다: " + response.reason()
					);
				case 500:
					// 서버 내부 오류
					return new RuntimeException(
						"서비스 내부 오류가 발생했습니다"
					);
				case 503:
					// 서비스 이용 불가
					return new RuntimeException(
						"서비스를 일시적으로 사용할 수 없습니다"
					);
				default:
					return new RuntimeException(
						"알 수 없는 오류가 발생했습니다: " + response.status()
					);
			}
		};
	}
}
