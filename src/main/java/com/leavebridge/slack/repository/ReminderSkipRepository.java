package com.leavebridge.slack.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leavebridge.slack.entity.ReminderSkip;

public interface ReminderSkipRepository extends JpaRepository<ReminderSkip, LocalDate> {
	boolean existsBySkipDate(LocalDate date);

	void deleteAllBySkipDateBefore(LocalDate today);
}