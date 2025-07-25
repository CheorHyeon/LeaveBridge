package com.leavebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // @PreAuthorize 사용하기 위해 필요
public class SecurityConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests((authorizeHttpRequests) -> authorizeHttpRequests
				// .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers("/", "/members/login", "/css/**", "/js/**").permitAll()
				.requestMatchers("/api/*/calendar/events/*/*").permitAll() // 일정 조회
				.requestMatchers("/members/login").permitAll() // 메인 페이지 누구나 가능
				.requestMatchers("/members/signup", "/api/*/members/signup").permitAll() // 회원가입 누구나 가능
				.requestMatchers("/usage").permitAll() // 연차 사용 현황 누구나 가능
				.requestMatchers("/error").permitAll()
				.anyRequest().permitAll() // 나머지는 인증된 사용자만 가능
			)
			.logout(
				logout -> logout
					.logoutUrl("/members/logout")
					.logoutSuccessUrl("/")
					.invalidateHttpSession(true)
					.deleteCookies("JSESSIONID")
			)

			.formLogin(
				formLogin -> formLogin
					.loginPage("/members/login")
					.permitAll()
					.defaultSuccessUrl("/", true)
			)
		;
		return http.build();
	}
}