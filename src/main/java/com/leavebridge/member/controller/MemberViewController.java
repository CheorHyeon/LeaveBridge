package com.leavebridge.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MemberViewController {

	@GetMapping("/members/login")
	public String login() {
		return "member/login";
	}

	@GetMapping("/members/me/password")
	public String changePassword() {
		return "member/change_password";
	}

	@GetMapping("/members/leaves/usage")
	public String membersLeavesUsage() {
		return "member/leaves_usage";
	}
}