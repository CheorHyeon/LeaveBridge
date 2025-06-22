package com.leavebridge.calendar.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.leavebridge.calendar.service.CalendarService;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
	private final CalendarService calendarService;

	public CalendarController(CalendarService calendarService) {
		this.calendarService = calendarService;
	}

	/** 공휴일 이벤트 조회 */
	@GetMapping("/holiday-events")
	public ResponseEntity<List<Event>> getUpcomingHolidaysEvents() throws Exception {
		Events events = calendarService.findHolidayFromGoogleCalendar();
		return ResponseEntity.ok(events.getItems());
	}

	/** 이번달 구글 캘린더 등록 이벤트 조회 */
	@GetMapping("/events")
	public ResponseEntity<List<Event>> getUpcomingEvents() throws Exception {
		List<Event> events = calendarService.listMonthlyEvents(2025, 6);
		return ResponseEntity.ok(events);
	}
}