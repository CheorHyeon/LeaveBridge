package com.leavebridge.calendar.scheduler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class CalendarScheduler {

	private final Calendar calendarClient;
	private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
	private final LeaveAndHolidayRepository leaveAndHolidayRepository;

	@Value("${google.calendar-id}")
	private String GOOGLE_PERSONAL_CALENDAR_ID;

	private final static String GOOGLE_KOREA_HOLIDAY_CALENDAR_ID = "ko.south_korea#holiday@group.v.calendar.google.com";

	// @Scheduled(cron = "0 */1 * * * *") //1분마다 적용 확인을 위해 일단 달아둠
	@Scheduled(cron = "0 0 0 * * *")
	@Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
	public void getLeaveSchduleRegularly() throws IOException {

		LocalDate today = LocalDate.now();
		int year = today.getYear();
		int month = today.getMonthValue();

		log.info("getLeaveSchduleRegularly {}", year + "-" + month);

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

		List<Event> items = events.getItems();

		processAndSaveNewEvents(items, 0L, LeaveType.OTHER_PEOPLE);
	}

	// @Scheduled(cron = "0 */1 * * * *") //1분마다 적용 확인을 위해 일단 달아둠
	@Scheduled(cron = "0 0 4 1 * *")
	@Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
	public void getHolidaysRegularly() throws IOException {

		log.info("getHolidaysRegularly :: {}", LocalDateTime.now());

		Events holidaysEvents = calendarClient.events().list(GOOGLE_KOREA_HOLIDAY_CALENDAR_ID).execute();

		List<Event> items = holidaysEvents.getItems();

		processAndSaveNewEvents(items, 0L, LeaveType.HOLIDAY);
	}

	private void processAndSaveNewEvents(List<Event> events, Long userId, LeaveType type) {
		if (events.isEmpty()) {
			return;
		}

		// 1) 이벤트 ID 추출
		List<String> eventIds = extractEventIds(events);

		// 2) DB에 이미 있는 ID 조회
		List<String> existingIds = findExistingEventIds(eventIds);

		// 3) 신규 이벤트만 매핑 후 저장
		List<LeaveAndHoliday> toSave = events.stream()
			.filter(e -> !existingIds.contains(e.getId()))
			.map(e -> LeaveAndHoliday.of(e, userId, type))
			.sorted(Comparator.comparing(LeaveAndHoliday::getStartDate))
			.toList();

		leaveAndHolidayRepository.saveAll(toSave);
	}

	/**
	 * 이벤트 리스트에서 Google Event ID만 추출
	 */
	private List<String> extractEventIds(List<Event> events) {
		return events.stream()
			.map(Event::getId)
			.toList();
	}

	/**
	 * DB에 이미 저장된 Google Event ID 목록 조회
	 */
	private List<String> findExistingEventIds(List<String> eventIds) {
		return leaveAndHolidayRepository
			.findAllByGoogleEventIdIn(eventIds)
			.stream()
			.map(LeaveAndHoliday::getGoogleEventId)
			.toList();
	}
}
