package com.leavebridge.slack.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leavebridge.slack.entity.ReminderSkip;
import com.leavebridge.slack.repository.ReminderSkipRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReminderSkipService {
	private final ReminderSkipRepository reminderSkipRepository;

	public boolean shouldSkipMorning(LocalDate date) {
		return reminderSkipRepository.existsBySkipDate(date);
	}

	@Transactional
	public void saveSkipMorningAlarmDate(LocalDate date, String reason) {
		reminderSkipRepository.save(ReminderSkip.of(date,reason));
	}

	/** skip할 날짜 지났으면 DB에서 삭제 **/
	@Transactional
	public void cleanupOldSkipDate(LocalDate today) {
		reminderSkipRepository.deleteAllBySkipDateBefore(today);
	}
}