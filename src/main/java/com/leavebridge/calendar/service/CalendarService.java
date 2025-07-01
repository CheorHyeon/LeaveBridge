package com.leavebridge.calendar.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.leavebridge.calendar.dto.CreateLeaveRequestDto;
import com.leavebridge.calendar.dto.MonthlyEvent;
import com.leavebridge.calendar.dto.MonthlyEventDetailResponse;
import com.leavebridge.calendar.dto.PatchLeaveRequestDto;
import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;
import com.leavebridge.util.DateUtils;

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
	 * 특정 이벤트의 상세 정보를 조회합니다.
	 */
	public MonthlyEventDetailResponse getEventDetails(Long eventId) {

		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId).orElseThrow(
			() -> new IllegalArgumentException("존재하지 않는 일정 Id입니다."));

		return MonthlyEventDetailResponse.from(leaveAndHoliday);
	}

	/**
	 * 지정된 calendarId의 설정한 연도, 월에 해당하는 일정 목록 로드
	 */
	public List<MonthlyEvent> listMonthlyEvents(int year, int month) throws Exception {
		log.info("CalendarService.listMonthlyEvents :: year={}, month={}", year, month);

		LocalDateTime startDate = LocalDateTime.of(year, month, 1, 1, 0, 0);
		LocalDateTime endDate = startDate.plusMonths(1);  // 다음 달 1일 00:00

		// 시작일이 지정한 날짜 이상인 것
		List<LeaveAndHoliday> currentMonthEvents = leaveAndHolidayRepository.findAllByStartDateGreaterThanEqualAndStartDateLessThan(
			startDate, endDate);

		return currentMonthEvents.stream()
			.map(MonthlyEvent::from)
			.toList();
	}

	/**
	 * 지정된 calendarId에 연차를 등록합니다.
	 */
	@Transactional
	public void createTimedEvent(CreateLeaveRequestDto requestDto) throws IOException {
		// 1) Event 객체 생성 및 기본 정보 설정
		Event event = new Event().setSummary(requestDto.title());

		// 2) 시작 시각 설정
		DateTime startDT = new DateTime(Date.from(requestDto.startDate().atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
		EventDateTime start = new EventDateTime().setDateTime(startDT);
		event.setStart(start);

		// 3) 종료 시각 설정
		DateTime endDT = new DateTime(Date.from(requestDto.endDate().atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
		EventDateTime end = new EventDateTime().setDateTime(endDT);
		event.setEnd(end);

		// 4) Calendar API 호출하여 등록
		Event created = calendarClient.events()
			.insert(GOOGLE_PERSONAL_CALENDAR_ID, event)
			.execute();

		try {
			// TODO : Security 에서 user 정보 꺼내서 id 넣도록 수정
			LeaveAndHoliday entity = LeaveAndHoliday.of(requestDto, 2L, created.getId());
			leaveAndHolidayRepository.saveAndFlush(entity);
		} catch (RuntimeException dbException) {
			// 캘린더에 저장된거 삭제
			try {
				calendarClient.events()
					.delete(GOOGLE_PERSONAL_CALENDAR_ID, event.getId())
					.execute();
			} catch (Exception ignore) {
				log.error("Calendar에 저장 성공했으나 DB 저장 실패하여 캘린더 삭제 시도했으나 삭제 실패, 수동 업데이트 필요 event = {}", event);
			}
			// db 예외난거는 다시 롤백하기 위해서 예외 다시 던짐
			throw dbException;
		}
	}

	/**
	 * 기존 이벤트(eventId)의 시작/종료 시각을 업데이트합니다.
	 */
	@Transactional
	public void updateEventDate(Long eventId, PatchLeaveRequestDto dto) throws IOException {
		log.info("new updateEventDate :: event Id = {}, dtp = {}", eventId, dto);

		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		// TODO : 사용자 & 로직 검증 추가

		// 1) 엔티티 수정
		leaveAndHoliday.patchEntityByDto(dto);

		String googleEventId = leaveAndHoliday.getGoogleEventId();

		// 2) Google 이벤트에서 현재 start/end 정보 조회
		Event apiEvent;
		try {
			apiEvent = calendarClient.events()
				.get(GOOGLE_PERSONAL_CALENDAR_ID, googleEventId)
				.execute();
		} catch (IOException e) {
			throw new IllegalArgumentException("Google 이벤트 조회 실패", e);
		}

		// 3) 변경 없으면 바로 리턴
		if (!applyAllChanges(apiEvent, dto)) {
			return;
		}

		// TODO : 에외 공통화
		// 4) 변경된 필드가 있으면 PATCH 호출
		try {
			calendarClient.events()
				.patch(GOOGLE_PERSONAL_CALENDAR_ID, googleEventId, apiEvent)
				.execute();
		} catch (GoogleJsonResponseException e) {
			if (e.getStatusCode() == 404) {
				log.error("업데이트할 이벤트를 찾을 수 없음: googleEventId={}", googleEventId);
			}
			throw new IllegalArgumentException("이벤트 업데이트 실패", e);
		}
	}

	/**
	 * apiEvent 에 dto 의 변경값(summary, description, startDate, endDate)을
	 * 실제로 달라졌을 때만 적용하고, 하나라도 바뀌면 true 반환
	 */
	private boolean applyAllChanges(Event apiEvent, PatchLeaveRequestDto dto) {
		boolean changed = false;

		// --- 1) summary(제목) 검사/적용 ---
		if (StringUtils.hasText(dto.title())) {
			String curTitle = apiEvent.getSummary();
			if (!dto.title().equals(curTitle)) {
				apiEvent.setSummary(dto.title());
				changed = true;
			}
		}

		// --- 2) description(설명) 검사/적용 ---
		if (StringUtils.hasText(dto.description())) {
			String curDesc = apiEvent.getDescription();
			if (!dto.description().equals(curDesc)) {
				apiEvent.setDescription(dto.description());
				changed = true;
			}
		}

		// --- 3) startDate 검사/적용 ---
		if (dto.startDate() != null) {
			LocalDateTime newStart = dto.startDate();
			DateTime curDt = apiEvent.getStart().getDateTime();
			LocalDateTime curStart = DateUtils.convertToLocalDateTime(curDt.getValue());
			if (!curStart.equals(newStart)) {
				apiEvent.setStart(new EventDateTime()
					.setDateTime(new DateTime(
						Date.from(newStart.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant())
					))
				);
				changed = true;
			}
		}

		// --- 4) endDate 검사/적용 ---
		if (dto.endDate() != null) {
			LocalDateTime newEnd = dto.endDate();
			DateTime curDt = apiEvent.getEnd().getDateTime();
			LocalDateTime curEnd = DateUtils.convertToLocalDateTime(curDt.getValue());
			if (!curEnd.equals(newEnd)) {
				apiEvent.setEnd(new EventDateTime()
					.setDateTime(new DateTime(
						Date.from(newEnd.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant())
					))
				);
				changed = true;
			}
		}

		return changed;
	}

	/**
	 * 지정한 이벤트(eventId)를 삭제합니다.
	 */
	@Transactional
	public void deleteEvent(Long eventId) throws IOException {
		log.info("deleteEvent :: event Id = {}", eventId);

		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		// TODO : 사용자 & 로직 검증 추가

		// 1) DB 삭제 우선
		leaveAndHolidayRepository.delete(leaveAndHoliday);

		// 2) 캘린더 삭제 -> 이미 삭제된거는 DB 삭제만 처리하면 되니까
		try {
			calendarClient.events()
				// 객체는 아직 살아있기에 꺼내오기 가능
				.delete(GOOGLE_PERSONAL_CALENDAR_ID, leaveAndHoliday.getGoogleEventId())
				.execute();
		} catch (GoogleJsonResponseException e) {
			switch (e.getStatusCode()) {
				case 404 -> log.error("삭제할 이벤트를 찾을 수 없습니다. eventId={}", eventId);
				case 410 -> log.error("이미 삭제된 이벤트입니다. eventId={}", eventId);
				default -> throw new RuntimeException("예외 발생으로 삭제 불가", e);
			}
		}
	}
}