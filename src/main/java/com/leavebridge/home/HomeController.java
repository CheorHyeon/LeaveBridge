package com.leavebridge.home;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

	@GetMapping("/")
	public String home(Model model, Authentication authentication) {
		boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
								  && !(authentication instanceof AnonymousAuthenticationToken);
		model.addAttribute("isAuthenticated", isAuthenticated);
		return "calendar/home";
	}
}
