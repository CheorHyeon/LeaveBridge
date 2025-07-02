package com.leavebridge.member.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.leavebridge.member.entitiy.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
	Optional<Member> findByLoginId(String username);

	@Query("""
        select distinct m
        from Member m
        left join fetch m.leaveAndHolidays l
        where m.name not like '%마스터유저%'
        """)
	List<Member> findAllWithLeaves();
}
