package com.leavebridge.member.dto;

import java.util.List;

import com.leavebridge.member.entitiy.Member;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record MemberUsedLeavesResponseDto(
	@Schema(description = "member Id", example = "3")
	Long memberId,
	@Schema(description = "member 이름", example = "박철현")
	String memberName,
	@Schema(description = "총 잔여 연차", example = "12.0")
	double totalCount,     // 12
	@Schema(description = "총 사용 연차", example = "3.5")
	double usedDays,
	@Schema(description = "총 남은 연차", example = "3.5")
	double remainingDays,  // 7.5
	@Schema(description = "개인별 연차 사용 현황 목록")
	List<LeaveDetailDto> leaveDetails
) {
	public static MemberUsedLeavesResponseDto of(Member member, double totalCount, double usedDays, double remainingDays,
		List<LeaveDetailDto> leaveDetails) {
		return MemberUsedLeavesResponseDto.builder()
			.memberId(member.getId())
			.memberName(member.getName())
			.totalCount(totalCount)
			.usedDays(usedDays)
			.remainingDays(remainingDays)
			.leaveDetails(leaveDetails)
			.build();
	}
}
