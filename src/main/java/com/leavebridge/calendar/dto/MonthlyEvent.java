package com.leavebridge.calendar.dto;

import java.time.LocalDateTime;

import com.leavebridge.calendar.entity.LeaveAndHoliday;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record MonthlyEvent(

	@Schema(description = "DB상 일정 id", example = "2")
	Long id,

	@Schema(description = "일정 제목", example = "박철현 연차")
	String title,

	@Schema(description = "시작 시간", example = "'2025-06-29T14:00:00'")
	LocalDateTime start,

	@Schema(description = "종료 시간", example = "2025-06-29T18:00:00")
	LocalDateTime end,

	@Schema(description = "하루 종일 일정인지", example = "true or false")
	boolean allDay,

	@Schema(description = "휴일인지 여부", example = "true or false")
	Boolean isHoliday
) {
	public static MonthlyEvent from(LeaveAndHoliday leaveAndHoliday) {
		return MonthlyEvent.builder()
			.id(leaveAndHoliday.getId())
			.title(leaveAndHoliday.getTitle())
			.start(LocalDateTime.of(leaveAndHoliday.getStartDate(), leaveAndHoliday.getStarTime()))
			.end(LocalDateTime.of(leaveAndHoliday.getEndDate(), leaveAndHoliday.getEndTime()))
			.allDay(leaveAndHoliday.getIsAllDay())
			.isHoliday(leaveAndHoliday.getIsHoliday())
			.build();
	}
}
