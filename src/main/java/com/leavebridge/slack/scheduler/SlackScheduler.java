package com.leavebridge.slack.scheduler;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.leavebridge.slack.client.SlackApiClient;
import com.leavebridge.slack.service.BusinessDayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EnableScheduling
@Component
@RequiredArgsConstructor
public class SlackScheduler {

	private final SlackApiClient slackApiClient;
	private final BusinessDayService businessDayService;

	// 중복 전송 방지 (동일 인스턴스)
	private final AtomicBoolean sentToday = new AtomicBoolean(false);

	private static final DateTimeFormatter KOR = DateTimeFormatter.ofPattern("yyyy년 M월 d일(E)", Locale.KOREAN);

	// 실제 한 번 실행 로직 (여러 스케줄에서 공용 호출)
	public void trySendOnce() {
		LocalDate today = LocalDate.now();

		if (sentToday.get()) {
			log.info("이미 오늘 보냄. skip");
			return;
		}

		// 1) 오늘이 주말/공휴일이면 종료
		if (!businessDayService.isBusinessDay(today)) {
			log.info("Skip: today {} is not a business day", today);
			return;
		}

		// 2) 어제부터 거슬러 올라가며 마지막 근무일 찾기
		LocalDate target = businessDayService.findLastBusinessDayBefore(today);

		// (보내는 문구)
		String text = """
			🪖 *독일광부 작업반장 알림* ⛏️
			좋은 아침입니다! 🙌
			*%s*에 하신 작업을 *이 메시지에 스레드 댓글*로 간단히 남겨주세요.
			안전모 단단히! 작은 전진이 큰 성과를 만듭니다. 🤝
			""".formatted(target.format(KOR));

		try {
			// 전송 시도 (3회 재시도 내장)
			slackApiClient.sendMessage(text);

			// ✅ 성공한 경우에만 true로
			sentToday.set(true);
		} catch (Exception e) {
			// 실패: sentToday 를 건드리지 않음 → 백업 스케줄/수동 재시도 기회 보장
			log.warn("Slack 메시지 전송 실패 cause={}", e.toString());
		}
	}

	/**
	 * 매일 08:00 (Asia/Seoul) 1차 시도
	 */
	// 테스트용 1분마다
	// @Scheduled(cron = "0 0/1 * * * *", zone = "Asia/Seoul")
	@Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
	public void run0800() {
		trySendOnce();
	}

	/**
	 * (선택) 2차 시도: 08:10
	 */
	@Scheduled(cron = "0 10 8 * * *", zone = "Asia/Seoul")
	public void run0810() {
		trySendOnce();
	}

	/**
	 * 자정에 플래그 초기화
	 */
	@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
	@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0))
	public void resetFlag() {
		sentToday.set(false);
	}

}
