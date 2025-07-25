package com.leavebridge.calendar.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;

@Repository
public interface LeaveAndHolidayRepository extends JpaRepository<LeaveAndHoliday, Long> {
	List<LeaveAndHoliday> findAllByGoogleEventIdIn(List<String> eventIds);
	List<LeaveAndHoliday> findAllByStartDateBetween(LocalDate yearStart, LocalDate yearEnd);

	@Query("""
		   SELECT l FROM LeaveAndHoliday l
		WHERE (l.isHoliday = false OR l.isHoliday IS NULL)
		      AND l.leaveType IN :consumesLeaveTypes
		      AND l.startDate <= :holidayEnd
		      AND l.endDate   >= :holidayStart
		""")
	List<LeaveAndHoliday> findAllConsumesLeaveByDateRange(
		@Param("holidayStart") LocalDate holidayStart,
		@Param("holidayEnd")   LocalDate holidayEnd,
		@Param("consumesLeaveTypes") List<LeaveType> consumesLeaveTypes
	);

	List<LeaveAndHoliday> findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayFalse(
		LocalDate endDate, LocalDate startDate);

	boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayTrueAndLeaveTypeNot(
		LocalDate date, LocalDate date1, LeaveType leaveType);

	boolean existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayTrue(LocalDate date, LocalDate date1);

	List<LeaveAndHoliday> findAllByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate monthEnd, LocalDate monthStart);
}