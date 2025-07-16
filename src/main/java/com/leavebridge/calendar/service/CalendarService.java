package com.leavebridge.calendar.service;

import static com.leavebridge.calendar.entity.LeaveAndHoliday.*;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
	public List<MonthlyEvent> listMonthlyEvents(int year, int month) {
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
		// 휴일인 경우
		if (Boolean.TRUE.equals(requestDto.isHolidayInclude())) {
			handleHolidayRegistration(requestDto, member);
		}
		// 휴일 아닌 경우
		else {
			handleLeaveRegistration(requestDto, member);
		}
	}

	/**
	 * 휴일 등록 - 등록한 휴일에 기존 일정이 있을경우 삭제 (연차 차감되는 일정들만)
	 */

	private void handleHolidayRegistration(CreateLeaveRequestDto dto, Member member) throws IOException {
		if (!member.isAdmin()) {
			log.info("비관리자의 휴일 등록 시도 차단 loginId = {}", member.getLoginId());
			throw new IllegalArgumentException("관리자만 휴일 등록이 가능합니다.");
		}
		// 1. 캘린더 등록
		Event event = createCalendarEvent(dto);
		Event created;
		try {
			created = calendarClient.events().insert(GOOGLE_PERSONAL_CALENDAR_ID, event).execute();
		} catch (Exception dbEx) {
			log.info("일정 등록 실패");
			throw new IllegalArgumentException("일정 등록 실패");
		}

		// 2. DB 저장
		try {
			saveEntity(dto, member, created.getId(), dto.isHolidayInclude(), 0.0, null);
		} catch (Exception e) {
			rollbackCalendar(created.getId());
			throw e;
		}
		// 3. 기존 연차 보정
		adjustOverlappingLeaves(dto);
	}

	/**
	 * 일정 등록 - 일반 일정 등록
	 * 중간에 휴일있을 경우 사용 시간 계산 제외, 휴일 또는 주말 시작일 불가능
	 */
	private void handleLeaveRegistration(CreateLeaveRequestDto requestDto, Member member) throws IOException {
		double usedDays = 0.0;
		String comment = null;

		// 1) “연차 소진” 타입인 경우에만 검증 & 연차 사용 처리
		if (requestDto.leaveType().isConsumesLeave()) {
			// 1-1. 검증(주말 혹은 휴일에 쓰려는거 아닌지)
			validateLeave(requestDto);

			// 1-2. 연차 사용 시간 계산 (0.0 ~ N.0)
			Map<String, Object> usedInfoMap = calcUsedDaysAndGetComment(requestDto.startDate(), requestDto.startTime(),
				requestDto.endDate(), requestDto.endTime());
			usedDays = (double)usedInfoMap.get("usedDays");
			comment = (String)usedInfoMap.get("comment");

			if (usedDays == 0.0) {
				throw new IllegalArgumentException("해당 기간에 소진되는 연차가 없어 등록할 수 없습니다.");
			}
		}

		// 2) Google Calendar 이벤트 생성 (소진 여부와 무관하게)
		Event leaveEvent = createCalendarEvent(requestDto);
		Event createdLeave = calendarClient.events()
			.insert(GOOGLE_PERSONAL_CALENDAR_ID, leaveEvent)
			.execute();

		// 3. DB 저장
		try {
			// 일반 사용자
			saveEntity(requestDto, member, createdLeave.getId(), false, usedDays, comment);
		} catch (Exception e) {
			rollbackCalendar(createdLeave.getId());
			throw e;
		}
	}

	private void adjustOverlappingLeaves(CreateLeaveRequestDto dto) throws IOException {
		// 휴일 범위
		LocalDate holStartDate = dto.startDate();
		LocalTime holStartTime = dto.startTime();
		LocalDate holEndDate = dto.endDate();
		LocalTime holEndTime = dto.endTime();

		// 연차 소진 타입 이벤트 조회
		List<LeaveAndHoliday> list = leaveAndHolidayRepository.findAllConsumesLeaveByDateRange(
			holStartDate, holEndDate,
			List.of(LeaveType.FULL_DAY_LEAVE, LeaveType.HALF_DAY_MORNING,
				LeaveType.HALF_DAY_AFTERNOON, LeaveType.OUTING, LeaveType.SUMMER_VACATION)
		);

		for (LeaveAndHoliday leave : list) {
			LocalDate leaveStartDate = leave.getStartDate();
			LocalDate leaveEndDate = leave.getEndDate();
			LocalTime leaveStartTime = leave.getStarTime();
			LocalTime leaveEndTime = leave.getEndTime();

			// 각 날짜별로 휴일 범위에 완전 포함되는지 확인
			boolean fullyCovered = true;
			for (LocalDate d = leaveStartDate; !d.isAfter(leaveEndDate); d = d.plusDays(1)) {
				LocalTime dayHolStart = d.equals(holStartDate) ? holStartTime : LocalTime.MIN;
				LocalTime dayHolEnd = d.equals(holEndDate) ? holEndTime : LocalTime.MAX;

				LocalTime dayLeaveStart = d.equals(leaveStartDate) ? leaveStartTime : LocalTime.MIN;
				LocalTime dayLeaveEnd = d.equals(leaveEndDate) ? leaveEndTime : LocalTime.MAX;

				boolean covered = !dayLeaveStart.isBefore(dayHolStart)
								  && !dayLeaveEnd.isAfter(dayHolEnd);
				if (!covered) {
					fullyCovered = false;
					break;
				}
			}

			if (fullyCovered) {
				// 완전 포함된 연차는 삭제
				leaveAndHolidayRepository.delete(leave);
				rollbackCalendar(leave.getGoogleEventId());
			} else {
				// 부분 보정: 사용일수와 사유 재계산
				Map<String, Object> info = calcUsedDaysAndGetComment(
					leave.getStartDate(), leave.getStarTime(),
					leave.getEndDate(), leave.getEndTime()
				);
				double usedDays = (double)info.get("usedDays");
				String reason = ((String)info.get("comment"));

				leave.updateUsedLeaveHours(usedDays);
				leave.updateComment(reason);
				leaveAndHolidayRepository.saveAndFlush(leave);
			}
		}
	}

	private void rollbackCalendar(String eventId) {
		try {
			calendarClient.events().delete(GOOGLE_PERSONAL_CALENDAR_ID, eventId).execute();
		} catch (Exception ex) {
			log.error("캘린더 롤백 실패: {}", eventId, ex);
		}
	}

	private void validateLeave(CreateLeaveRequestDto dto) {
		LocalDate start = dto.startDate();
		DayOfWeek dow = start.getDayOfWeek();
		if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
			throw new IllegalArgumentException("연차는 주말을 시작일로 사용할 수 없습니다.");
		if (isHoliday(start))
			throw new IllegalArgumentException("연차 시작일이 휴일일 수 없습니다.");
	}

	private void saveEntity(CreateLeaveRequestDto dto, Member member, String eventId,
		boolean isHoliday, double usedDays, String comment) {
		LeaveAndHoliday ent = LeaveAndHoliday.of(dto, member, eventId);
		ent.updateIsHoliday(isHoliday);
		if (dto.leaveType().isConsumesLeave()) {
			ent.updateUsedLeaveHours(usedDays);
			ent.updateComment(comment);
		}
		leaveAndHolidayRepository.saveAndFlush(ent);
	}

	private Event createCalendarEvent(CreateLeaveRequestDto dto) {
		Event event = new Event().setSummary(dto.title());
		LocalDateTime s = LocalDateTime.of(dto.startDate(), dto.startTime());
		LocalDateTime e = LocalDateTime.of(dto.endDate(), dto.endTime());
		event.setDescription(dto.description());
		if (dto.isAllDay()) {
			event.setStart(new EventDateTime().setDate(new DateTime(dto.startDate().toString())));
			event.setEnd(new EventDateTime().setDate(new DateTime(dto.endDate().plusDays(1).toString())));
		} else {
			event.setStart(new EventDateTime().setDateTime(
				new DateTime(Date.from(s.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()))));
			event.setEnd(new EventDateTime().setDateTime(
				new DateTime(Date.from(e.atZone(ZoneId.of(DEFAULT_TIME_ZONE)).toInstant()))));
		}
		return event;
	}

	/**
	 * 실제 연차 사용 “일수” 계산 + 연차 비차감 사유 추출
	 */
	private Map<String, Object> calcUsedDaysAndGetComment(LocalDate startDate, LocalTime startTime, LocalDate endDate,
		LocalTime endTime) {

		LocalDateTime start = LocalDateTime.of(startDate, startTime);
		LocalDateTime end = LocalDateTime.of(endDate, endTime);

		double totalMinutes = 0;
		StringBuilder reasonBuilder = new StringBuilder();

		// 날짜별 루프 (하루 단위 탐색)
		for (LocalDate d = start.toLocalDate(); !d.isAfter(end.toLocalDate()); d = d.plusDays(1)) {

			// 1) 주말 스킵
			DayOfWeek dow = d.getDayOfWeek();
			if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
				reasonBuilder.append("[").append(d).append("] 주말 제외\n");
				continue;
			}

			// 2) 전일 휴일 스킵
			if (isHoliday(d)) {
				reasonBuilder.append("[").append(d).append("] 전일 휴일 제외\n");
				continue;
			}

			// 3) 부분 휴일 조회
			List<LeaveAndHoliday> partials = leaveAndHolidayRepository
				.findByStartDateAndIsHolidayTrueAndIsAllDayFalse(d);

			// 4) 해당 날짜의 시작/종료 시각 결정
			// 시작, 종료일이 아니라면 중간에 낀거니까 이건 1일 연차임이 자명 -> 일 시작, 종료 시간으로 세팅
			LocalDateTime dayStart = d.equals(start.toLocalDate())
				? start // 이번 반복이 시작일과 같다면 이 날짜의 연차 시작으로 봄
				: d.atTime(WORK_START_TIME); // 이번 반복이 시작일이 아니라면 이 날짜의 08시로 시작

			LocalDateTime dayEnd = d.equals(end.toLocalDate())
				? end // 이번 반복의 종료일이 매개변수 종료일과 같다면 이 날짜의 연차 끝날로봄
				: d.atTime(WORK_END_TIME); // 이번 반복이 연차 종료일이 아니라면(중간일 - 당연히 1일 연차니깐 일과 끝나는 시간으로 변경)

			// 5) 점심만 일정 스킵
			if (isOnlyLunch(dayStart, dayEnd)) {
				reasonBuilder
					.append("[").append(d).append("] 점심시간(12:00~13:00) 전부 제외\n");
				continue;
			}

			// 6) 기본 분 계산
			long minutes = Duration.between(dayStart, dayEnd).toMinutes();

			// 7) 부분 휴일과 겹치는 분 차감
			for (LeaveAndHoliday h : partials) {
				LocalDateTime holStart = LocalDateTime.of(d, h.getStarTime());
				LocalDateTime holEnd = LocalDateTime.of(d, h.getEndTime());

				// 계산을 위해 요청 구간과 휴일 구간 겹치는 부분
				LocalDateTime overlapStart = dayStart.isAfter(holStart) ? dayStart : holStart;
				LocalDateTime overlapEnd = dayEnd.isBefore(holEnd) ? dayEnd : holEnd;

				if (overlapEnd.isAfter(overlapStart)) {
					long overlapMin = Duration.between(overlapStart, overlapEnd).toMinutes();
					minutes -= overlapMin;
					// 상세 사유: 시간 범위 표시
					reasonBuilder
						.append("[").append(d).append("] 부분 휴일 ")
						.append(holStart.toLocalTime()).append("~")
						.append(holEnd.toLocalTime())
						.append(" 중 ").append(overlapMin).append("분 제외\n");
				}
			}

			// 8) 점심시간이 겹치면 60분 차감
			if (isLunchIncluded(dayStart.toLocalTime(), dayEnd.toLocalTime())) {
				minutes -= 60;
				reasonBuilder
					.append("[").append(d).append("] 점심시간(12:00~13:00) 60분 제외\n");
			}

			totalMinutes += Math.max(0, minutes);
		}

		// 8시간 = 480분 → 1일
		double usedDays = (totalMinutes / 60.0) / 8.0;
		String reason = !reasonBuilder.isEmpty() ? reasonBuilder.toString().trim() : "";

		return Map.of(
			"usedDays", usedDays,
			"comment", reason
		);
	}

	/**
	 * 12:00~13:00 구간이 포함되는지
	 */
	private boolean isLunchIncluded(LocalTime st, LocalTime en) {
		return !st.isAfter(LUNCH_START)   //  st ≤ 12:00
			   && !en.isBefore(LUNCH_END);   //  en ≥ 13:00
	}

	/**
	 * 12:00 ~ 13:00 구간 전부만 포함한 일정인지 여부
	 */
	private boolean isOnlyLunch(LocalDateTime start, LocalDateTime end) {
		return !start.toLocalTime().isBefore(LUNCH_START)   // start ≥ 12:00
			   && !end.toLocalTime().isAfter(LUNCH_END);       // end   ≤ 13:00
	}

	private boolean isHoliday(LocalDate startDate) {
		// 하루종일 쉬는 날인지 반환 (기념일 & 휴일) -> 시작일만 안맞으면 사실 괜찮으니
		return leaveAndHolidayRepository.existsByStartDateAndIsHolidayTrueAndIsAllDayTrue(startDate);
	}

	/**
	 * 기존 이벤트(eventId)를 수정합니다.
	 */
	@Transactional
	public void updateEventDate(Long eventId, PatchLeaveRequestDto dto, Member member) throws IOException {
		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		// 1) 수정 가능한 타입인지, 관리자 또는 일정 등록자인지, 연차 시작이 휴일 시작 또는 주말 인지 검증
		checkHolidayUpdateAllowed(leaveAndHoliday, member);
		checkOwnerOrAdmin(member, leaveAndHoliday);
		if (dto.leaveType().isConsumesLeave()) {
			validateLeaveForPatch(dto);
		}

		// 2-1) 일반 <-> 휴일 변경 불가
		boolean wasHoliday = Boolean.TRUE.equals(leaveAndHoliday.getIsHoliday());
		boolean nowHoliday = Boolean.TRUE.equals(dto.isHolidayInclude());
		if (wasHoliday != nowHoliday) {
			throw new IllegalArgumentException("일반 일정과 휴일 일정은 상호 변경할 수 없습니다. 삭제 후 재등록해주세요.");
		}
		// 휴일일때 일정 변경 금지
		else {
			// DTO 상의 startDate/startTime, endDate/endTime 이 원본과 다르면 예외
			if (!dto.startDate().equals(leaveAndHoliday.getStartDate())
				|| !dto.endDate().equals(leaveAndHoliday.getEndDate())
				|| !dto.startTime().equals(leaveAndHoliday.getStarTime())
				|| !dto.endTime().equals(leaveAndHoliday.getEndTime())
			) {
				throw new IllegalArgumentException("등록된 휴일 일정은 기간 변경이 불가능합니다. 삭제 후 재등록해주세요.");
			}
		}

		//  2-2) “연차 소진" <-> "연차 미소진" 타입의 일정 변경 불가
		boolean wasDeductible = leaveAndHoliday.getLeaveType().isConsumesLeave();
		boolean nowDeductible = dto.leaveType().isConsumesLeave();
		if (wasDeductible != nowDeductible) {
			throw new IllegalArgumentException("연차 소진 타입 변경은 지원되지 않습니다. 삭제 후 재등록해 주세요.");
		}

		// 3) Google Calendar에 등록된 이벤트 정보 조회
		String googleEventId = leaveAndHoliday.getGoogleEventId();
		Event apiEvent;
		try {
			apiEvent = calendarClient.events()
				.get(GOOGLE_PERSONAL_CALENDAR_ID, googleEventId)
				.execute();
		} catch (IOException e) {
			throw new IllegalArgumentException("Google 이벤트 조회 실패", e);
		}

		// 4)  applyAllChanges 에서 API payload에 반영해야 할 변경이 있었는지 저장
		boolean changed = applyAllChanges(apiEvent, dto);

		// 5-1) 엔티티 기본 정보 수정
		leaveAndHoliday.patchEntityByDto(dto);

		// 5-2) 연차 사용량 재계산
		if (leaveAndHoliday.getLeaveType().isConsumesLeave()) {
			Map<String, Object> info = calcUsedDaysAndGetComment(
				leaveAndHoliday.getStartDate(), leaveAndHoliday.getStarTime(),
				leaveAndHoliday.getEndDate(), leaveAndHoliday.getEndTime()
			);

			double usedDays = (double)info.get("usedDays");
			String comment = ((String)info.get("comment"));

			if (usedDays == 0.0) {
				throw new IllegalArgumentException("해당 기간에 휴일/주말만 포함되어 실제 연차 소진이 없게되어 수정할 수 없습니다.");
			}

			leaveAndHoliday.updateUsedLeaveHours(usedDays);
			leaveAndHoliday.updateComment(comment);
		}

		// TODO : 에외 공통화
		// 6) PATCH 호출하여 구글 캘린더에도 수정 반영하기
		if (changed) {
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
	}

	/**
	 * 지정한 이벤트(eventId)를 삭제합니다.
	 */
	@Transactional
	public void deleteEvent(Long eventId, Member member) throws IOException {
		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		checkOwnerOrAdmin(member, leaveAndHoliday);
		checkHolidayUpdateAllowed(leaveAndHoliday, member);

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
	private void checkHolidayUpdateAllowed(LeaveAndHoliday leaveAndHoliday, Member member) {

		LeaveType targetLeaveType = leaveAndHoliday.getLeaveType();
		if (targetLeaveType == LeaveType.OTHER_PEOPLE) {
			throw new IllegalArgumentException("비회원 이벤트는 관리자도 조작할 수 없습니다.");
		}

		List<LeaveType> cantModifyingType = List.of(LeaveType.PUBLIC_HOLIDAY, LeaveType.NATIONAL_HOLIDAY,
			LeaveType.SUNDRY_DAY, LeaveType.TWENTY_FOUR_SOLAR_TERMS, LeaveType.ANNIVERSARY);

		if (cantModifyingType.contains(targetLeaveType)) {
			if(!member.isAdmin()) {
				throw new IllegalArgumentException(targetLeaveType.getType() + "은 관리자만 조작할 수 있습니다.");
			}
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
		if (member == null) {
			return false;
		}
		boolean isAdmin = member.isAdmin();
		boolean isOwer = leaveAndHoliday.isOwnedBy(member);
		return isOwer || isAdmin;
	}

	private void validateLeaveForPatch(PatchLeaveRequestDto dto) {
		LocalDate start = dto.startDate();
		DayOfWeek dow = start.getDayOfWeek();
		if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY)
			throw new IllegalArgumentException("연차는 주말을 시작일로 사용할 수 없습니다.");
		if (isHoliday(start))
			throw new IllegalArgumentException("연차 시작일이 휴일일 수 없습니다.");
	}
}