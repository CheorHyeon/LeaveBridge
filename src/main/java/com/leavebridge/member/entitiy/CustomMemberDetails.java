package com.leavebridge.member.entitiy;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class CustomMemberDetails implements UserDetails {

	// 현재 인증된 Member 정보 저장
	private final Member member;

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return member.getGrantedAuthorities();
	}

	@Override
	public String getPassword() {
		return member.getPassword();
	}

	@Override
	public String getUsername() {
		return member.getLoginId();
	}
}
