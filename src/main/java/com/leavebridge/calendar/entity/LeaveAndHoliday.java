package com.leavebridge.calendar.entity;

import java.time.LocalDateTime;

import com.google.api.services.calendar.model.Event;
import com.leavebridge.calendar.dto.CreateLeaveRequestDto;
import com.leavebridge.calendar.dto.PatchLeaveRequestDto;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.util.DateUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "LEAVE_AND_HOLIDAYS")
public class LeaveAndHoliday {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@Column(name = "TITLE", length = 100)
	private String title;

	@Column(name = "START_DATE")
	private LocalDateTime startDate;

	@Column(name = "END_DATE")
	private LocalDateTime endDate;

	@Column(name = "IS_ALL_DAY")
	private Boolean isAllDay;

	// TODO : User 테이블 설계 후 변경
	@Column(name = "USER_ID")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "LEAVE_TYPE", length = 50)
	private LeaveType leaveType;

	@Column(name = "GOOGLE_EVENT_ID")
	private String googleEventId;

	@Column(name = "DESCRIPTION")
	private String description;

	public static LeaveAndHoliday of(Event event, Long userId, LeaveType leaveType) {

		LocalDateTime start = DateUtils.parseDateTime(event.getStart(), true);
		LocalDateTime end = DateUtils.parseDateTime(event.getEnd(), false);
		boolean isAllDay = DateUtils.determineAllDay(event);

		return LeaveAndHoliday.builder()
			.title(event.getSummary())
			.startDate(start)
			.endDate(end)
			.isAllDay(isAllDay)
			.userId(userId)
			.leaveType(leaveType)
			.googleEventId(event.getId())
			.description(event.getDescription())
			.build();
	}

	public static LeaveAndHoliday of(CreateLeaveRequestDto requestDto, long userId, String googleCalendarId) {

		boolean isAllDay = DateUtils.determineAllDay(requestDto);

		return LeaveAndHoliday.builder()
			.title(requestDto.title())
			.startDate(requestDto.startDate())
			.endDate(requestDto.endDate())
			.isAllDay(isAllDay)
			.userId(userId)
			.leaveType(requestDto.leaveType())
			.googleEventId(googleCalendarId)
			.description(requestDto.description())
			.build();
	}

	public void patchEntityByDto(PatchLeaveRequestDto dto) {
		if (dto.title() != null) {
			this.title = dto.title();
		}
		if (dto.startDate() != null) {
			this.startDate = dto.startDate();
		}
		if (dto.endDate() != null) {
			this.endDate = dto.endDate();
		}
		if (dto.leaveType() != null) {
			this.leaveType = dto.leaveType();
		}
		if (dto.description() != null) {
			this.description = dto.description();
		}
		boolean isAllDay = DateUtils.determineAllDay(dto);
		this.isAllDay = isAllDay;
	}
}
