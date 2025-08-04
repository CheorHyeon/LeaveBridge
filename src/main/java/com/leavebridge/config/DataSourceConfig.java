package com.leavebridge.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class DataSourceConfig {

	@Value("${spring.datasource.username}")
	private String dbUser;

	@Value("${spring.datasource.password}")
	private String dbPass;

	@Value("${spring.datasource.driver-class-name}")
	private String dbDriver;

	@Value("${spring.datasource.url}")
	private String url;

	@Bean
	@DependsOn("sshTunnelConfig")  // ❶ 반드시 SSH 터널 먼저 초기화
	public DataSource dataSource() {
		return DataSourceBuilder.create()                   // Spring Boot 기본 Builder 사용
			.driverClassName(dbDriver)
			.url(url)
			.username(dbUser)
			.password(dbPass)
			.build();
	}
}