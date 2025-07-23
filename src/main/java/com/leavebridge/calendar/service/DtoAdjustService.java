package com.leavebridge.calendar.service;

import static com.leavebridge.util.TimeRuleUtils.*;

import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.leavebridge.calendar.dto.CreateLeaveRequestDto;
import com.leavebridge.calendar.dto.PatchLeaveRequestDto;
import com.leavebridge.calendar.enums.LeaveType;

@Service
public class DtoAdjustService {

	/**
	 * Dto 값 조정을 위한 내부 Record 및 함수
	 * Leave Type에 따라서 시작, 종료 시간 및 allDay 여부를 수정한다.
	 * 단 공휴일, 기념일의 경우 휴일 포함 여부 값이 오지 않으면 예외를 던진다.
	 */
	private record Adjusted(LocalTime start, LocalTime end, boolean allDay) { }


	/**
	 * 생성 Dto 조절
	 */
	public CreateLeaveRequestDto processLeaveRequestDataForCreate(CreateLeaveRequestDto requestDto,
		boolean isGermany) {
		// 1. Dto 값 조정
		Adjusted adjustedDto = adjustByLeaveType(requestDto.leaveType(), isGermany, requestDto.startTime(),
			requestDto.endTime(), requestDto.isAllDay(), requestDto.isHolidayInclude());

		// 2) 새로운 CreateLeaveRequestDto 반환
		return new CreateLeaveRequestDto(
			requestDto.title(), adjustedDto.allDay(), requestDto.leaveType(),
			requestDto.startDate(), requestDto.endDate(),
			adjustedDto.start(), adjustedDto.end(),
			requestDto.description(), requestDto.isHolidayInclude()
		);
	}

	/**
	 * 수정 Dto 조절
	 */
	public PatchLeaveRequestDto processLeaveRequestDataForUpdate(PatchLeaveRequestDto requestDto, boolean isGermany) {
		// 1. Dto 값 조정
		Adjusted adjustedDto = adjustByLeaveType(requestDto.leaveType(), isGermany, requestDto.startTime(),
			requestDto.endTime(), requestDto.isAllDay(),
			requestDto.isHolidayInclude());

		// 2_ 새로운 PatchLeaveRequestDto 반환
		return new PatchLeaveRequestDto(requestDto.title(), adjustedDto.allDay(), requestDto.leaveType(),
			requestDto.startDate(),
			requestDto.endDate(), adjustedDto.start(), adjustedDto.end(), requestDto.description(),
			requestDto.isHolidayInclude()
		);
	}

	/**
	 * LeaveType에 따른 로직, 내부에서만 접근 가능
	 */
	private Adjusted adjustByLeaveType(LeaveType type, boolean isGermany, LocalTime start, LocalTime end,
		Boolean allDay, Boolean isHolidayInclude) {

		LocalTime workStart = getAdjustStartTime(isGermany);
		LocalTime halfBoundary = getAdjustLunchBoundaryTime(isGermany);
		LocalTime workEnd = getAdjustEndTime(isGermany);

		switch (type) {
			case FULL_DAY_LEAVE, SUMMER_VACATION -> {
				start = workStart;
				end = workEnd;
				allDay = true;
			}
			case HALF_DAY_MORNING -> {
				start = workStart;
				end = halfBoundary;
				allDay = false;
			}
			case HALF_DAY_AFTERNOON -> {
				start = halfBoundary;
				end = workEnd;
				allDay = false;
			}
			case OUTING -> {
				if (start == null || end == null)
					throw new IllegalArgumentException("외출 시간 미입력 시 등록 불가");
				if (!start.isAfter(workStart))
					start = workStart;   // start ≤ workStart
				if (!end.isBefore(workEnd))
					end = workEnd;     // end   ≥ workEnd
				allDay = start.equals(workStart) && end.equals(workEnd);
			}
			case PUBLIC_HOLIDAY, ANNIVERSARY -> {
				if (isHolidayInclude == null)
					throw new IllegalArgumentException("공휴일, 기념일 입력 시 휴일 포함 여부는 필수입니다");
			}
		}
		return new Adjusted(start, end, allDay);
	}


}
