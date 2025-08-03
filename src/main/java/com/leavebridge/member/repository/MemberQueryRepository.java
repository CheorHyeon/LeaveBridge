package com.leavebridge.member.repository;

import static com.leavebridge.calendar.entity.QLeaveAndHoliday.*;
import static com.leavebridge.member.entitiy.Member.*;
import static com.leavebridge.member.entitiy.QMember.*;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Repository;

import com.leavebridge.member.dto.FindMemberListReponseDto;
import com.leavebridge.member.dto.LeaveDetailDto;
import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
import com.leavebridge.member.entitiy.Member;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemberQueryRepository {

	private final JPAQueryFactory queryFactory;

	/**
	 * 1) 전체 통계용 DTO(MemberUsedLeavesResponseDto) Projections
	 */
	public MemberUsedLeavesResponseDto fetchMemberStats(Member targetMember, int year) {
		return queryFactory
			.select(Projections.constructor(MemberUsedLeavesResponseDto.class,
				Expressions.constant(targetMember.getId()),
				Expressions.constant(targetMember.getName()),
				Expressions.constant(15.0),
				leaveAndHoliday.usedLeaveDays.sum()
			))
			.from(leaveAndHoliday)
			.where(
				leaveAndHoliday.member.id.eq(targetMember.getId())
					.and(leaveAndHoliday.startDate.year().eq(year))
			)
			.fetchOne();
	}

	/**
	 * 2) 페이징된 LeaveDetailDto 페이지 조회
	 */
	public PagedModel<LeaveDetailDto> findLeaveDetails(Member targetMember, Integer year, Pageable pageable) {
		List<LeaveDetailDto> content = queryFactory
			.select(Projections.constructor(LeaveDetailDto.class,
				leaveAndHoliday.id,
				leaveAndHoliday.title,
				leaveAndHoliday.description,
				leaveAndHoliday.startDate,
				leaveAndHoliday.starTime,
				leaveAndHoliday.endDate,
				leaveAndHoliday.endTime,
				leaveAndHoliday.usedLeaveDays,
				leaveAndHoliday.comment,
				leaveAndHoliday.leaveType
			))
			.from(leaveAndHoliday)
			.where(
				leaveAndHoliday.member.id.eq(targetMember.getId())
					.and(leaveAndHoliday.startDate.year().eq(year))
			)
			.orderBy(leaveAndHoliday.startDate.asc())
			.offset(pageable.getOffset())
			.limit(pageable.getPageSize())
			.fetch();

		// total count
		JPAQuery<Long> total = queryFactory.select(leaveAndHoliday.count())
			.from(leaveAndHoliday)
			.where(
				leaveAndHoliday.member.id.eq(targetMember.getId())
					.and(leaveAndHoliday.startDate.year().eq(year))
			);

		// content paged
		return new PagedModel<>(PageableExecutionUtils.getPage(content, pageable, total::fetchOne));
	}

	public List<FindMemberListReponseDto> findAllMembersNotIncludeAdmin() {
		return queryFactory.select(Projections.constructor(FindMemberListReponseDto.class,
				member.id,
				member.name))
			.from(member)
			.where(member.id.ne(ADMIN_ID))
			.fetch();
	}
}
