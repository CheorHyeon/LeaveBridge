package com.leavebridge.member.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.leavebridge.member.dto.FindMemberListReponseDto;
import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
import com.leavebridge.member.dto.RequestChangePasswordRequest;
import com.leavebridge.member.dto.SignupRequestDto;
import com.leavebridge.member.entitiy.CustomMemberDetails;
import com.leavebridge.member.service.MemberService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/check-loginId")
	public ResponseEntity<Void> checkMemberName(@RequestParam(name = "loginId") String loginId) {
		memberService.checkMemberName(loginId);
		return ResponseEntity.ok().build();
	}

	@PostMapping("/signup")
	public ResponseEntity<Void> signupMember(@RequestBody SignupRequestDto requestDto) {
		memberService.signUpMember(requestDto);
		return ResponseEntity.ok().build();
	}

	@GetMapping
	public ResponseEntity<List<FindMemberListReponseDto>> findMemberList() {
		return ResponseEntity.ok(memberService.findMemberListForUsage());
	}

	@GetMapping("/{memberId}/used-leaves")
	public ResponseEntity<MemberUsedLeavesResponseDto> getUsedLeaves(
		@PathVariable("memberId") @Schema(description = "사용자 id", example = "3") Long memberId,
		@RequestParam(name = "year") @Schema(description = "조회할 년도", example = "2025") Integer year,
		@PageableDefault(size = 20, page = 0) Pageable pageable) {
		return ResponseEntity.ok(memberService.getMemberUsedLeaves(memberId, year, pageable));
	}

	@PatchMapping("/me/password")
	public ResponseEntity<Void> changePassword(
		@RequestBody @Valid RequestChangePasswordRequest requestChangePasswordRequest,
		@AuthenticationPrincipal CustomMemberDetails customMemberDetails) {
		log.info("MemberController :: changePassword memberId = {}", customMemberDetails.getUsername());
		memberService.changePassword(customMemberDetails.getMember(), requestChangePasswordRequest);
		return ResponseEntity.ok().build();
	}
}
