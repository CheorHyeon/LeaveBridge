package com.leavebridge.calendar.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalendarService {
	private final Calendar calendarClient;
	private final static String GOOGLE_KOREA_HOLIDAY_CALENDAR_ID = "ko.south_korea#holiday@group.v.calendar.google.com";
	private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
	private final LeaveAndHolidayRepository leaveAndHolidayRepository;

	@Value("${google.calendar-id}")
	private String GOOGLE_PERSONAL_CALENDAR_ID;

	/**
	 * 공휴일 데이터를 가져오는 API
	 */
	@Transactional
	public void findHolidayFromGoogleCalendar() throws IOException {
		// 1) 올해 연도 구하기
		int currentYear = LocalDate.now().getYear();
		LocalDateTime startOfYear = LocalDate.of(currentYear, 1, 1).atStartOfDay();
		LocalDateTime endOfYear = LocalDate.of(currentYear, 12, 31).atTime(LocalTime.MAX);

		// 2) 이미 올해 공휴일이 저장되어 있으면 바로 예외
		boolean exists = leaveAndHolidayRepository.existsByLeaveTypeAndStartDateBetween(LeaveType.HOLIDAY, startOfYear,
			endOfYear);
		if (exists) {
			throw new IllegalStateException("이미 해당 년도의 공휴일이 모두 로드되어 가져오지 않습니다.");
		}

		Events holidaysEvents = calendarClient.events().list(GOOGLE_KOREA_HOLIDAY_CALENDAR_ID).execute();

		List<LeaveAndHoliday> list = holidaysEvents.getItems().stream()
			.map(item -> LeaveAndHoliday.of(item, 0L, LeaveType.HOLIDAY))
			.toList();

		leaveAndHolidayRepository.saveAll(list);

	}

	/**
	 * 특정 이벤트의 상세 정보를 조회합니다.
	 */
	public Event getEventDetails(String eventId) throws IOException {
		try {
			return calendarClient.events()
				.get(GOOGLE_PERSONAL_CALENDAR_ID, eventId)
				.execute();
		} catch (GoogleJsonResponseException e) {
			// 404 Not Found 처리
			if (e.getStatusCode() == 404) {
				log.warn("이벤트를 찾을 수 없습니다. eventId={}", eventId);
				return null;
			}
			// 그 외 오류는 다시 던집니다.
			throw new RuntimeException(e.getMessage());
		}
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

	/**
	 * 지정된 calendarId에 연차를 등록합니다.
	 */
	public Event createTimedEvent(String summary, LocalDateTime startDateTime, LocalDateTime endDateTime) throws
		IOException {
		// 1) Event 객체 생성 및 기본 정보 설정
		Event event = new Event()
			.setSummary(summary);

		// 2) 시작 시각 설정
		DateTime startDT = new DateTime(Date.from(startDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
		EventDateTime start = new EventDateTime()
			.setDateTime(startDT);
		event.setStart(start);

		// 3) 종료 시각 설정
		DateTime endDT = new DateTime(Date.from(endDateTime.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
		EventDateTime end = new EventDateTime()
			.setDateTime(endDT);
		event.setEnd(end);

		// 4) 이벤트 등록
		return calendarClient.events()
			.insert(GOOGLE_PERSONAL_CALENDAR_ID, event)
			.execute();
	}

	/**
	 * 기존 이벤트(eventId)의 시작/종료 시각을 업데이트합니다.
	 */
	public Event updateEventDate(
		String eventId,
		LocalDateTime newStart,
		LocalDateTime newEnd
	) throws IOException {
		log.info("new Start :: {}", newStart);
		// 1) 먼저 기존 이벤트를 가져옵니다.
		Event existing = calendarClient.events()
			.get(GOOGLE_PERSONAL_CALENDAR_ID, eventId)
			.execute();

		// 2) 시작/종료 시각을 새 값으로 설정
		DateTime startDt = new DateTime(Date.from(newStart.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
		DateTime endDt = new DateTime(Date.from(newEnd.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));

		existing.setStart(new EventDateTime()
			.setDateTime(startDt));
		existing.setEnd(new EventDateTime()
			.setDateTime(endDt));

		// 3) patch 호출
		try {
			return calendarClient.events()
				.patch(GOOGLE_PERSONAL_CALENDAR_ID, eventId, existing)
				.execute();
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				log.warn("업데이트 도중 이벤트를 찾을 수 없습니다. eventId={}", eventId);
			}
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * 지정한 이벤트(eventId)를 삭제합니다.
	 */
	public void deleteEvent(String eventId) throws IOException {
		try {
			calendarClient.events()
				.delete(GOOGLE_PERSONAL_CALENDAR_ID, eventId)
				.execute();
		} catch (GoogleJsonResponseException e) {
			switch (e.getStatusCode()) {
				case 404 -> log.warn("삭제할 이벤트를 찾을 수 없습니다. eventId={}", eventId);
				case 410 -> log.warn("이미 삭제된 이벤트입니다. eventId={}", eventId);
			}
			throw new RuntimeException(e.getMessage());
		}
	}
}