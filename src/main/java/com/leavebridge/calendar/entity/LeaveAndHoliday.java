package com.leavebridge.calendar.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.google.api.services.calendar.model.Event;
import com.leavebridge.calendar.dto.CreateLeaveRequestDto;
import com.leavebridge.calendar.dto.PatchLeaveRequestDto;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.member.entitiy.Member;
import com.leavebridge.util.DateUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "LEAVE_AND_HOLIDAYS")
@EntityListeners(AuditingEntityListener.class)
public class LeaveAndHoliday {

	// 상수들 모아두기
	public static final LocalTime WORK_START_TIME = LocalTime.of(8, 0);
	public static final LocalTime WORK_END_TIME = LocalTime.of(17, 0);
	public static final LocalTime LUNCH_START = LocalTime.NOON;        // 12:00
	public static final LocalTime LUNCH_END = LocalTime.of(13, 0);   // 13:00

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@Column(name = "TITLE", length = 100)
	private String title;

	@Column(name = "START_DATE")
	private LocalDate startDate;

	@Column(name = "START_TIME")
	private LocalTime starTime;

	@Column(name = "END_DATE")
	private LocalDate endDate;

	@Column(name = "END_TIME")
	private LocalTime endTime;

	@Column(name = "IS_ALL_DAY")
	private Boolean isAllDay;

	@JoinColumn(name = "MEMBER_ID")
	@ManyToOne(fetch = FetchType.LAZY)
	@ToString.Exclude
	private Member member;

	/* 한번에 연차 1개의 타입만 사용 가능 (여러개면 별도로 만들게) */
	@Enumerated(EnumType.STRING)
	@Column(name = "LEAVE_TYPE", length = 50)
	private LeaveType leaveType;

	@Column(name = "GOOGLE_EVENT_ID")
	private String googleEventId;

	@Column(name = "DESCRIPTION")
	private String description;

	@Column(name = "CREATED_DATE")
	@CreatedDate
	private LocalDateTime createdDate = LocalDateTime.now();

	@Column(name = "UPDATED_DATE")
	@LastModifiedDate
	private LocalDateTime updatedDate;

	@Column(name = "IS_HOLIDAY")
	private Boolean isHoliday;

	@Column(name = "USED_LEAVE_HOURS")
	private Double usedLeaveHours;  // 차감 연차 시간

	@Column(name = "COMMENT")
	private String comment;  // 연차 미차감 사유

	public static LeaveAndHoliday of(Event event, Member member, LeaveType leaveType) {

		LocalDateTime start = DateUtils.parseDateTime(event.getStart(), true);
		LocalDateTime end = DateUtils.parseDateTime(event.getEnd(), false);
		boolean isAllDay = DateUtils.determineAllDay(event);

		return LeaveAndHoliday.builder()
			.title(event.getSummary())
			.startDate(start.toLocalDate())
			.starTime(start.toLocalTime())
			.endDate(end.toLocalDate())
			.endTime(end.toLocalTime())
			.isAllDay(isAllDay)
			.member(member)
			.leaveType(leaveType)
			.googleEventId(event.getId())
			.description(event.getDescription())
			.build();
	}

	public static LeaveAndHoliday of(CreateLeaveRequestDto requestDto, Member member, String googleCalendarId) {

		LocalDateTime starDateTime = DateUtils.makeLocalDateTimeFromLocalDAteAndLocalTime(requestDto.startDate(), requestDto.startTime());
		LocalDateTime endDateTime = DateUtils.makeLocalDateTimeFromLocalDAteAndLocalTime(requestDto.endDate(), requestDto.endTime());

		return LeaveAndHoliday.builder()
			.title(requestDto.title())
			.startDate(starDateTime.toLocalDate())
			.starTime(starDateTime.toLocalTime())
			.endDate(endDateTime.toLocalDate())
			.endTime(endDateTime.toLocalTime())
			.isAllDay(requestDto.isAllDay())
			.member(member)
			.leaveType(requestDto.leaveType())
			.googleEventId(googleCalendarId)
			.description(requestDto.description())
			.build();
	}

	public void patchEntityByDto(PatchLeaveRequestDto dto) {
		if (dto.title() != null) {
			this.title = dto.title();
		}

		if(dto.isAllDay() != null) {
			this.isAllDay = dto.isAllDay();
		}

		if (dto.description() != null) {
			this.description = dto.description();
		}

		if (dto.startDate() != null) {
			this.startDate = dto.startDate();
		}

		if(dto.startTime() != null) {
			this.starTime = dto.startTime();
		}

		if (dto.endDate() != null) {
			this.endDate = dto.endDate();
		}

		if(dto.endTime() != null) {
			this.endTime = dto.endTime();
		}

		// 설명은 null로 고칠수도 있으니 열어둠
		this.leaveType = dto.leaveType();
	}

	// 최초 생성 시 updatedDate는 null로
	@PrePersist // 영속화 되기 직전 한번만 실행
	public void onPrePersist() {
		this.updatedDate = null;
	}

	// 프록시 객체 (leaveAndHoliday 속 member) 는 id는 가지고 있으니 쿼리 없이 검사하기 위함
	public boolean isOwnedBy(Member member) {
		return member != null && this.member.getId().equals(member.getId());
	}

	public boolean canModifyLeave(){
		return !leaveType.equals(LeaveType.PUBLIC_HOLIDAY) && !leaveType.equals(LeaveType.OTHER_PEOPLE);
	}

	public void updateIsHoliday(Boolean isHoliday) {
		this.isHoliday = Boolean.TRUE.equals(isHoliday);
	}

	public void updateUsedLeaveHours(Double usedLeaveHours) {
		this.usedLeaveHours = usedLeaveHours;
	}

	public void updateComment(String comment) {
		this.comment = comment;
	}
}
