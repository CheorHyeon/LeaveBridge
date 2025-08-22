package com.leavebridge.slack.scheduler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.leavebridge.slack.client.SlackApiClient;
import com.leavebridge.slack.service.BusinessDayService;
import com.leavebridge.slack.service.ReminderSkipService;
import com.slack.api.methods.SlackApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableScheduling
@Component
@RequiredArgsConstructor
public class SlackScheduler {

	private final SlackApiClient slackApiClient;
	private final BusinessDayService businessDayService;
	private final ReminderSkipService skipService;

	private static final DateTimeFormatter KOR = DateTimeFormatter.ofPattern("yyyy년 M월 d일(E)", Locale.KOREAN);

	@Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
	public void morningReminder() {
		LocalDate today = LocalDate.now();

		/**
		 * 1. 안보내는 경우 사전 차단 : 휴일 + 휴일 전날 오후 4시에 보낸 경우
		 */

		// 휴일엔 안보냄
		if (!businessDayService.isBusinessDay(today)) {
			return;
		}

		// ✅ 전날 16시에 선보냈던 케이스를 위해 '오늘 아침' 스킵 여부 확인
		// ex) 금요일 16시 보냄 -> 월요일 8시엔 안보냄
		if (skipService.shouldSkipMorning(today)) {
			return;
		}

		/**
		 * 2. 실제 비즈니스 로직, 전 근무일 꺼내옴
		 */
		LocalDate target = businessDayService.findLastBusinessDayBefore(today);
		String text = """
			🪖 *독일광부 작업반장 알림* ⛏️
			좋은 아침입니다! 🙌
			*%s* 에 하신 작업을 *이 메시지에 스레드 댓글* 로 간단히 남겨주세요.
			안전모 단단히! 작은 전진이 큰 성과를 만듭니다.🤝
			""".formatted(target.format(KOR));
		try {
			slackApiClient.sendMessage(text);
		} catch (Exception e) {
			// 실패: sentToday 를 건드리지 않음 → 백업 스케줄/수동 재시도 기회 보장
			log.warn("Slack 메시지 전송 실패 cause={}", e.toString());
		}
	}

	/**
	 * 이전 날짜들의 스킵 날짜 DB 정리
	 **/
	@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
	@Transactional
	public void midnightClean() {
		skipService.cleanupOldSkipDate(LocalDate.now());
	}

	/**
	 * 매일 16:00: '내일이 비근무일'이면 오늘자 공유 요청 + 다음 근무일 아침 스킵표 기록
	 */
	@Scheduled(cron = "0 0 16 * * *", zone = "Asia/Seoul")
	// @Scheduled(cron = "0 */1 * * * *") //1분마다 적용 확인을 위해 일단 달아둠
	@Transactional(rollbackFor = {Exception.class})
	public void preHolidayReminder() throws SlackApiException, IOException {
		LocalDate today = LocalDate.now();

		/**
		 * 1. 안보내는 경우 사전 차단 : 휴일 + 내일이 근무일인 경우(휴일이 아님)
		 */

		// 휴일엔 안보냄
		if (!businessDayService.isBusinessDay(today)) {
			return;
		}

		// 내일이 근무일이면 안보냄
		boolean isTomorrowBusiness = businessDayService.isBusinessDay(today.plusDays(1));
		if (isTomorrowBusiness) {
			log.info("Skip 4pm: tomorrow {} is business day", today.plusDays(1));
			return;
		}

		/**
		 * 2. 실제 비즈니스 로직, '내일이 휴일/주말' → 오늘 16시에 전송
		 */
		String text = """
			🪖 *독일광부 작업반장 알림* ⛏️
			오늘도 고생 많으셨습니다! 내일은 휴일이니 조금만 더 힘냅시다! 🙌
			오늘(*%s*) 진행하신 작업을 *이 메시지에 스레드 댓글* 로 정리해 주세요.
			- 내일 아침 및 월요일 또는 다음 근무일 아침 스킵 리마인더는 쉽니다.
			안전모 단단히, 오늘도 마무리 한 삽! 🤝
			""".formatted(today.format(KOR));
		try {
			// ➕ 휴일 다음 첫 근무일 아침은 스킵 처리
			LocalDate nextBiz = businessDayService.findNextBusinessDayAfter(today);
			skipService.saveSkipMorningAlarmDate(nextBiz, "휴일 전날 오후 4시에 보내서 이날은 안보냄");
			slackApiClient.sendMessage(text);
		} catch (Exception e) {
			log.warn("휴일 전날 4시 slack 메시지 전송 실패 메세지 : {}", e.toString());
			throw e;
		}
	}
}
