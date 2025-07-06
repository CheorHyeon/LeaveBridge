package com.leavebridge.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

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
		// 1) 시간이 명시된 경우 - all day 이벤트가 아님
		if (dt.getDateTime() != null) {
			return convertToLocalDateTime(dt.getDateTime().getValue());
		}
		// 2) all-day 이벤트인 경우 date 필드만 채워짐
		LocalDate date = LocalDate.parse(dt.getDate().toStringRfc3339());
		// 시작이면 자정, 종료면 당일 23:59:59
		return isStart
			? date.atStartOfDay()
			: date.atStartOfDay().minusSeconds(1);  // all-day 이벤트의 경우 다음날 00:00:00 이기 때문에 1초 빼기
	}

	/**
	 * 구글 캘린더 Event 객체를 통해 하루종일 이벤트인지 시간 정해진 이벤트인지 확인
	 */
	public static boolean determineAllDay(Event event) {
		// start.date 가 채워져 있으면 dateOnly:true 인 all-day 이벤트
		//  EventDateTime.getDateTime()이 값이 있으면 시간 지정 이벤트 (all-day가 아님)
		//   EventDateTime.getDate()이 값이 있으면 종일 이벤트 (all-day)
		return event.getStart().getDate() != null;
	}

	/**
	 * 구글 캘린더의 시간을 받아서 LocalDateTime으로 변경합니다.
	 */
	public static LocalDateTime convertToLocalDateTime(long millis) {
		return LocalDateTime.ofInstant(
			java.time.Instant.ofEpochMilli(millis),
			ZoneId.of("Asia/Seoul")
		);
	}

	/**
	 * 주어진 날짜와 시간으로 LocalDateTime 생성.
	 * @param date 날짜 (null 이면 null 반환)
	 * @param time 시간 (null 이면 date.atStartOfDay() 사용)
	 */
	public static LocalDateTime makeLocalDateTimeFromLocalDAteAndLocalTime(LocalDate date, LocalTime time) {
		if (Objects.isNull(date)) {
			throw new IllegalArgumentException("date cannot be null");
		}
		return Objects.isNull(time)
			? date.atStartOfDay()
			: LocalDateTime.of(date, time);
	}
}