package com.leavebridge.member.entitiy;

import org.springframework.security.core.GrantedAuthority;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum MemberRole implements GrantedAuthority {

	ROLE_GERMANY("파견직"),
	ROLE_MEMBER("직원"),
	ROLE_ADMIN("관리자");

	private final String description;

	@Override
	public String getAuthority() {
		return this.name();
	}
}
