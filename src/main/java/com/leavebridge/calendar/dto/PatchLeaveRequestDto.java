package com.leavebridge.calendar.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record PatchLeaveRequestDto(
	@Schema(description = "수정 일정 제목", example = "박철현 연차")
	@NotBlank(message = "일정 제목은 필수입니다")
	String title,
	@Schema(description = "하루 종일 일정 여부", example = "true")
	@NotNull(message = "하루 종일 여부는 필수입니다")
	Boolean isAllDay,
	@Schema(description = "연차 종류", example = "HOLIDAY(공휴일), FULL_DAY_LEAVE(1일 연차), HALF_DAY_MORNING(오전_반차), HALF_DAY_AFTERNOON(오후 반차), OUT_GOING(외출), SUMMER_VACATION(여름 휴가), OTHER_PERSON(비회원 연차), NON_DEDUCTIBLE(공결, 병가, 기타 미소진 연차), MEETING(회의)")
	@NotNull(message = "연차 종류는 필수입니다")
	LeaveType leaveType,
	@Schema(description = "시작 날짜", example = "2025-07-01")
	@NotNull(message = "시작 날짜는 필수입니다")
	LocalDate startDate,
	@Schema(description = "종료 날짜", example = "2025-07-01")
	@NotNull(message = "종료 날짜는 필수입니다")
	LocalDate endDate,
	@Schema(description = "수정 시작 날짜 시간", example = "00:00")
	LocalTime startTime,
	@Schema(description = "수정 종료 날짜 시간", example = "23:59")
	LocalTime endTime,
	@Schema(description = "수정 비고", example = "일정 설명 수정 텍스트")
	String description,
	@Schema(description = "수정 휴일 포함 여부", example = "true(휴일 포함), false(휴일 미포함)")
	Boolean isHolidayInclude
) {
	public PatchLeaveRequestDto {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new IllegalArgumentException("시작 날짜는 종료 날짜보다 이후일 수 없습니다");
		}

		if(startTime != null && endTime != null && !(endTime.isAfter(startTime))) {
			throw new IllegalArgumentException("종료 시간은 시작 시간보다 늦어야합니다.");
		}
	}
}
