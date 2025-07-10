package com.leavebridge.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RequestChangePasswordRequest(
	@Schema(description = "현재 비밀번호", example = "currentPassword123!")
	@NotBlank(message = "현재 비밀번호를 입력하세요")
	String currentPassword,

	@Schema(description = "새 비밀번호", example = "newPassword456!")
	@NotBlank(message = "새 비밀번호를 입력하세요")
	String newPassword,

	@Schema(description = "비밀번호 확인", example = "newPassword456!")
	@NotBlank(message = "비밀번호 확인을 입력하세요")
	String confirmPassword
) { }