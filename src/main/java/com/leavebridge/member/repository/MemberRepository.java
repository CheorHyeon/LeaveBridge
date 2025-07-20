package com.leavebridge.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.leavebridge.member.entitiy.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
	Optional<Member> findByLoginId(String username);

	@Modifying
	@Query("UPDATE Member m SET m.password = :newPassword WHERE m.id = :memberId")
	void updatePassword(@Param("memberId") Long memberId, @Param("newPassword") String newPassword);
}
