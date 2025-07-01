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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.services.calendar.model.Event;
import com.leavebridge.calendar.dto.CreateLeaveRequestDto;
import com.leavebridge.calendar.dto.MonthlyEvent;
import com.leavebridge.calendar.dto.MonthlyEventDetailResponse;
import com.leavebridge.calendar.service.CalendarService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

	private final CalendarService calendarService;

	/**
	 * 이번달 구글 캘린더 등록 이벤트 조회
	 */
	@GetMapping("/events/{year}/{month}")
	public ResponseEntity<List<MonthlyEvent>> getUpcomingEvents(@PathVariable Integer year, @PathVariable Integer month) throws Exception {
		log.info("getUpcomingEvents :: year={}, month={}", year, month);
		List<MonthlyEvent> events = calendarService.listMonthlyEvents(year, month);
		return ResponseEntity.ok(events);
	}

	/**
	 * 특정 이벤트 상세 조회
	 */
	@GetMapping("/events/{eventId}")
	public ResponseEntity<MonthlyEventDetailResponse> getEventDetail(@PathVariable("eventId") Long eventId){
		return ResponseEntity.ok(calendarService.getEventDetails(eventId));
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