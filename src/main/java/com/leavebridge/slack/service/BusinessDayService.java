package com.leavebridge.slack.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;

@Service
@RequiredArgsConstructor
public class BusinessDayService {

	private final LeaveAndHolidayRepository leaveAndHolidayRepository;

	public boolean isWeekend(LocalDate date) {
		DayOfWeek w = date.getDayOfWeek();
		return w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY;
	}

	public boolean isHoliday(LocalDate date) {
		// 오늘 날짜로 된 휴일이 존재하는지 여부(파견직은 기념일 휴일에 영향 안받기 때문에 기념일 제외)
		return leaveAndHolidayRepository
			.existsByStartDateLessThanEqualAndEndDateGreaterThanEqualAndIsHolidayTrueAndIsAllDayTrueAndLeaveTypeNot(
				date, date, LeaveType.ANNIVERSARY);
	}

	public boolean isBusinessDay(LocalDate d) {
		return !isWeekend(d) && !isHoliday(d);
	}

	/** today 이전에서 마지막 근무일을 찾음 (어제→전날→... 반복) */
	public LocalDate findLastBusinessDayBefore(LocalDate today) {
		LocalDate cursor = today.minusDays(1);
		while (!isBusinessDay(cursor)) {
			cursor = cursor.minusDays(1);
		}
		return cursor;
	}
}