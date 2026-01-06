package com.rushcrew.queue.infrastructure.config;

import com.rushcrew.queue.infrastructure.filter.AuthorizationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
	private final AuthorizationFilter authorizationFilter;

	public SecurityConfig(AuthorizationFilter authorizationFilter) {
		this.authorizationFilter = authorizationFilter;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.sessionManagement(session ->
				session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.logout(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/**").permitAll()
				// ⚠️ 수정: @PreAuthorize에서 권한 체크하도록 permitAll()로 변경
				.anyRequest().permitAll()
			)
			.addFilterBefore(authorizationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
