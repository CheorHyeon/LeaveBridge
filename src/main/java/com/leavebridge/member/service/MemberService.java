package com.leavebridge.member.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;
import com.leavebridge.member.dto.LeaveDetailDto;
import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
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
	private final LeaveAndHolidayRepository leaveRepository;

	private static final LocalTime WORK_START = LocalTime.of(8, 0, 0);
	private static final LocalTime WORK_END = LocalTime.of(17, 0, 0);

	/**
	 * 점심시간(12~13) 분 단위 제외용 상수
	 */
	private static final LocalTime LUNCH_START = LocalTime.NOON;        // 12:00
	private static final LocalTime LUNCH_END = LocalTime.of(13, 0);   // 13:00

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
			.mapToDouble(this::calcUsedDays)
			.sum();

		// 남은 일수 계산
		double remaining = TOTAL_ANNUAL_DAYS - usedDays;

		// 상세 DTO 리스트
		List<LeaveDetailDto> detailDtos = leaves.stream()
			.map(leaveAndHoliday -> LeaveDetailDto.of(leaveAndHoliday, calcUsedDays(leaveAndHoliday)))
			.toList();

		return MemberUsedLeavesResponseDto.of(member, TOTAL_ANNUAL_DAYS, usedDays, remaining, detailDtos);
	}

	/**
	 * LeaveAndHoliday → 사용 “일수” 환산 (하루 단위 분할)
	 */
	private double calcUsedDays(LeaveAndHoliday leaveAndHoliday) {

		// 연차 소진 안될 일정이면 사용 시간 0 -> 공휴일, 회의, 공가 등
		if (!leaveAndHoliday.getLeaveType().isDeductible()) {
			return 0.0;
		}

		LocalDateTime start = leaveAndHoliday.getStartDate();
		LocalDateTime end = leaveAndHoliday.getEndDate();

		double totalMinutes = 0;

		// 날짜별 루프 (하루 단위 탐색)
		for (LocalDate d = start.toLocalDate(); !d.isAfter(end.toLocalDate()); d = d.plusDays(1)) {

			// ──➤ ① 주말(토·일) 스킵 ─────────────────
			DayOfWeek dow = d.getDayOfWeek();
			if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
				continue;               // 분/일 계산 없이 다음 날로
			}

			// 해당 날짜의 시작/종료 시각 결정
			// 시작, 종료일이 아니라면 중간에 낀거니까 이건 1일 연차임이 자명 -> 일 시작, 종료 시간으로 세팅
			LocalDateTime dayStart = d.equals(start.toLocalDate())
				? start // 이번 반복이 시작일과 같다면 이 날짜의 연차 시작으로 봄
				: d.atTime(WORK_START); // 이번 반복이 시작일이 아니라면 이 날짜의 08시로 시작

			LocalDateTime dayEnd = d.equals(end.toLocalDate())
				? end // 이번 반복의 종료일이 매개변수 종료일과 같다면 이 날짜의 연차 끝날로봄
				: d.atTime(WORK_END); // 이번 반복이 연차 종료일이 아니라면(중간일 - 당연히 1일 연차니깐 일과 끝나는 시간으로 변경)

			// ③ **“점심만 일정”이면 continue;**  ← 위치 이동
			if (isOnlyLunch(dayStart, dayEnd)) {
				continue;             // 차감 0, 다음 날로
			}

			// 하루 분 단위 근무시간
			long minutes = Duration.between(dayStart, dayEnd).toMinutes();

			// 점심시간이 겹치면 60분 차감
			if (isLunchIncluded(dayStart.toLocalTime(), dayEnd.toLocalTime())) {
				minutes -= 60;
			}

			// 00시 ~ 23:59:59 까지의 일정이면 1일 연차니까 8시간으로 변환
			// 당일 연차일 경우 중 00시 ~ 23:59:59로 등록된거 있을 수 있기에 처리
			if (minutes >= 480) {
				minutes = 480;
			}

			totalMinutes += minutes;
		}

		// 8시간 = 480분 → 1일
		return (totalMinutes / 60.0) / 8.0;
	}

	/**
	 * 12:00~13:00 구간이 포함되는지
	 */
	private boolean isLunchIncluded(LocalTime st, LocalTime en) {
		return !st.isAfter(LUNCH_START)   //  st ≤ 12:00
			   && !en.isBefore(LUNCH_END);   //  en ≥ 13:00
	}

	/**
	 * 12:00 ~ 13:00 구간 전부만 포함한 일정인지 여부
	 */
	private boolean isOnlyLunch(LocalDateTime start, LocalDateTime end) {
		return !start.toLocalTime().isBefore(LUNCH_START)   // start ≥ 12:00
			   && !end.toLocalTime().isAfter(LUNCH_END);       // end   ≤ 13:00
	}

}
