package com.leavebridge.calendar.service;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarService {
	private final Calendar calendarClient;
	private final static String GOOGLE_KOREA_HOLIDAY_CALENDAR_ID = "ko.south_korea#holiday@group.v.calendar.google.com";

	@Value("${google.calendar-id}")
	private String GOOGLE_PERSONAL_CALENDAR_ID;

	/**
	 * 공휴일 데이터를 가져오는 API
	 */
	public Events findHolidayFromGoogleCalendar() throws IOException {
		return calendarClient.events().list(GOOGLE_KOREA_HOLIDAY_CALENDAR_ID).execute();
	}

	/**
	 * 지정된 calendarId의, year년 month월 전체 이벤트 조회
	 */
	public List<Event> listMonthlyEvents(int year, int month) throws Exception {
		log.info("GOOGLE_PERSONAL_CALENDAR_ID ::{}", GOOGLE_PERSONAL_CALENDAR_ID);
		// 월 시작: yyyy-MM-01T00:00:00Z
		DateTime timeMin = new DateTime(String.format("%04d-%02d-01T00:00:00Z", year, month));
		// 1) YearMonth로 말일(LocalDate) 구하기
		YearMonth ym = YearMonth.of(year, month);
		LocalDate lastDayDate = ym.atEndOfMonth();

		// 2) 말일 자정(UTC)을 포함한 ISO 8601 문자열 생성
		String timeMaxIso = String.format("%sT23:59:59Z", lastDayDate);

		// 3) DateTime 객체로 변환
		DateTime timeMax = new DateTime(timeMaxIso);

		Events events = calendarClient.events().list(GOOGLE_PERSONAL_CALENDAR_ID)
			.setTimeMin(timeMin)
			.setTimeMax(timeMax)
			.setSingleEvents(true)    // 반복 이벤트를 각 회차별로 개별 인스턴스로 분할해 반환
			.setOrderBy("startTime")
			.execute();

		return events.getItems();
	}
}