package com.leavebridge.slack.client;

import java.io.IOException;

import com.slack.api.Slack;
import com.slack.api.SlackConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SlackApiClient {

	@Value("${slack.channel-id}")
	private String channelId;

	private final MethodsClient methods;

	public SlackApiClient(@Value("${slack.bot-token}") String token) {
		SlackConfig config = new SlackConfig();
		config.setPrettyResponseLoggingEnabled(true);  // Json 들여쓰기 등 적용하여 로깅 남김
		Slack slack = Slack.getInstance(config);
		this.methods = slack.methods(token);
	}

	@Retryable(
		maxAttempts = 3,
		backoff = @Backoff(delay = 2000, multiplier = 2.0),
		retryFor = { IOException.class, SlackApiException.class, IllegalStateException.class  }
	)
	public void sendMessage(String text) throws SlackApiException, IOException {
		ChatPostMessageResponse chatPostMessageResponse = methods.chatPostMessage(r -> r
			.channel(channelId)
			.text(text)
		);
		if(!chatPostMessageResponse.isOk()){
			throw new IllegalStateException("Slack error: " + chatPostMessageResponse.getError());
		}
	}

	@Recover
	public void recover(Exception e, String channelId, String text) {
		// 3회 모두 실패시 마지막으로 여기로 옴 (로그/알림 등)
		log.error("Slack send failed after retries. channel={}, text={}", channelId, text, e);
	}
}