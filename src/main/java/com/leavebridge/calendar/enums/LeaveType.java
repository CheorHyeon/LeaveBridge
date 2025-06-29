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

	SUMMER_VACATION("여름휴가");

	private final String type;
}
