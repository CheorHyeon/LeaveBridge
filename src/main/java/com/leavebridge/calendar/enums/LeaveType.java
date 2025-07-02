package com.leavebridge.calendar.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum LeaveType {
	HOLIDAY("공휴일"),

	FULL_DAY_LEAVE("1일 연차"),

	HALF_DAY_LEAVE("반차"),

	OUTING("외출"),

	SUMMER_VACATION("여름휴가"),

	OTHER_PEOPLE("비회원 연차");

	// TODO : 회의, 병결, 공가 등 추가하여 연차 소진 안되게

	private final String type;
}
