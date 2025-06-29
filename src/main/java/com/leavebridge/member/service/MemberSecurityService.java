package com.leavebridge.member.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.leavebridge.member.entitiy.Member;
import com.leavebridge.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class MemberSecurityService implements UserDetailsService {

	private final MemberRepository memberRepository;

	@Override
	// 사용자명으로 비밀번호를 조회하여 리턴하는 메서드
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		Member member = memberRepository.findByLoginId(username).orElseThrow(
			() -> new IllegalArgumentException("사용자를 찾을수 없습니다.")
		);

		return new User(member.getLoginId(), member.getPassword(), member.getGrantedAuthorities());
	}
}