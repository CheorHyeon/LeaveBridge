package com.leavebridge.calendar.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.services.calendar.model.Event;
import com.leavebridge.calendar.service.CalendarService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {
	private final CalendarService calendarService;

	/**
	 * 공휴일 이벤트 조회
	 */
	@PostMapping("/holiday-events")
	public ResponseEntity<String> getUpcomingHolidaysEvents() throws Exception {
		calendarService.findHolidayFromGoogleCalendar();
		return ResponseEntity.ok("공휴일 데이터가 성공적으로 저장되었습니다.");
	}

	/**
	 * 이번달 구글 캘린더 등록 이벤트 조회
	 */
	@GetMapping("/events")
	public ResponseEntity<List<Event>> getUpcomingEvents() throws Exception {
		List<Event> events = calendarService.listMonthlyEvents(2025, 6);
		return ResponseEntity.ok(events);
	}

	/**
	 * 특정 이벤트 상세 조회
	 */
	@GetMapping("/events/{eventId}")
	public ResponseEntity<Event> getEventDetail(@PathVariable("eventId") String eventId) throws Exception {
		Event events = calendarService.getEventDetails(eventId);
		return ResponseEntity.ok(events);
	}

	/**
	 * 일정 등록
	 **/
	@PostMapping("/events")
	public ResponseEntity<Event> createHolidays() throws Exception {
		Event event = calendarService.createTimedEvent("박철현 연차", LocalDateTime.now(), LocalDateTime.now());
		return ResponseEntity.ok(event);
	}

	/**
	 * 특정 이벤트 수정
	 */
	@PatchMapping("/events/{eventId}")
	public ResponseEntity<Void> updateHolidays(@PathVariable("eventId") String eventId) throws IOException {
		calendarService.updateEventDate(eventId, LocalDateTime.now(), LocalDateTime.now());
		return ResponseEntity.ok().build();
	}

	/**
	 * 특정 이벤트 삭제
	 */
	@DeleteMapping("/events/{eventId}")
	public ResponseEntity<Void> deleteHolidays(@PathVariable("eventId") String eventId) throws IOException {
		calendarService.deleteEvent(eventId);
		return ResponseEntity.ok().build();
	}
}