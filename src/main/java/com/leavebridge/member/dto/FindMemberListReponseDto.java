package com.leavebridge.member.dto;

import com.leavebridge.member.entitiy.Member;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record FindMemberListReponseDto(
	@Schema(description = "사용자 Id", example = "3")
	Long memberId,
	@Schema(description = "사용자 이름", example = "박철현")
	String memberName
) {
	public static FindMemberListReponseDto of(Member member) {
		return FindMemberListReponseDto.builder().memberId(member.getId()).memberName(member.getName()).build();
	}
}
