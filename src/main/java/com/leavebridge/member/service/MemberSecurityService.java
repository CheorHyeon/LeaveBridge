package com.leavebridge.member.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.leavebridge.member.entitiy.CustomMemberDetails;
import com.leavebridge.member.entitiy.Member;
import com.leavebridge.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class MemberSecurityService implements UserDetailsService {

	private final MemberRepository memberRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		log.info("loadUserByUsername :: {}", username);
		Member member = memberRepository.findByLoginId(username).orElseThrow(
			() -> new IllegalArgumentException("사용자를 찾을수 없습니다.")
		);
		// Custom에서 권한, 비밀번호 넘겨줌 -> DaoAuthenticationProvider가 인증 하도록
		return new CustomMemberDetails(member);
	}
}