package com.leavebridge.calendar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum LeaveType {
	PUBLIC_HOLIDAY("공휴일", false),

	NATIONAL_HOLIDAY("국경일", false),

	TWENTY_FOUR_SOLAR_TERMS("24절기", false),

	SUNDRY_DAY("잡절", false),

	ANNIVERSARY("기념일", false),

	FULL_DAY_LEAVE("1일 연차", true),

	HALF_DAY_MORNING ("오전 반차", true),

	HALF_DAY_AFTERNOON("오후 반차", true),

	OUTING("외출", true),

	SUMMER_VACATION("여름휴가", true),

	OTHER_PEOPLE("비회원 연차", false),

	NON_DEDUCTIBLE("비차감 휴가", false),  // 공결, 병가, 기타 소진 안될 휴가

	MEETING("회의", false),;

	private final String type;
	private final boolean isDeductible;  // true면 연차 소진
}
