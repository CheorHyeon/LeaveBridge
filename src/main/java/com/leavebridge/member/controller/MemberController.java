package com.leavebridge.member.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
import com.leavebridge.member.dto.RequestChangePasswordRequest;
import com.leavebridge.member.entitiy.CustomMemberDetails;
import com.leavebridge.member.service.MemberService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/used-leaves")
	public ResponseEntity<List<MemberUsedLeavesResponseDto>> getUsedLeaves() {
		return ResponseEntity.ok(memberService.getMemberUsedLeaves());
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
