package com.rushcrew.queue.infrastructure.filter;

import com.rushcrew.queue.infrastructure.security.UserDetailsImpl;
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

@Component
public class AuthorizationFilter extends OncePerRequestFilter {

	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String USER_NAME_HEADER = "X-User-Email";
	private static final String USER_ROLE_HEADER = "X-User-Role";

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
			filterChain.doFilter(request, response);
			return;
		}

		// ⚠️ 수정: Lambda 대신 SimpleGrantedAuthority 사용
		UsernamePasswordAuthenticationToken auth =
			new UsernamePasswordAuthenticationToken(
				new UserDetailsImpl(Long.valueOf(userId), email, role),
				null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
			);

		SecurityContextHolder.getContext().setAuthentication(auth);
		filterChain.doFilter(request, response);
	}
}
