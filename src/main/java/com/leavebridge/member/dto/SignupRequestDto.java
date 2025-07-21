package com.leavebridge.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupRequestDto(
	@Schema(description = "사용자 이름", example = "박철현")
	String memberName,
	@Schema(description = "로그인 Id", example = "puar12")
	String loginId,
	@Schema(description = "비밀번호", example = "qwer1234")
	String password,
	@Schema(description = "비밀번호 확인", example = "qwer1234")
	String confirmPassword,
	@Schema(description = "아이디 중복 확인 했는지 여부", example = "true or false")
	Boolean isUsernameAvailable
) {
}
