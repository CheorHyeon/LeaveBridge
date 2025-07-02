package com.leavebridge.member.dto;

import java.time.LocalDateTime;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record LeaveDetailDto(
	@Schema(description = "Entity Pk", example = "3")
	Long id,
	@Schema(description = "일정 제목", example = "박철현 연차")
	String title,
	@Schema(description = "일정 설명", example = "박철현 연차")
	String description,
	@Schema(description = "일정 시작일", example = "2025-=07-01T12:00:00")
	LocalDateTime start,
	@Schema(description = "일정 종료일", example = "2025-=07-01T15:00:00")
	LocalDateTime end,
	@Schema(description = "해당 일자에서 연차 사용일", example = "0.5")
	double usedDays,
	@Schema(description = "연차 타입")
	LeaveType leaveType
) {
	public static LeaveDetailDto of(LeaveAndHoliday leaveAndHoliday, double usedDays) {

		return LeaveDetailDto.builder()
			.id(leaveAndHoliday.getId())
			.title(leaveAndHoliday.getTitle())
			.description(leaveAndHoliday.getDescription())
			.start(leaveAndHoliday.getStartDate())
			.end(leaveAndHoliday.getEndDate())
			.usedDays(usedDays)
			.leaveType(leaveAndHoliday.getLeaveType())
			.build();
	}
}
