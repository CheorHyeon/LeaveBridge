package com.leavebridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.codec.Encoder;
import feign.form.spring.SpringFormEncoder;

@Configuration
public class FeignConfig {
	@Bean
	public Encoder feignEncoder() {
		// SpringFormEncoder: FormEncoder + Spring의 HTTP 컨버터 지원
		return new SpringFormEncoder();
	}
}