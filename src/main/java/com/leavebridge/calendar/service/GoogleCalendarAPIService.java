package com.leavebridge.calendar.service;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCalendarAPIService {
	public static final String DEFAULT_TIME_ZONE = "Asia/Seoul";
	private final Calendar calendarClient;

	@Value("${google.calendar-id}")
	private String CALENDAR_ID;

	// ─── 퍼블릭 API 메서드 ──────────────────────────────────────────────────────

	public Event createGoogleCalendarEvent(Event event) {
		return withGoogleCall(
			() -> calendarClient.events().insert(CALENDAR_ID, event).execute(),
			HttpStatus.BAD_REQUEST
		);
	}

	public Event getGoogleCalendarEventByGoogleEventId(String eventId) {
		return withGoogleCall(
			() -> calendarClient.events().get(CALENDAR_ID, eventId).execute(),
			HttpStatus.NOT_FOUND
		);
	}

	public void patchGoogleCalendarEventByEventIdAndEvent(String eventId, Event event) {
		// 람다에서 null을 반환
		withGoogleCall(
			() -> {
				calendarClient.events().patch(CALENDAR_ID, eventId, event).execute();
				return null;
			},
			HttpStatus.BAD_REQUEST
		);
	}

	public void deleteGoogleCalendarEvent(String eventId) {
		// 람다에서 null을 반환
		try {
			withGoogleCall(
				() -> {
					calendarClient.events().delete(CALENDAR_ID, eventId).execute();
					return null;
				},
				HttpStatus.NOT_FOUND
			);
		} catch (ResponseStatusException e) {
			int status = e.getStatusCode().value();
			if (status == 404 || status == 410) {
				log.info("이미 삭제된 이벤트로 판단, DB만 정리: eventId={}", eventId);
				return; // idempotent 성공 처리
			}
			throw e; // 나머지는 그대로 던짐
		}
	}

	// ─── 공통 예외 처리 헬퍼 ──────────────────────────────────────────────────────

	private <T> T withGoogleCall(Callable<T> googleCall, HttpStatus defaultStatus) {
		try {
			return googleCall.call();
		} catch (GoogleJsonResponseException ex) {
			int code = ex.getStatusCode();
			String message = ex.getDetails().getMessage();
			log.error("구글 캘린더 API 예외 발생 :: message = {}", message);
			String reason = switch (code) {
				case 400 -> "잘못된 요청입니다." + message;
				case 401 -> "Google Token이 만료되었습니다. 관리자에게 문의하세요";
				case 403 -> "Google Calendar API 한도 초과 등, 관리자에게 문의하세요";
				case 404 -> "이미 삭제되었거나 리소스를 찾을 수 없습니다.";
				case 409 -> "리소스 충돌이 발생했습니다.";
				case 410 -> "리소스가 삭제되었거나(syncToken/updatedMin 무효) 전체 동기화가 필요할 수 있습니다.";
				case 429 -> "요청이 너무 많습니다. 잠시 후 시도해주세요.";
				default -> "Google Calendar 오류: " + ex.getStatusMessage();
			};
			throw new ResponseStatusException(HttpStatus.valueOf(code), reason, ex);
		} catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "네트워크 오류로 Google Calendar에 연결할 수 없습니다.",
				ex);
		} catch (Exception ex) {
			throw new ResponseStatusException(
				defaultStatus, defaultStatus.getReasonPhrase(), ex
			);
		}
	}
}
