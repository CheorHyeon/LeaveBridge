package com.leavebridge.calendar.scheduler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;
import com.leavebridge.calendar.service.ExternalEventSyncService;
import com.leavebridge.member.entitiy.Member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Slf4j
@Service
public class CalendarScheduler {

	private final Calendar calendarClient;
	private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
	private final LeaveAndHolidayRepository leaveAndHolidayRepository;
	private final ExternalEventSyncService externalEventSyncService;

	@Value("${google.calendar-id}")
	private String GOOGLE_PERSONAL_CALENDAR_ID;

	public static Member adminMember = Member.builder().id(4L).build();

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

		externalEventSyncService.processAndSaveNewEvents(items);
	}

	// @Scheduled(cron = "0 */1 * * * *") //1분마다 적용 확인을 위해 일단 달아둠
	@Scheduled(cron = "0 0 4 1 * *") // 매달 1일 새벽 4시 실행
	@Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
	public void syncHolidaysMonthly() throws IOException {
		log.info("syncHolidaysMonthly :: {}", LocalDateTime.now());
		List<LeaveAndHoliday> sortedNewLeaveAndHolidayEntities = externalEventSyncService.syncNextYears(2);
		leaveAndHolidayRepository.saveAll(sortedNewLeaveAndHolidayEntities);
	}

}
