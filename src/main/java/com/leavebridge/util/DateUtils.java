package com.leavebridge.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

public class DateUtils {

	/**
	 * 구글 캘린더의 EventDateTime을 LocalDateTime으로 변환합니다.
	 *
	 * @param dt      구글 캘린더 EventDateTime
	 * @param isStart true면 시작 시각, false면 종료 시각
	 * @return 변환된 LocalDateTime
	 */
	public static LocalDateTime parseDateTime(EventDateTime dt, boolean isStart) {
		// 1) 시간이 명시된 경우
		if (dt.getDateTime() != null) {
			return convertToLocalDateTime(dt.getDateTime().getValue());
		}
		// 2) all-day 이벤트인 경우 date 필드만 채워짐
		LocalDate date = LocalDate.parse(dt.getDate().toStringRfc3339());
		// 시작이면 자정, 종료면 다음 날 자정(배타적 end) 또는 당일 23:59:59
		return isStart
			? date.atStartOfDay()
			: date.atTime(LocalTime.MAX);
	}

	/**
	 * 구글 캘린더 Event 객체를 통해 하루종일 이벤트인지 시간 정해진 이벤트인지 확인
	 */
	public static boolean determineAllDay(Event event) {
		return event.getStart().getDateTime() == null;
	}

	/**
	 * 구글 캘린더의 시간을 받아서 LocalDateTime으로 변경합니다.
	 */
	private static LocalDateTime convertToLocalDateTime(long millis) {
		return LocalDateTime.ofInstant(
			java.time.Instant.ofEpochMilli(millis),
			ZoneId.of("Asia/Seoul")
		);
	}
}