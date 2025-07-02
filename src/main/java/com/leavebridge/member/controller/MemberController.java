package com.leavebridge.member.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.leavebridge.member.dto.MemberUsedLeavesResponseDto;
import com.leavebridge.member.service.MemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
@Slf4j
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/used-leaves")
	public ResponseEntity<List<MemberUsedLeavesResponseDto>> getUsedLeaves() {
		return ResponseEntity.ok(memberService.getMemberUsedLeaves());
	}
}
