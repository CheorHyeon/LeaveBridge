package com.leavebridge.calendar.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;

@Repository
public interface LeaveAndHolidayRepository extends JpaRepository<LeaveAndHoliday, Long> {

	/**
	 * 주어진 leaveType에 대해 startDate가 year년 내에 하나라도 존재하는지 확인
	 */
	boolean existsByLeaveTypeAndStartDateBetween(LeaveType leaveType, LocalDateTime startOfYear, LocalDateTime endOfYear);
}