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
		// 1) isAllDay == true 이면 기본적으로 하루종일(00:00~23:59:59)로 설정
		if (Boolean.TRUE.equals(isAllDay)) {
			// startTime, endTime 이 null 이거나 전부 공백("")로 넘어올 경우 대비
			if (startTime == null) {
				startTime = LocalTime.MIDNIGHT;            // 00:00:00
			}
			if (endTime == null) {
				endTime   = LocalTime.of(23, 59, 59);      // 23:59:59
			}

			// 1‑a) 단, “연차 소진 타입” (isConsumesLeave==true) 은
			//      전일 연차는 업무시간(08:00~17:00)으로 고정하도록 처리
			if (leaveType.isConsumesLeave()) {
				startTime = LeaveAndHoliday.WORK_START_TIME;   // 08:00
				endTime   = LeaveAndHoliday.WORK_END_TIME;     // 17:00
			}
		}

		// 2) !allDay 인 경우, 업무시간 범위로 전일 전환 로직
		else {
			// 2-1) 08:00~17:00 정확히 입력되면 전일로 간주
			if (LeaveAndHoliday.WORK_START_TIME.equals(startTime)
				&& LeaveAndHoliday.WORK_END_TIME.equals(endTime)) {
				isAllDay = true;
			}

			// 2-2) 시작 ≤ 08:00 && 종료 ≥ 17:00 && 연차 소진 타입 → 전일
			// 연차 소진 아닌 일정은 그대로 둘꺼임
			boolean startEarlyEnough = !startTime.isAfter(LeaveAndHoliday.WORK_START_TIME);
			boolean endLateEnough   = !endTime.isBefore(LeaveAndHoliday.WORK_END_TIME);
			if (startEarlyEnough && endLateEnough && leaveType.isConsumesLeave()) {
				isAllDay = true;
				startTime = LeaveAndHoliday.WORK_START_TIME;
				endTime   = LeaveAndHoliday.WORK_END_TIME;
			}
		}
	}
}
