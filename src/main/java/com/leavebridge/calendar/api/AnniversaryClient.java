package com.leavebridge.calendar.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.leavebridge.calendar.api.dto.RequestQueryParams;
import com.leavebridge.calendar.api.dto.ResponseWrapper;
import com.leavebridge.config.FeignConfig;

@FeignClient(
	name = "anniversaryApi",
	url  = "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService",
	configuration = FeignConfig.class  // Null 파람 무시
)
public interface AnniversaryClient {

	// 국경일 데이터
	@GetMapping(value = "/getHoliDeInfo", produces = "application/json")
	ResponseWrapper getHolidays(@SpringQueryMap RequestQueryParams params);

	// 공휴일 데이터
	@GetMapping(value = "/getRestDeInfo", produces = "application/json")
	ResponseWrapper getRestDeInfo(@SpringQueryMap RequestQueryParams params);

	// 기념일 데이터
	@GetMapping(value = "/getAnniversaryInfo", produces = "application/json")
	ResponseWrapper getAnniversaryInfo(@SpringQueryMap RequestQueryParams params);

	// 24절기 정보 조회
	@GetMapping(value = "/get24DivisionsInfo", produces = "application/json")
	ResponseWrapper get24DivisionsInfo(@SpringQueryMap RequestQueryParams params);

	// 잡절 정보 조회
	@GetMapping(value = "/getSundryDayInfo", produces = "application/json")
	ResponseWrapper getSundryDayInfo(@SpringQueryMap RequestQueryParams params);
}