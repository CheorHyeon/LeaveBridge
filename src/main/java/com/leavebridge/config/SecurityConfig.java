package com.leavebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/*/member/login").permitAll() // 로그인은 누구나 가능
				.requestMatchers(HttpMethod.GET, "/").permitAll() // 메인 페이지 누구나 가능
				// 예외 처리 로직 없을 때 Tomcat이 포워딩 하는 경로 접근 허용하여 예외 메세지 잘 전달되도록 허용
				.requestMatchers("/error").permitAll()
				.anyRequest().permitAll() // 나머지는 인증된 사용자만 가능
			)

			.logout(
				logout -> logout
					.logoutSuccessUrl("/")
					.invalidateHttpSession(true)
					.deleteCookies("JSESSIONID")
			)

			.formLogin(
				formLogin -> formLogin
				.defaultSuccessUrl("/")
			)
		;
		return http.build();
	}
}