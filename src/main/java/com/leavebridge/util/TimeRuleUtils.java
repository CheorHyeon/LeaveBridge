package com.leavebridge.util;

import static com.leavebridge.calendar.entity.LeaveAndHoliday.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class TimeRuleUtils {

	/**
	 * 파견직 여부에 따라 근무시간, 오후연차 시작 시간, 근무 종료 시간을 반환합니다.
	 */
	public static LocalTime getAdjustStartTime(boolean isGermany) {
		return isGermany ? WORK_START_TIME_FOR_GERMANY : WORK_START_TIME_FOR_MEMBER;
	}

	public static LocalTime getAdjustLunchBoundaryTime(boolean isGermany) {
		return isGermany ? LUNCH_BOUNDARY_TIME_FOR_GERMANY : LUNCH_BOUNDARY_TIME_FOR_MEMBER;
	}

	public static LocalTime getAdjustEndTime(boolean isGermany) {
		return isGermany ? WORK_END_TIME_FOR_GERMANY : WORK_END_TIME_FOR_MEMBER;
	}

	/**
	 * 12:00~13:00 구간이 포함되는지
	 */
	public static boolean isLunchIncluded(LocalTime st, LocalTime en) {
		return !st.isAfter(LUNCH_START)   //  st ≤ 12:00
			   && !en.isBefore(LUNCH_END);   //  en ≥ 13:00
	}

	/**
	 * 12:00 ~ 13:00 구간 전부만 포함한 일정인지 여부
	 */
	public static boolean isOnlyLunch(LocalDateTime start, LocalDateTime end) {
		return !start.toLocalTime().isBefore(LUNCH_START)   // start ≥ 12:00
			   && !end.toLocalTime().isAfter(LUNCH_END);       // end   ≤ 13:00
	}

}
