package com.leavebridge.calendar.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.leavebridge.calendar.entity.LeaveAndHoliday;

@Repository
public interface LeaveAndHolidayRepository extends JpaRepository<LeaveAndHoliday, Long> {
	List<LeaveAndHoliday> findAllByGoogleEventIdIn(List<String> eventIds);
	List<LeaveAndHoliday> findAllByStartDateGreaterThanEqualAndStartDateLessThan(LocalDate start, LocalDate end);
}