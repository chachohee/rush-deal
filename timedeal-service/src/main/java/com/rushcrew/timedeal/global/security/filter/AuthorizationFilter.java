package com.rushcrew.timedeal.global.security.filter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.rushcrew.timedeal.global.security.model.UserDetailsImpl;

@Component
public class AuthorizationFilter extends OncePerRequestFilter {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String USER_NAME_HEADER = "X-User-Email";
	private static final String USER_ROLE_HEADER = "X-User-Role";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
		return request.getDispatcherType() != DispatcherType.REQUEST;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws IOException, ServletException {

		String userId = request.getHeader(USER_ID_HEADER);
		String email = request.getHeader(USER_NAME_HEADER);
		String role = request.getHeader(USER_ROLE_HEADER);

		if (userId == null || email == null || role == null) {
			System.out.println("[AuthorizationFilter] 헤더 누락 -> SecurityContext 세팅 안 함");
			System.out.println("X-User-Id=" + userId + ", X-User-Email=" + email + ", X-User-Role=" + role);
			filterChain.doFilter(request, response);
			return;
		}

		System.out.println("[AuthorizationFilter] 헤더 확인 -> SecurityContext 세팅 시도");
		System.out.println("X-User-Id=" + userId + ", X-User-Email=" + email + ", X-User-Role=" + role);

		// ⚠️ Lambda 대신 SimpleGrantedAuthority 사용
		UsernamePasswordAuthenticationToken auth =
			new UsernamePasswordAuthenticationToken(
				new UserDetailsImpl(Long.valueOf(userId), email, role),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
			);

		SecurityContextHolder.getContext().setAuthentication(auth);

		System.out.println("[AuthorizationFilter] SecurityContext 세팅 완료");
		auth.getAuthorities().forEach(a ->
			System.out.println("[AuthorizationFilter] Authority: " + a.getAuthority())
		);

		filterChain.doFilter(request, response);
	}
}
