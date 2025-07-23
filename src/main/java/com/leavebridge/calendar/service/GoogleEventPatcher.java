package com.leavebridge.calendar.service;

import static com.leavebridge.calendar.service.GoogleCalendarAPIService.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.api.client.util.Data;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.leavebridge.calendar.dto.PatchLeaveRequestDto;
import com.leavebridge.util.DateUtils;

@Service
public class GoogleEventPatcher {

	/**
	 * apiEvent에 dto의 변경값을 적용하고, 하나라도 바뀌면 true 반환
	 */
	public boolean applyAllChanges(Event apiEvent, PatchLeaveRequestDto dto) {
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
}
