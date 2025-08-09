package com.leavebridge.home;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.leavebridge.member.entitiy.MemberRole;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home(Model model, Authentication authentication) {
		boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
								  && !(authentication instanceof AnonymousAuthenticationToken);
		model.addAttribute("isAuthenticated", isAuthenticated);

		boolean isGermany = false;
		boolean isAdmin = false;
		if(isAuthenticated) {
			isGermany = authentication.getAuthorities().contains(MemberRole.ROLE_GERMANY);
			isAdmin = authentication.getAuthorities().contains(MemberRole.ROLE_ADMIN);
		}

		model.addAttribute("isGermany", isGermany);
		model.addAttribute("isAdmin", isAdmin);
		return "calendar/home";
	}

	@GetMapping("/health")
	@ResponseBody
	public String health() {
		return "ok";
	}
}
