package com.leavebridge.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;

@Configuration
public class GoogleCalendarConfig {
	private static final String APPLICATION_NAME = "LeaveBridge";
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final String TOKENS_DIR = "tokens";
	private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
	private static final String CREDENTIALS_PATH = "/credentials.json";

	@Bean
	public Calendar calendarClient() throws Exception {
		// 1) HTTP transport 생성
		final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

		// 2) credentials.json 로드
		InputStream in = getClass().getResourceAsStream(CREDENTIALS_PATH);
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
			JSON_FACTORY, new InputStreamReader(in));

		// 3) OAuth 흐름 구성
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
			httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
			.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIR)))
			.setAccessType("offline")
			.build();

		// 4) 로컬 서버로 인증 코드 수신
		LocalServerReceiver receiver = new LocalServerReceiver.Builder()
			.setPort(8888)
			.build();

		// 5) 사용자 인증 및 Credential 생성
		Credential credential = new AuthorizationCodeInstalledApp(flow, receiver)
			.authorize("user");

		// 6) Calendar 클라이언트 빌드 - 사용자가 소유하거나 구독중인 캘린더
		return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
			.setApplicationName(APPLICATION_NAME)
			.build();
	}
}