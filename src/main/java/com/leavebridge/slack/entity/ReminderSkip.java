package com.leavebridge.slack.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "REMINDER_SKIP")
@EntityListeners(AuditingEntityListener.class)
public class ReminderSkip {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@Column(name = "SKIP_DATE")
	private LocalDate skipDate;

	@Column(name = "REASON")
	private String reason;

	@Column(name = "CREATED_DATE")
	@CreatedDate
	@Builder.Default
	private LocalDateTime createdDate = LocalDateTime.now();

	public static ReminderSkip of(LocalDate date, String reason) {
		return ReminderSkip.builder()
			.skipDate(date)
			.reason(reason)
			.build();
	}
}
