package com.leavebridge.calendar.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.google.api.services.calendar.model.Event;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.util.DateUtils;

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
	private Boolean allDay;

	// TODO : User 테이블 설계 후 변경
	@Column(name = "USER_ID")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "LEAVE_TYPE", length = 50)
	private LeaveType leaveType;

	public static LeaveAndHoliday of (Event event, Long userId, LeaveType leaveType) {

		LocalDateTime start = DateUtils.parseDateTime(event.getStart(), true);
		LocalDateTime end   = DateUtils.parseDateTime(event.getEnd(), false);
		boolean isAllDay     = DateUtils.determineAllDay(event);

		return LeaveAndHoliday.builder()
			.title(event.getSummary())
			.startDate(start)
			.endDate(end)
			.allDay(isAllDay)
			.userId(userId)
			.leaveType(leaveType)
			.build();
	}
}
