package com.leavebridge.member.entitiy;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.security.core.GrantedAuthority;

import com.leavebridge.calendar.entity.LeaveAndHoliday;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "MEMBER")
public class Member {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "ID")
	private Long id;

	@Column(name = "NAME")
	private String name;

	@Column(name = "LOGIN_ID")
	private String loginId;

	@Column(name = "PASSWORD")
	@ToString.Exclude
	private String password;

	@OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
	@ToString.Exclude
	private List<LeaveAndHoliday> leaveAndHolidays;

	@Column(name = "MEMBER_ROLE")
	@Enumerated(EnumType.STRING)
	private MemberRole memberRole;

	@Column(name = "CREATED_DATE")
	@CreatedDate
	private LocalDateTime createdDate = LocalDateTime.now();

	@Column(name = "UPDATED_DATE")
	@LastModifiedDate
	private LocalDateTime updatedDate;

	public List<? extends GrantedAuthority> getGrantedAuthorities() {
		return List.of(memberRole);
	}

	// 최초 생성 시 updatedDate는 null로
	@PrePersist // 영속화 되기 직전 한번만 실행
	public void onPrePersist() {
		this.updatedDate = null;
	}
}
