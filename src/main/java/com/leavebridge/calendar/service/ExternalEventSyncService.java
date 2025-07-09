package com.leavebridge.calendar.service;

import static com.leavebridge.calendar.scheduler.CalendarScheduler.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.services.calendar.model.Event;
import com.leavebridge.calendar.api.AnniversaryClient;
import com.leavebridge.calendar.api.dto.RequestQueryParams;
import com.leavebridge.calendar.api.dto.ResponseWrapper;
import com.leavebridge.calendar.entity.LeaveAndHoliday;
import com.leavebridge.calendar.enums.LeaveType;
import com.leavebridge.calendar.repository.LeaveAndHolidayRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalEventSyncService {
	private final AnniversaryClient client;
	private final LeaveAndHolidayRepository leaveAndHolidayRepository;

	@Value("${data.secret-key}")
	private String apiKey;

	record HolidayKey(LocalDate start, LocalDate end, String title) {
	}

	/**
	 * 다음 n년치(현재 연도 포함)를 한 번에 동기화
	 */
	@Transactional
	public List<LeaveAndHoliday> syncNextYears(int yearsAhead) throws IOException {

		int thisYear = LocalDate.now().getYear();
		List<LeaveAndHoliday> targetSaveEntities = new ArrayList<>();
		for (int year = thisYear; year < thisYear + yearsAhead; year++) {
			targetSaveEntities.addAll(fetchAndSaveAllKinds(year));
		}
		return removeDuplicatesEntriesAndSorted(targetSaveEntities);
	}

	private List<LeaveAndHoliday> removeDuplicatesEntriesAndSorted(List<LeaveAndHoliday> targetSaveEntities) {
		// 1) 빈 Map 생성
		Map<HolidayKey, LeaveAndHoliday> dedupeMap = new HashMap<>();

		// 2) targetSaveEntities 순회하면서 Map 에 넣기
		for (LeaveAndHoliday h : targetSaveEntities) {
			HolidayKey key = new HolidayKey(
				h.getStartDate(),
				h.getEndDate(),
				h.getTitle()
			);
			// key 가 없을 때만 put, 이미 있으면 무시
			dedupeMap.putIfAbsent(key, h);
		}

		// 3) Map.values() 로 리스트화
		List<LeaveAndHoliday> dedupedList = new ArrayList<>(dedupeMap.values());

		return dedupedList.stream()
			.sorted(Comparator.comparing(LeaveAndHoliday::getStartDate))
			.toList();
	}

	private List<LeaveAndHoliday> fetchAndSaveAllKinds(int year) throws IOException {
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		LocalDate yearEnd = LocalDate.of(year, 12, 31);

		// 1) 올해 시작일이 속한 모든 엔티티 미리 조회
		List<LeaveAndHoliday> existing =
			leaveAndHolidayRepository.findAllByStartDateBetween(yearStart, yearEnd);

		// 2) (startDate, endDate, title) 3-tuple 키 생성
		Set<HolidayKey> existingKeys = existing.stream()
			.map(h -> new HolidayKey(h.getStartDate(), h.getEndDate(), h.getTitle()))
			.collect(Collectors.toSet());

		// 3) API 호출
		Map<LeaveType, ResponseWrapper> fetchAllKindsEventThisYear = fetchAllKinds(year);

		List<LeaveAndHoliday> targetSaveEntities = new ArrayList<>();
		// 4) 변환, 필터, 저장엔티티 지정
		fetchAllKindsEventThisYear.forEach((leaveType, resp) -> {
			resp.response().body().items().item().stream()
				// 여기선 DTO인 item까지 유지해서 isHoliday 값을 가져올 수 있게 한다
				.map(item -> {
					LocalDate date = transLocdateToLocalDate(item);
					boolean holidayFlag = "Y".equalsIgnoreCase(item.isHoliday()); // 문자열 → boolean
					return new Object() {  // 익명 객체로 묶어서 전달
						final LocalDate start = date;
						final LocalDate end = date;
						final String title = item.dateName();
						final boolean isHoliday = holidayFlag;
						final LeaveType type = leaveType;
					};
				})
				// 기존에 없는 것만
				.filter(obj -> !existingKeys.contains(
					new HolidayKey(obj.start, obj.end, obj.title)
				))
				.forEach(obj -> {
					LeaveAndHoliday h = LeaveAndHoliday.builder()
						.title(obj.title)
						.startDate(obj.start)
						.starTime(LocalTime.MIN)
						.endDate(obj.end)
						.endTime(LocalTime.of(23, 59, 0))
						.leaveType(obj.type)
						.isHoliday(obj.isHoliday)
						.isAllDay(true) // 하루종일이니 트루
						.member(adminMember) // Id로 이뤄진 객체 넣어도 외래키로 잘 저장됨
						.description(obj.type.getType() + "   " + obj.title)
						.build();
					targetSaveEntities.add(h);
				});
		});

		return targetSaveEntities;
	}

	private static LocalDate transLocdateToLocalDate(ResponseWrapper.Item item) {
		// API locdate → LocalDate 변환
		int ld = item.locdate();
		return LocalDate.of(ld / 10000, (ld / 100) % 100, ld % 100);
	}

	/**
	 * 주어진 연도에 대해 5가지 API(국경일, 공휴일, 기념일, 24절기, 잡절)를 호출해서
	 * 각 ResponseWrapper를 리스트로 반환합니다.
	 */
	public Map<LeaveType, ResponseWrapper> fetchAllKinds(int year) {
		RequestQueryParams params = buildParams(year);

		Map<LeaveType, ResponseWrapper> map = new HashMap<>();

		map.put(LeaveType.PUBLIC_HOLIDAY, client.getRestDeInfo(params));
		map.put(LeaveType.NATIONAL_HOLIDAY, client.getHolidays(params));
		map.put(LeaveType.TWENTY_FOUR_SOLAR_TERMS, client.get24DivisionsInfo(params));
		map.put(LeaveType.SUNDRY_DAY, client.getSundryDayInfo(params));
		map.put(LeaveType.ANNIVERSARY, client.getAnniversaryInfo(params));

		return map;
	}

	private RequestQueryParams buildParams(int year) {
		return RequestQueryParams.builder()
			.serviceKey(apiKey)
			.solYear(String.valueOf(year))
			._type("json")
			.numOfRows("10000")
			.build();
	}

	@Transactional
	public void processAndSaveNewEvents(List<Event> events) {
		if (events.isEmpty()) {
			return;
		}

		// 1) 이벤트 ID 추출
		List<String> eventIds = extractEventIds(events);

		// 2) DB에 이미 있는 ID 조회
		List<String> existingIds = findExistingEventIds(eventIds);

		// 3) 신규 이벤트만 매핑 후 저장
		List<Event> targetSaveList = events.stream()
			.filter(e -> !existingIds.contains(e.getId()))
			.toList();

		// 4) 구글 캘린더에만 있는 일정은 다 비회원 일정으로 취급
		List<LeaveAndHoliday> newEntityList = targetSaveList.stream()
			.map(e -> LeaveAndHoliday.of(e, adminMember, LeaveType.OTHER_PEOPLE))
			.sorted(Comparator.comparing(LeaveAndHoliday::getStartDate))
			.toList();

		leaveAndHolidayRepository.saveAll(newEntityList);
	}

	/**
	 * 이벤트 리스트에서 Google Event ID만 추출
	 */
	private List<String> extractEventIds(List<Event> events) {
		return events.stream()
			.map(Event::getId)
			.toList();
	}

	/**
	 * DB에 이미 저장된 Google Event ID 목록 조회
	 */
	private List<String> findExistingEventIds(List<String> eventIds) {
		return leaveAndHolidayRepository
			.findAllByGoogleEventIdIn(eventIds)
			.stream()
			.map(LeaveAndHoliday::getGoogleEventId)
			.toList();
	}

}
