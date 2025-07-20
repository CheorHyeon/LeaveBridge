package com.leavebridge.member.dto;

import java.time.LocalDate;
import java.time.LocalTime;

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
	@Schema(description = "일정 시작일", example = "2025-=07-01")
	LocalDate startDate,
	@Schema(description = "일정 시작 시간", example = "12:00:00")
	LocalTime startTime,
	@Schema(description = "일정 종료일", example = "2025-=07-03")
	LocalDate endDate,
	@Schema(description = "일정 종료 시간", example = "12:00:00")
	LocalTime endTime,
	@Schema(description = "해당 일자에서 연차 사용일", example = "0.5")
	double usedDays,
	@Schema(description = "비고", example = "부분 휴일으로 240분 연차 미차감")
	String comment,
	@Schema(description = "연차 타입")
	String leaveType
) {
	public LeaveDetailDto(Long id, String title, String description, LocalDate startDate, LocalTime startTime,
		LocalDate endDate, LocalTime endTime, double usedDays, String comment, LeaveType leaveType) {
		this(id, title, description, startDate, startTime, endDate, endTime, usedDays, comment, leaveType.getType());
	}
}
