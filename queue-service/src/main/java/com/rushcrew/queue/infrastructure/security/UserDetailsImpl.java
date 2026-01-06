package com.rushcrew.queue.infrastructure.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserDetailsImpl(Long userId, String email, String role) implements UserDetails {

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		// ⚠️ 수정: Lambda 대신 SimpleGrantedAuthority 사용
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
	}

	@Override public String getPassword() { return ""; }
	@Override public String getUsername() { return email; }
}
