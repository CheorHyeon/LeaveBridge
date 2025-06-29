package com.leavebridge.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.leavebridge.member.entitiy.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
	Optional<Member> findByLoginId(String username);
}
