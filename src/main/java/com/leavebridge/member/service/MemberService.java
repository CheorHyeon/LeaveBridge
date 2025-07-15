package com.leavebridge.member.service;

import java.util.List;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.member.dto.LeaveDetailDto;
import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
import com.leavebridge.member.dto.RequestChangePasswordRequest;
import com.leavebridge.member.entitiy.Member;
import com.leavebridge.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 연차 총 일수 - 만일 나중에 실제 서비스 이용 시 규정 상 지급되는 일수를 개인별로 다르게
	 * ex) 입사 1년 미만은 12, 1년차 되는날 15개로 초기화 및 잔여 연차 초기화 등
	 * 사실상 잔여 연차 등을 DB에 저장하는것이 좋겠으나, 편의를 위해 별도 테이블 저장 안함
	 */
	private static final double TOTAL_ANNUAL_DAYS = 12.0;

	/**
	 * 모든 회원의 연차 사용 현황 조회
	 */
	public List<MemberUsedLeavesResponseDto> getMemberUsedLeaves() {

		// ① 회원 전체 조회 - 1차 캐시에 전부 넣기용
		List<Member> members = memberRepository.findAllWithLeaves();

		return members.stream()
			.map(this::buildMemberDto)
			.toList();
	}

	/**
	 * 개별 회원에 대한 DTO 빌드
	 */
	private MemberUsedLeavesResponseDto buildMemberDto(Member member) {

		// ② 회원별 LeaveAndHoliday 조회 - 1차 캐시 데이터 써서 쿼리 안나감
		List<LeaveAndHoliday> leaves = member.getLeaveAndHolidays();

		// ③ 일정별 사용 시간(분) → 총 사용 일수 계산
		double usedDays = leaves.stream()
			.mapToDouble(LeaveAndHoliday::getUsedLeaveHours)
			.filter(Objects::nonNull)
			.sum();

		// 남은 일수 계산
		double remaining = TOTAL_ANNUAL_DAYS - usedDays;

		// 상세 DTO 리스트
		List<LeaveDetailDto> detailDtos = leaves.stream()
			.map(leaveAndHoliday -> LeaveDetailDto.of(leaveAndHoliday, usedDays))
			.toList();

		return MemberUsedLeavesResponseDto.of(member, TOTAL_ANNUAL_DAYS, usedDays, remaining, detailDtos);
	}

	@Transactional
	public void changePassword(Member member, RequestChangePasswordRequest request) {
		// 1) 현재 비밀번호 맞는지 확인
		if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
		}

		// 2) 새 비밀번호·확인 일치 확인
		if (!request.newPassword().equals(request.confirmPassword())) {
			throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
		}

		member.updatePassword(passwordEncoder.encode(request.newPassword()));
	}
}
