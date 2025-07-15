package com.leavebridge.calendar.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
		LocalDate startDate = leaveAndHoliday.getStartDate();
		LocalTime startTime = leaveAndHoliday.getStarTime();
		LocalDate endDate   = leaveAndHoliday.getEndDate();
		LocalTime endTime   = leaveAndHoliday.getEndTime();
		boolean allDay      = Boolean.TRUE.equals(leaveAndHoliday.getIsAllDay());

		LocalDateTime start = LocalDateTime.of(startDate, startTime);
		LocalDateTime end = LocalDateTime.of(endDate, endTime);
		if (allDay) {
			// all-day 이벤트는 end 날짜를 exclusive 처리하기 위해 +1일
			end = LocalDateTime.of(endDate.plusDays(1), LocalTime.MIDNIGHT);
		}

		return MonthlyEvent.builder()
			.id(leaveAndHoliday.getId())
			.title(leaveAndHoliday.getTitle())
			.start(start)
			.end(end)
			.allDay(allDay)
			.isHoliday(leaveAndHoliday.getIsHoliday())
			.build();
	}
}
