package com.leavebridge.member.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leavebridge.member.dto.FindMemberListReponseDto;
import com.leavebridge.member.dto.LeaveDetailDto;
import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
import com.leavebridge.member.dto.RequestChangePasswordRequest;
import com.leavebridge.member.dto.SignupRequestDto;
import com.leavebridge.member.entitiy.Member;
import com.leavebridge.member.entitiy.MemberRole;
import com.leavebridge.member.repository.MemberQueryRepository;
import com.leavebridge.member.repository.MemberRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	@Autowired
	private HttpServletRequest request;
	@Autowired
	private HttpServletResponse response;

	private final MemberQueryRepository memberQueryRepository;

	/**
	 * 연차 사용 현황 조회 시 회원 목록 반환
	 */
	public List<FindMemberListReponseDto> findMemberListForUsage() {
		return memberQueryRepository.findAllMembersNotIncludeAdmin();
	}

	/**
	 * 회원의 연차 사용 현황 조회
	 */
	public MemberUsedLeavesResponseDto getMemberUsedLeaves(Long memberId, Integer year, Pageable pageable) {

		// 0. 사용자 조회
		Member member = memberRepository.findById(memberId).orElseThrow(() -> new RuntimeException("존재하지 않는 회원입니다."));

		// 1. 통계 수치 먼저 채움
		MemberUsedLeavesResponseDto fetchMemberStats = memberQueryRepository.fetchMemberStats(member, year);

		// 2. 페이징 요소 채우기(페이지별 사용 요소)
		PagedModel<LeaveDetailDto> leaveDetails = memberQueryRepository.findLeaveDetails(member, year, pageable);

		// 3. Dto Paging 업데이트
		fetchMemberStats.updateLeaveDetails(leaveDetails);

		return fetchMemberStats;
	}

	@Transactional
	public void changePassword(Member member, RequestChangePasswordRequest requestDto) {
		// 1) 현재 비밀번호 맞는지 확인
		if (!passwordEncoder.matches(requestDto.currentPassword(), member.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
		}

		// 2) 새 비밀번호·확인 일치 확인
		if (!requestDto.newPassword().equals(requestDto.confirmPassword())) {
			throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
		}

		// 3) 비밀번호 변경 - 준영속 상태 엔티티 불필요하게 영속화 안하게 하기 위한 JPQL 쿼리 사용
		memberRepository.updatePassword(member.getId(), passwordEncoder.encode(requestDto.newPassword()));

		// 4) 세션 초기화 - SecurityContextLogoutHandler 로 세션/컨텍스트 무효화
		LogoutHandler logoutHandler = new SecurityContextLogoutHandler();
		logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
	}

	public void checkMemberName(String loginId) {
		if (memberRepository.existsByLoginId(loginId)) {
			throw new IllegalArgumentException("이미 존재하는 Id 입니다.");
		}
	}

	@Transactional
	public void signUpMember(SignupRequestDto requestDto) {

		if(!Boolean.TRUE.equals(requestDto.isUsernameAvailable())) {
			throw new IllegalArgumentException("아이디 중복확인은 필수입니다.");
		}

		if(!requestDto.password().equals(requestDto.confirmPassword())) {
			throw new IllegalArgumentException("비밀번호 확인과, 비밀번호가 일치하지 않습니다.");
		}

		Member member = Member.builder()
			.name(requestDto.memberName())
			.memberRole(MemberRole.ROLE_MEMBER)
			.loginId(requestDto.loginId())
			.password(passwordEncoder.encode(requestDto.password()))
			.build();

		memberRepository.save(member);
	}
}
