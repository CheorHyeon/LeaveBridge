package com.leavebridge.member.dto;

import org.springframework.data.web.PagedModel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberUsedLeavesResponseDto {
	@Schema(description = "member Id", example = "3")
	Long memberId;
	@Schema(description = "member 이름", example = "박철현")
	String memberName;
	@Schema(description = "총 부여 연차", example = "15.0")
	double totalCount;
	@Schema(description = "총 사용 연차", example = "3.5")
	double totalUsedDays;
	@Schema(description = "총 남은 연차", example = "3.5")
	double remainingDays;
	@Schema(description = "개인별 연차 사용 현황 목록")
	PagedModel<LeaveDetailDto> leaveDetails;

	public MemberUsedLeavesResponseDto(Long memberId, String memberName, double totalCount, double totalUsedDays) {
		this(memberId, memberName, totalCount, totalUsedDays, totalCount - totalUsedDays, null);
	}

	public void updateLeaveDetails(PagedModel<LeaveDetailDto> leaveDetails) {
		this.leaveDetails = leaveDetails;
	}

}
