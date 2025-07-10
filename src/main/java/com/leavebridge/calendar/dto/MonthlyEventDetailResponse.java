package com.leavebridge.calendar.dto;

import java.time.LocalDateTime;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record MonthlyEventDetailResponse(
	@Schema(description = "Entity id", example = "1")
	Long id,
	@Schema(description = "일정 제목", example = "박철현 연차")
	String title,
	@Schema(description = "시작 시간", example = "2025-07-01T14:00:00")
	LocalDateTime startDate,
	@Schema(description = "종료 시간", example = "2025-07-01T14:00:00")
	LocalDateTime endDate,
	@Schema(description = "하루 종일 이벤트 여부", example = "true or false")
	Boolean isAllDay,
	@Schema(description = "연차 종류", example = "HOLIDAY(공휴일), FULL_DAY_LEAVE(1일 연차), HALF_DAY_LEAVE(반차),  OUT_GOING(외출), SUMMER_VACATION(여름 휴가), OTHER_PERSON(비회원 연차)")
	LeaveType leaveType,
	@Schema(description = "일정 상세 설명", example = "일정 설명 텍스트")
	String description,
	@Schema(description = "일정 수정, 삭제 가능한지 여부", example = "true(수정 가능), false(수정 불가)")
	Boolean isOwner,
	@Schema(description = "휴일 여부", example = "true (휴일), false(휴일 아님)")
	Boolean isHoliday
) {
	public static MonthlyEventDetailResponse of(LeaveAndHoliday leaveAndHoliday, Boolean isOwer) {
		return MonthlyEventDetailResponse.builder()
			.id(leaveAndHoliday.getId())
			.title(leaveAndHoliday.getTitle())
			.startDate(LocalDateTime.of(leaveAndHoliday.getStartDate(), leaveAndHoliday.getStarTime()))
			.endDate(LocalDateTime.of(leaveAndHoliday.getEndDate(), leaveAndHoliday.getEndTime()))
			.isAllDay(leaveAndHoliday.getIsAllDay())
			.leaveType(leaveAndHoliday.getLeaveType())
			.description(leaveAndHoliday.getDescription())
			.isOwner(isOwer)
			.isHoliday(leaveAndHoliday.getIsHoliday())
			.build();
	}
}
