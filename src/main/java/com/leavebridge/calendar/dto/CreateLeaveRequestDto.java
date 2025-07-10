package com.leavebridge.calendar.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateLeaveRequestDto(
	@Schema(description = "일정 제목", example = "박철현 연차")
	String title,
	@Schema(description = "하루 종일 일정 여부", example = "true")
	Boolean isAllDay,
	@Schema(description = "연차 종류", example = "HOLIDAY(공휴일), FULL_DAY_LEAVE(1일 연차), HALF_DAY_MORNING(오전_반차), HALF_DAY_AFTERNOON(오후 반차), OUT_GOING(외출), SUMMER_VACATION(여름 휴가), OTHER_PERSON(비회원 연차), NON_DEDUCTIBLE(공결, 병가, 기타 미소진 연차), MEETING(회의)")
	LeaveType leaveType,
	@Schema(description = "시작 날짜", example = "2025-07-01")
	LocalDate startDate,
	@Schema(description = "종료 날짜", example = "2025-07-01")
	LocalDate endDate,
	@Schema(description = "시작 날짜 시간", example = "00:00")
	LocalTime startTime,
	@Schema(description = "종료 날짜 시간", example = "23:59")
	LocalTime endTime,
	@Schema(description = "비고", example = "일정 설명 텍스트")
	String description,
	@Schema(description = "휴일 포함 여부", example = "true(휴일 포함), false(휴일 미포함)")
	Boolean isHolidayInclude
) {
	public CreateLeaveRequestDto {
		if (!Boolean.TRUE.equals(isAllDay)) {
			// 08:00~17:00 정확히이면 전일
			if (LeaveAndHoliday.WORK_START_TIME.equals(startTime) &&
				LeaveAndHoliday.WORK_END_TIME.equals(endTime)) {
				isAllDay = true;
			}

			// ★ 시작 ≤ 08:00  &&  종료 ≥ 17:00  → 전일
			boolean startEarlyEnough = !startTime.isAfter(LeaveAndHoliday.WORK_START_TIME); // ≤
			boolean endLateEnough   = !endTime.isBefore(LeaveAndHoliday.WORK_END_TIME);     // ≥
			if (startEarlyEnough && endLateEnough) {
				isAllDay = true;
			}

			// 타입이 전일 전용이면 강제 전일
			if (leaveType == LeaveType.FULL_DAY_LEAVE ||
				leaveType == LeaveType.SUMMER_VACATION ||
				leaveType == LeaveType.PUBLIC_HOLIDAY) {
				isAllDay = true;
			}
		}
	}
}
