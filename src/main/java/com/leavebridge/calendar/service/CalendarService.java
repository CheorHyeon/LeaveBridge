package com.leavebridge.calendar.service;

import static com.leavebridge.calendar.entity.LeaveAndHoliday.*;
import static com.leavebridge.calendar.service.GoogleCalendarAPIService.*;
import static com.leavebridge.util.TimeRuleUtils.*;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.google.api.client.util.DateTime;
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
import com.leavebridge.util.TimeRuleUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CalendarService {

	private final LeaveAndHolidayRepository leaveAndHolidayRepository;
	private final GoogleCalendarAPIService googleCalendarAPIService;
	private final DtoAdjustService dtoAdjustService;
	private final GoogleEventPatcher googleEventPatcher;

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

		LocalDate monthStart = LocalDate.of(year, month, 1);
		LocalDate monthEnd   = monthStart.plusMonths(1).minusDays(1);   // 해당 월의 마지막 날

		/**
		 * 일정이 월 말 이전 시작했고 일정이 월초 이후에 끝 -> 6/30 ~ 7/2 같은 일정도 7월에 포함됨
		 * startDate <= MonthEnd && endDate >= monthStart
		 */
		List<LeaveAndHoliday> currentMonthEvents = leaveAndHolidayRepository
			.findAllByStartDateLessThanEqualAndEndDateGreaterThanEqual(monthEnd, monthStart);

		return currentMonthEvents.stream()
			.map(MonthlyEvent::from)
			.toList();
	}

	/**
	 * 지정된 calendarId에 연차를 등록합니다.
	 */
	@Transactional
	public void createTimedEvent(CreateLeaveRequestDto requestDto, Member member) {

		// 생성 dto 입력값에 따라 맞추기
		requestDto = dtoAdjustService.processLeaveRequestDataForCreate(requestDto, member.isGermany());

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
	 * 기존 이벤트(eventId)를 수정합니다.
	 */
	@Transactional
	public void updateEventDate(Long eventId, PatchLeaveRequestDto dto, Member member) {
		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		boolean isGermany = member.isGermany();  // 파견직 여부

		// 0) dto 입력값 맞추기
		dto = dtoAdjustService.processLeaveRequestDataForUpdate(dto, isGermany);

		String googleEventId = leaveAndHoliday.getGoogleEventId();

		// 1) 수정 가능한지 검증 (권한, 원래 entity type 등)
		validateToUpdateLeaveAndHolidayEntity(dto, member, leaveAndHoliday);

		Event apiEvent = null;
		boolean shouldSyncGoogle = isGermany && googleEventId != null;

		// 2) (파견직 & 구글ID 존재 시) Google Calendar 이벤트 조회
		if (shouldSyncGoogle) {
			apiEvent = googleCalendarAPIService.getGoogleCalendarEventByGoogleEventId(googleEventId);
		}

		// 3) (파견직만) 변경사항을 API payload에 반영할지 여부 판단
		boolean changed = false;
		if (shouldSyncGoogle) {
			changed = googleEventPatcher.applyAllChanges(apiEvent, dto);
		}
		// 4-1) 엔티티 기본 정보 수정
		leaveAndHoliday.patchEntityByDto(dto);

		// 4-2) 연차 사용량 재계산
		if (leaveAndHoliday.getLeaveType().isConsumesLeave()) {
			Map<String, Object> info = calcUsedDaysAndGetComment(
				leaveAndHoliday.getStartDate(), leaveAndHoliday.getStarTime(),
				leaveAndHoliday.getEndDate(), leaveAndHoliday.getEndTime(), isGermany
			);

			double usedDays = (double)info.get("usedDays");
			String comment = ((String)info.get("comment"));

			if (usedDays == 0.0) {
				throw new IllegalArgumentException("해당 기간에는 휴일·주말만 포함되어 실제 차감 연차가 없습니다. " +
												   "변경하시려면 기존 일정을 삭제한 후 다시 등록해주세요.");
			}

			leaveAndHoliday.updateUsedLeaveHours(usedDays);
			leaveAndHoliday.updateComment(comment);
		}

		// 5) (파견직 & 변경사항 있음 & 구글ID 존재 시) Google Calendar에 수정 반영
		if (shouldSyncGoogle && changed) {
			googleCalendarAPIService.patchGoogleCalendarEventByEventIdAndEvent(googleEventId, apiEvent);
		}
	}

	private void validateToUpdateLeaveAndHolidayEntity(PatchLeaveRequestDto dto, Member member,
		LeaveAndHoliday leaveAndHoliday) {
		checkHolidayUpdateAllowed(leaveAndHoliday, member);
		checkOwnerOrAdmin(member, leaveAndHoliday);
		if (dto.leaveType().isConsumesLeave()) {
			validateLeaveForPatch(dto, member);
		}

		// 2-1) 일반 <-> 휴일 변경 불가
		boolean wasHoliday = Boolean.TRUE.equals(leaveAndHoliday.getIsHoliday());
		boolean nowHoliday = Boolean.TRUE.equals(dto.isHolidayInclude());
		if (wasHoliday != nowHoliday) {
			throw new IllegalArgumentException("일반 일정과 휴일 일정은 상호 변경할 수 없습니다. 삭제 후 재등록해주세요.");
		}
		// 휴일일때 일정 변경 금지
		if (nowHoliday) {
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
	}

	/**
	 * 지정한 이벤트(eventId)를 삭제합니다.
	 */
	@Transactional
	public void deleteEvent(Long eventId, Member member) {
		LeaveAndHoliday leaveAndHoliday = leaveAndHolidayRepository.findById(eventId)
			.orElseThrow(() -> new IllegalArgumentException("해당 Id를 가진 이벤트가 없습니다."));

		boolean isGermany = member.isGermany();  // 파견직 여부
		String googleEventId = leaveAndHoliday.getGoogleEventId();

		// 권한 및 휴일 수정 가능 여부 검증
		checkOwnerOrAdmin(member, leaveAndHoliday);
		checkHolidayUpdateAllowed(leaveAndHoliday, member);

		// 만약 삭제 대상이 '휴일'이라면, 그 기간에 걸친 연차(consume leave)들을 미리 조회
		boolean isDeletingHoliday = Boolean.TRUE.equals(leaveAndHoliday.getIsHoliday());
		List<LeaveAndHoliday> impactedLeaves = Collections.emptyList();
		if (isDeletingHoliday) {
			// 휴일 기간 동안 연차 소모 타입에 해당하는 일정들
			impactedLeaves = leaveAndHolidayRepository.findAllConsumesLeaveByDateRange(
				leaveAndHoliday.getStartDate(), leaveAndHoliday.getEndDate(),
				List.of(LeaveType.FULL_DAY_LEAVE, LeaveType.HALF_DAY_MORNING, LeaveType.HALF_DAY_AFTERNOON,
					LeaveType.OUTING, LeaveType.SUMMER_VACATION)
			);
		}

		// 3) DB 삭제 우선
		leaveAndHolidayRepository.delete(leaveAndHoliday);
		leaveAndHolidayRepository.flush();

		// 3) (파견직 & googleEventId 유효할 때만) 구글 캘린더에서도 삭제
		if (isGermany && StringUtils.hasText(googleEventId)) {
			googleCalendarAPIService.deleteGoogleCalendarEvent(googleEventId);
		}

		// 4) 휴일 삭제 시, 영향받은 연차 재계산
		if (isDeletingHoliday) {
			for (LeaveAndHoliday leave : impactedLeaves) {
				// 재계산할때 일정의 주인에 따라 달라지게(파견 or 비파견) & batch size로 N+1 해결
				Map<String, Object> recalculated = calcUsedDaysAndGetComment(
					leave.getStartDate(), leave.getStarTime(),
					leave.getEndDate(), leave.getEndTime(), leave.getMember().isGermany());
				double usedDays = (double)recalculated.get("usedDays");
				String comment = (String)recalculated.get("comment");

				// 연차 정보 업데이트
				leave.updateUsedLeaveHours(usedDays);
				leave.updateComment(comment);
				leaveAndHolidayRepository.save(leave);
			}
		}
	}

	/**
	 * 휴일 등록 - 등록한 휴일에 기존 일정이 있을경우 삭제 (연차 차감되는 일정들만)
	 * -> Google Calendar 미동록
	 */

	private void handleHolidayRegistration(CreateLeaveRequestDto dto, Member member) {
		if (!member.isAdmin()) {
			log.info("비관리자의 휴일 등록 시도 차단 loginId = {}", member.getLoginId());
			throw new IllegalArgumentException("관리자만 휴일 등록이 가능합니다.");
		}
		// DB 저장
		saveEntity(dto, member, null, dto.isHolidayInclude(), 0.0, null);
		// 기존 연차 보정
		adjustOverlappingLeaves(dto);
	}

	/**
	 * 일정 등록 - 일반 일정 등록
	 * 중간에 휴일있을 경우 사용 시간 계산 제외, 휴일 또는 주말 시작일 불가능
	 */
	private void handleLeaveRegistration(CreateLeaveRequestDto requestDto, Member member) {
		double usedDays = 0.0;
		String comment = null;
		boolean isGermany = member.isGermany();

		// 1) “연차 소진” 타입인 경우에만 검증 & 연차 사용 처리
		if (requestDto.leaveType().isConsumesLeave()) {
			// 1-1. 검증(주말 혹은 휴일에 쓰려는거 아닌지)
			validateLeaveForCreate(requestDto, member);

			// 1-2. 연차 사용 시간 계산 (0.0 ~ N.0)
			Map<String, Object> usedInfoMap = calcUsedDaysAndGetComment(requestDto.startDate(), requestDto.startTime(),
				requestDto.endDate(), requestDto.endTime(), isGermany);
			usedDays = (double)usedInfoMap.get("usedDays");
			comment = (String)usedInfoMap.get("comment");

			if (usedDays == 0.0) {
				throw new IllegalArgumentException("해당 기간에 소진되는 연차가 없어 등록할 수 없습니다.");
			}
		}

		Event createdEvent = null;
		// 2) 파견직만 Google Calendar 이벤트 생성
		if (isGermany) {
			Event ev = createCalendarEvent(requestDto);
			createdEvent = googleCalendarAPIService.createGoogleCalendarEvent(ev);
		}

		// 3) DB 저장
		try {
			saveEntity(requestDto, member, createdEvent != null ? createdEvent.getId() : null, false,
				usedDays, comment);
		} catch (Exception ex) {
			if (createdEvent != null) {
				googleCalendarAPIService.deleteGoogleCalendarEvent(createdEvent.getId());
			}
			throw ex;  // 다시 예외를 던져서 DB 롤백되도록
		}
	}

	/**
	 * 관리자 휴일 등록 시 휴일과 겹치는 일정들 조정
	 * - 공휴일 + 국경일 + 휴가 포함 : 전체 인원 영향
	 * - 공휴일은 보통 하루종일 이니깐 근무시간 다른것 고려 안함
	 * - 기념일 + 휴가 포함 : 파견직 제외 직원 영향 (파견지에서의 특별 기념일 휴가는 관리자가 관리하기 어려울 것)
	 */
	private void adjustOverlappingLeaves(CreateLeaveRequestDto dto) {

		// 0) 휴일 타입에 따른 처리 분기
		LeaveType holidayType = dto.leaveType();
		// 1) “기념일(ANNIVERSARY)” + 휴가 포함: 파견직 일정(googleEventId가 있는)만 건너뛰고, 일반 직원 일정만 조정
		boolean isAnniversary = holidayType == LeaveType.ANNIVERSARY;

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

			Member owner = leave.getMember();  // batch size로 한방에 가져와서 1+N 해결함
			boolean isGermany = owner.isGermany();

			// --- 분기 1: 파견직 + 기념일 → 건너뛰기 --- (파견직은 기념일 놉)
			if (isGermany && isAnniversary) {
				continue;
			}

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
				// 구글 캘린더 Id 가진 이벤트만 연동
				if (StringUtils.hasText(leave.getGoogleEventId())) {
					googleCalendarAPIService.deleteGoogleCalendarEvent(leave.getGoogleEventId());
				}
			} else {
				// 부분 보정: 사용일수와 사유 재계산
				Map<String, Object> info = calcUsedDaysAndGetComment(
					leave.getStartDate(), leave.getStarTime(),
					leave.getEndDate(), leave.getEndTime(), isGermany
				);
				double usedDays = (double)info.get("usedDays");
				String reason = ((String)info.get("comment"));

				leave.updateUsedLeaveHours(usedDays);
				leave.updateComment(reason);
				leaveAndHolidayRepository.saveAndFlush(leave);
			}
		}
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

	// LeaveAndHoliday에서 LocalDateTime 구간 추출용 DTO
	record DateTimeInterval(LocalDateTime start, LocalDateTime end) { }

	/**
	 * 주어진 부분 휴일들의 겹치는 시간을 합하여 최종적으로 제외할 시간들 리스트 반환 함수
	 */
	private List<DateTimeInterval> mergeHolidayIntervalsNonIncludeLunchTime(
		List<LeaveAndHoliday> partials,
		LocalDate targetDate, boolean isGermany
	) {
		List<DateTimeInterval> intervals = new ArrayList<>();
		LocalTime adjustStartTime = getAdjustStartTime(isGermany);
		LocalTime adjustEndTime = getAdjustEndTime(isGermany);

		for (LeaveAndHoliday h : partials) {
			// 1) 이 건이 targetDate를 포함하므로, 날짜 루프 불필요
			// rawStart: 휴일이 targetDate 이전부터 시작됐다면 근무시작, 아니면 실제 시작시간
			LocalTime rawStart = h.getStartDate().isBefore(targetDate)
				? adjustStartTime
				: h.getStarTime();
			// rawEnd: 휴일이 targetDate 이후까지 이어진다면 근무종료, 아니면 실제 종료시간
			LocalTime rawEnd = h.getEndDate().isAfter(targetDate)
				? adjustEndTime
				: h.getEndTime();

			// 2) 근무시간 범위로 클램핑
			LocalTime startT = rawStart.isBefore(adjustStartTime) ? adjustStartTime : rawStart;
			LocalTime endT = rawEnd.isAfter(adjustEndTime) ? adjustEndTime : rawEnd;

			// 3) 유효 구간이면 (시작 18시, 종료 19시면 안맞게되는 등)
			if (startT.isBefore(endT)) {
				boolean overlapsLunch =
					startT.isBefore(LUNCH_END) && endT.isAfter(LUNCH_START);

				if (overlapsLunch) {
					// 점심 전 구간
					if (startT.isBefore(LUNCH_START)) {
						intervals.add(new DateTimeInterval(
							LocalDateTime.of(targetDate, startT),
							LocalDateTime.of(targetDate, LUNCH_START)
						));
					}
					// 점심 후 구간
					if (endT.isAfter(LUNCH_END)) {
						intervals.add(new DateTimeInterval(
							LocalDateTime.of(targetDate, LUNCH_END),
							LocalDateTime.of(targetDate, endT)
						));
					}
				} else {
					// 점심과 겹치지 않으면 그대로 추가
					intervals.add(new DateTimeInterval(
						LocalDateTime.of(targetDate, startT),
						LocalDateTime.of(targetDate, endT)
					));
				}
			}
		}
		// 병합
		return mergeIntervals(intervals);
	}

	/**
	 * 주어진 (시작,종료) 구간들을 겹침 및 연속 포함해서 최대 병합한 리스트로 반환
	 */
	public static List<DateTimeInterval> mergeIntervals(List<DateTimeInterval> intervals) {
		if (intervals.isEmpty()) {
			return Collections.emptyList();
		}

		// 1) 시작 시간 기준으로 정렬 (LocalDateTime 비교)
		intervals.sort(Comparator.comparing(DateTimeInterval::start));

		List<DateTimeInterval> merged = new ArrayList<>();
		// 2) 첫 구간으로 시작
		DateTimeInterval current = intervals.getFirst();

		for (int i = 1; i < intervals.size(); i++) {
			DateTimeInterval next = intervals.get(i);

			// "겹치거나 연속" (current.end >= next.start)일 때 병합
			if (!current.end().isBefore(next.start())) {
				// end 시각을 둘 중 더 늦은 쪽으로 연장
				LocalDateTime newEnd = current.end().isAfter(next.end())
					? current.end()
					: next.end();
				current = new DateTimeInterval(current.start(), newEnd);
			} else {
				// 틈이 있으면, 지금까지 병합된 current를 결과에 추가하고 next를 새로운 current로
				merged.add(current);
				current = next;
			}
		}
		// 마지막 구간 추가
		merged.add(current);

		return merged;
	}

	/**
	 * 실제 연차 사용 “일수” 계산 + 연차 비차감 사유 추출
	 */
	private Map<String, Object> calcUsedDaysAndGetComment(LocalDate startDate, LocalTime startTime, LocalDate endDate,
		LocalTime endTime, boolean isGermany) {

		LocalDateTime start = LocalDateTime.of(startDate, startTime);
		LocalDateTime end = LocalDateTime.of(endDate, endTime);

		double totalMinutes = 0;
		StringBuilder reasonBuilder = new StringBuilder();

		// 전체 구간(startDate~endDate)에 걸친 “부분 휴일”만 미리 가져온다.
		List<LeaveAndHoliday> allPartials = leaveAndHolidayRepository
			.findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayFalse(
				endDate, startDate
			);

		/**
		 * 적용 날짜 : [휴일 엔티티] 묶어줌 (여러 일 거친것도 특정일 기준으로 검사할 수 있게)
		 * h1: 7/13 ~ 7/15, h2: 7/14 ~ 7/14, h3: 7/16 ~ 7/17
		 * partialsByDate.get(2025-07-13) → [h1]
		 * partialsByDate.get(2025-07-14) → [h1, h2]
		 * partialsByDate.get(2025-07-15) → [h1]
		 * partialsByDate.get(2025-07-16) → [h3]
		 * partialsByDate.get(2025-07-17) → [h3]
		 */
		Map<LocalDate, List<LeaveAndHoliday>> partialsByDate = new HashMap<>();
		for (LeaveAndHoliday h : allPartials) {
			// 0) “기념일+파견직 제외”를 공통 처리
			//    - dto가 ANNIVERSARY인데 등록하려는 회원이 파견직이면 아무 것도 하지 않음(차감 안함)
			if (h.getLeaveType() == LeaveType.ANNIVERSARY && isGermany) {
				continue;
			}
			LocalDate from = h.getStartDate();
			LocalDate to = h.getEndDate();
			for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
				// map에 key d가 없으면 새 리스트 생성
				if (!partialsByDate.containsKey(d)) {
					partialsByDate.put(d, new ArrayList<>());
				}
				// 해당 날짜 리스트에 h 추가
				partialsByDate.get(d).add(h);
			}
		}

		// 날짜별 루프 (하루 단위 탐색)
		for (LocalDate d = start.toLocalDate(); !d.isAfter(end.toLocalDate()); d = d.plusDays(1)) {

			// 1) 주말 스킵
			DayOfWeek dow = d.getDayOfWeek();
			if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
				reasonBuilder.append("[").append(d).append("] 주말 제외\n");
				continue;
			}

			// 2) 전일 휴일이 포함된 일정이면 스킵
			if (isHoliday(d, isGermany)) {
				reasonBuilder.append("[").append(d).append("] 하루종일 휴일이 포함된 일정 제외\n");
				continue;
			}

			// 3) 부분 휴일 조회 - 지금 연차 계산일이 포함된 일정 가져오기
			List<LeaveAndHoliday> partials = partialsByDate.getOrDefault(d, Collections.emptyList());

			// 4) 해당 날짜의 시작/종료 시각 결정
			// 시작, 종료일이 아니라면 중간에 낀거니까 이건 1일 연차임이 자명 -> 일 시작, 종료 시간으로 세팅
			LocalDateTime dayStart = d.equals(start.toLocalDate())
				? start // 이번 반복이 시작일과 같다면 이 날짜의 연차 시작으로 봄
				: d.atTime(getAdjustStartTime(isGermany)); // 이번 반복이 시작일이 아니라면 이 날짜의 근무시간 시작

			LocalDateTime dayEnd = d.equals(end.toLocalDate())
				? end // 이번 반복의 종료일이 매개변수 종료일과 같다면 이 날짜의 연차 끝날로봄
				: d.atTime(getAdjustEndTime(isGermany)); // 이번 반복이 연차 종료일이 아니라면(중간일 - 당연히 1일 연차니깐 일과 끝나는 시간으로 변경)

			// 5) 점심시간에만 있는 일정 스킵
			if (isOnlyLunch(dayStart, dayEnd)) {
				reasonBuilder
					.append("[").append(d).append("] 점심시간(12:00~13:00) 만 포함된 일정 전부 제외\n");
				continue;
			}

			// 6) 해당 일자에 이미 존재하는 휴일들의 시간 중복을 합친 새로운 일정 반환(점심시간 포함이면 전후로)
			List<DateTimeInterval> intervals = mergeHolidayIntervalsNonIncludeLunchTime(partials, d, isGermany);

			// 7) 기본 분 계산 - 점심시간 포함하면 1시간 제외
			long minutes = Duration.between(dayStart, dayEnd).toMinutes();
			if (TimeRuleUtils.isLunchIncluded(dayStart.toLocalTime(), dayEnd.toLocalTime())) {
				minutes -= 60;
			}
			long overlapMin = 0;

			// 8) 부분 휴일과 겹치는 분 차감
			for (DateTimeInterval interval : intervals) {
				LocalDateTime holStart = interval.start();
				LocalDateTime holEnd = interval.end();

				// 계산을 위해 요청 구간과 휴일 구간 겹치는 부분(시작점, 종료점) 추출
				LocalDateTime overlapStart = dayStart.isAfter(holStart) ? dayStart : holStart;
				LocalDateTime overlapEnd = dayEnd.isBefore(holEnd) ? dayEnd : holEnd;

				// 부분 휴일과 겹치는 부분을 사용한 연차에서 제외
				if (overlapEnd.isAfter(overlapStart)) {
					overlapMin += Duration.between(overlapStart, overlapEnd).toMinutes();
				}
			}

			minutes -= overlapMin;
			if (overlapMin > 0) {
				reasonBuilder
					.append("[").append(d).append("] 부분 휴일 ")
					.append(overlapMin).append("분 제외");
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
	 * 주어진 날이 시작일인 휴일인 일정이 한개라도 있는지 반환
	 * 단 파견직의 경우 관리자가 등록한 "기념일 +휴일"은 휴일로 취급하지 않는다.
	 */
	private boolean isHoliday(LocalDate date, boolean isGermany) {
		if (isGermany) {
			// date 가 휴일 기간 내에 속하고, ANNIVERSARY(기념일)가 아닌 하루종일 휴일이 있는지
			return leaveAndHolidayRepository
				.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayTrueAndLeaveTypeNot(
					date, date, LeaveType.ANNIVERSARY);
		} else {
			// date 가 휴일 기간 내에 속하는 모든 하루종일 휴일을 고려
			return leaveAndHolidayRepository
				.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayTrue(
					date, date);
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
			if (!member.isAdmin()) {
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

	private boolean checkOwnerOrAdminMember(Member member, LeaveAndHoliday leaveAndHoliday) {
		// 로그인 안했으면 그냥 나가리
		if (member == null) {
			return false;
		}
		boolean isAdmin = member.isAdmin();
		boolean isOwer = leaveAndHoliday.isOwnedBy(member);
		return isOwer || isAdmin;
	}

	/**
	 * 파견직 - 기념일 휴일에 연차 사용할 수 있도록
	 */
	private void validateLeaveForCreate(CreateLeaveRequestDto dto, Member member) {
		LocalDate start = dto.startDate();
		DayOfWeek dow = start.getDayOfWeek();
		if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
			throw new IllegalArgumentException("연차 시작일로 주말을 선택할 수 없습니다.");
		}
		if (isHoliday(start, member.isGermany())) {
			throw new IllegalArgumentException("연차 시작일로 휴일을 선택할 수 없습니다.");
		}
	}

	private void validateLeaveForPatch(PatchLeaveRequestDto dto, Member member) {
		LocalDate start = dto.startDate();
		DayOfWeek dow = start.getDayOfWeek();
		if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
			throw new IllegalArgumentException("연차 시작일로 주말을 선택할 수 없습니다.");
		}
		if (isHoliday(start, member.isGermany())) {
			throw new IllegalArgumentException("연차 시작일로 휴일을 선택할 수 없습니다.");
		}
	}
}