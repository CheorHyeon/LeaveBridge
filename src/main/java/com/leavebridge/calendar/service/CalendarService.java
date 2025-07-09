package com.leavebridge.calendar.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import com.google.api.services.calendar.model.Events;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.util.Data;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.leavebridge.calendar.dto.CreateLeaveRequestDto;
import com.leavebridge.calendar.dto.MonthlyEvent;
import com.leavebridge.calendar.dto.MonthlyEventDetailResponse;
import com.leavebridge.calendar.dto.PatchLeaveRequestDto;
import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;
import com.leavebridge.member.entitiy.Member;
import com.leavebridge.util.DateUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalendarService {
	private final Calendar calendarClient;
	private static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
	private final LeaveAndHolidayRepository leaveAndHolidayRepository;

	@Value("${google.calendar-id}")
	private String GOOGLE_PERSONAL_CALENDAR_ID;

	/**
	 * 특정 이벤트의 상세 정보를 조회합니다.
	 */
	public MonthlyEventDetailResponse getEventDetails(Long eventId, Member member) {

		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId).orElseThrow(
			() -> new IllegalArgumentException("존재하지 않는 일정 Id입니다."));

		boolean canAccess = checkOwnerOrAdminMember(member, leaveAndHoliday);
		boolean cantModifyLeave = leaveAndHoliday.canModifyLeave();

		return MonthlyEventDetailResponse.of(leaveAndHoliday, canAccess && cantModifyLeave);
	}

	/**
	 * 지정된 calendarId의 설정한 연도, 월에 해당하는 일정 목록 로드
	 */
	public List<MonthlyEvent> listMonthlyEvents(int year, int month) throws Exception {
		log.info("CalendarService.listMonthlyEvents :: year={}, month={}", year, month);

		LocalDate startDate = LocalDate.of(year, month, 1);
		LocalDate endDate = startDate.plusMonths(1);  // 다음 달 1일 00:00

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
	public void createTimedEvent(CreateLeaveRequestDto requestDto, Member member) throws IOException {
		// 1) Event 객체 생성 및 기본 정보 설정
		Event event = new Event().setSummary(requestDto.title());

		LocalDateTime startLdt = DateUtils.makeLocalDateTimeFromLocalDAteAndLocalTime(
			requestDto.startDate(), requestDto.startTime());

		LocalDateTime endLdt = DateUtils.makeLocalDateTimeFromLocalDAteAndLocalTime(
			requestDto.endDate(), requestDto.endTime());

		// 3) 구글 캘린더에 보낼 start/end 설정
		if (requestDto.isAllDay()) {
			// 날짜 전용 (종일 이벤트)
			event.setStart(new EventDateTime().setDate(new DateTime(requestDto.startDate().toString())));
			// 종료는 “다음 날” 날짜만 넘김
			event.setEnd(new EventDateTime().setDate(new DateTime(requestDto.endDate().plusDays(1).toString())));
		} else {
			// 시간 지정
			DateTime startDt = new DateTime(Date.from(
				startLdt.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
			DateTime endDt = new DateTime(Date.from(
				endLdt.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()));
			event.setStart(new EventDateTime().setDateTime(startDt));
			event.setEnd(new EventDateTime().setDateTime(endDt));
		}

		// 4) Calendar API 호출하여 등록
		Event created = calendarClient.events()
			.insert(GOOGLE_PERSONAL_CALENDAR_ID, event)
			.execute();

		try {
			LeaveAndHoliday entity = LeaveAndHoliday.of(requestDto, member, created.getId());
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
	 * 기존 이벤트(eventId)를 수정합니다.
	 */
	@Transactional
	public void updateEventDate(Long eventId, PatchLeaveRequestDto dto, Member member) throws IOException {
		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		checkOwnerOrAdmin(member, leaveAndHoliday);

		checkHolidayUpdateAllowed(leaveAndHoliday);

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
	 * 지정한 이벤트(eventId)를 삭제합니다.
	 */
	@Transactional
	public void deleteEvent(Long eventId, Member member) throws IOException {
		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		checkOwnerOrAdmin(member, leaveAndHoliday);

		checkHolidayUpdateAllowed(leaveAndHoliday);

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

	/**
	 * 공휴일 검증
	 */
	private void checkHolidayUpdateAllowed(LeaveAndHoliday leaveAndHoliday) {
		if (leaveAndHoliday.getLeaveType() == LeaveType.PUBLIC_HOLIDAY || leaveAndHoliday.getLeaveType() == LeaveType.OTHER_PEOPLE) {
			throw new IllegalArgumentException("공휴일 이벤트와 비회원 이벤트는 조작할 수 없습니다.");
		}
	}

	/**
	 * 관리자 혹은 일정 등록자인지 검증
	 */
	private void checkOwnerOrAdmin(Member member, LeaveAndHoliday leaveAndHoliday) {
		if (!checkOwnerOrAdminMember(member, leaveAndHoliday)) {
			log.info("login Id = {} 가 관리자도 아닌데 다른 일정 수정하려 시도 (비정상 접근)", member.getLoginId());
			throw new IllegalArgumentException("해당 일정 작성자 혹은 관리자만 일정을 수정할 수 있습니다.");
		}
	}

	/**
	 * apiEvent에 dto의 변경값을 적용하고, 하나라도 바뀌면 true 반환
	 */
	private boolean applyAllChanges(Event apiEvent, PatchLeaveRequestDto dto) {
		boolean changed = false;
		// |= 복합대입 연산자 사용해서 true가 한번이라도 나오면 무조건 true로 반환하도록
		// |= 연산자는 불리언에서 비단락 평가 논리합 연산 - 단락 평가(short-circuit) 하지 않아 오른쪽도 항상 검사
		// -> 즉 제목 변경이 이미 되었지만, 설명이나 일정도 변경되었을 수 있기에 메소드 무조건 실행하긴 함

		// 1) summary(제목) 검사/적용
		changed |= updateSummaryIfChanged(apiEvent, dto);

		// 2) description(설명) 검사/적용
		changed |= updateDescriptionIfChanged(apiEvent, dto);

		// 3) start/end DateTime 업데이트
		changed |= updateDateTimeIfChanged(apiEvent, dto);

		return changed;
	}

	/**
	 * 제목 업데이트
	 */
	private boolean updateSummaryIfChanged(Event apiEvent, PatchLeaveRequestDto dto) {
		if (StringUtils.hasText(dto.title()) && !dto.title().equals(apiEvent.getSummary())) {
			apiEvent.setSummary(dto.title());
			return true;
		}
		return false;
	}

	/**
	 * 설명 업데이트
	 */
	private boolean updateDescriptionIfChanged(Event apiEvent, PatchLeaveRequestDto dto) {
		if (StringUtils.hasText(dto.description()) && !dto.description().equals(apiEvent.getDescription())) {
			apiEvent.setDescription(dto.description());
			return true;
		}
		return false;
	}

	/**
	 * 현재 apiEvent 에 dto 로 받은 날짜/시간을 반영한다.
	 * 변경이 있었으면 true, 없으면 false
	 */
	private boolean updateDateTimeIfChanged(Event apiEvent, PatchLeaveRequestDto dto) {

		ZoneId zone = ZoneId.of(DEFAULT_TIME_ZONE);

		// ---------- 1) DTO → 목표 값 계산 ----------
		boolean wantedAllDay = Boolean.TRUE.equals(dto.isAllDay());

		LocalDateTime wantedStart = wantedAllDay
			? dto.startDate().atStartOfDay()
			: LocalDateTime.of(dto.startDate(), dto.startTime());

		LocalDateTime wantedEnd = wantedAllDay
			? dto.endDate().plusDays(1).atStartOfDay()       // ★ 전일은 +1day 00:00
			: LocalDateTime.of(dto.endDate(), dto.endTime());

		// ---------- 2) 현재 값 가져오기 ----------
		boolean currentAllDay = apiEvent.getStart().getDate() != null;

		LocalDateTime currentStart;
		LocalDateTime currentEnd;

		if (currentAllDay) {
    /* 전일 일정 ─ start·end 에는 '날짜만' 들어 있으므로
       → LocalDate 로 파싱한 뒤 자정으로 맞춰 LocalDateTime 생성 */
			currentStart = LocalDate
				.parse(apiEvent.getStart().getDate().toString())   // "2025-07-28"
				.atStartOfDay();                                   // 2025-07-28T00:00
			currentEnd = LocalDate
				.parse(apiEvent.getEnd().getDate().toString())     // 구글은 다음날 00:00 저장
				.atStartOfDay();                                   // 2025-07-29T00:00
		} else {
			/* 시간 지정 일정 ─ millisecond epoch 값 → LocalDateTime */
			currentStart = DateUtils.convertToLocalDateTime(apiEvent.getStart().getDateTime().getValue());
			currentEnd = DateUtils.convertToLocalDateTime(apiEvent.getEnd().getDateTime().getValue());
		}

		// ---------- 3) 변동 여부 확인 ----------
		// 하루종일 일정 == 바꿀일정도 하루종일 일정 & 일자도 같다 -> 변동 없음
		if (wantedAllDay == currentAllDay && wantedStart.equals(currentStart) && wantedEnd.equals(currentEnd)) {
			return false;
		}

		// ---------- 4) EventDateTime 새로 만들어 교체 ----------
		// 하루종일 일정으로 변경하고싶다 -> 새로운날의 하루종일 일정으로 변경
		if (wantedAllDay) {

			// 전일(all-day)로 바꿔야 할 경우 - 기존꺼에 업데이트 하기 때문에 Null 확실하게 처리해야 함
			EventDateTime newStart = new EventDateTime()
				.setDateTime(Data.NULL_DATE_TIME)   // 👈 반드시 포함
				.setTimeZone(null)
				.setDate(
					new DateTime(wantedStart.toLocalDate().toString())
				);

			EventDateTime newEnd = new EventDateTime()
				.setDateTime(Data.NULL_DATE_TIME)
				.setTimeZone(null)
				.setDate(
					new DateTime(wantedEnd.toLocalDate().toString())
				);

			apiEvent.setStart(newStart);
			apiEvent.setEnd(newEnd);
		}
		// 바꿀 일정이 하루종일이 아닌거로 바뀔경우 -> 새로운거로 변경
		else {
			DateTime startDt = new DateTime(
				Date.from(wantedStart.atZone(zone).toInstant())); // 2025-07-28T13:00:00+09:00
			DateTime endDt = new DateTime(
				Date.from(wantedEnd.atZone(zone).toInstant()));   // 2025-07-28T17:00:00+09:00

			apiEvent.setStart(new EventDateTime()
				.setDate(Data.NULL_DATE_TIME)            // date 필드 제거(시간 지정 이벤트이므로)
				.setDateTime(startDt)
				.setTimeZone(zone.getId()));

			apiEvent.setEnd(new EventDateTime()
				.setDate(Data.NULL_DATE_TIME)
				.setDateTime(endDt)
				.setTimeZone(zone.getId()));
		}

		return true;
	}

	private boolean checkOwnerOrAdminMember(Member member, LeaveAndHoliday leaveAndHoliday) {
		// 로그인 안했으면 그냥 나가리
		if(member == null) {
			return false;
		}
		boolean isAdmin = member.isAdmin();
		boolean isOwer = leaveAndHoliday.isOwnedBy(member);
		return isOwer || isAdmin;
	}
}