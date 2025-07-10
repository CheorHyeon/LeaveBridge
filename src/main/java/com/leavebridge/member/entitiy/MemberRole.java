package com.leavebridge.member.entitiy;

import org.springframework.security.core.GrantedAuthority;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MemberRole implements GrantedAuthority {

	ROLE_MEMBER("일반 회원"),
	ROLE_ADMIN("관리자");

	private final String description;

	@Override
	public String getAuthority() {
		return this.name();
	}
}
