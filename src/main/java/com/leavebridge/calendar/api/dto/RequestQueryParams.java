package com.leavebridge.calendar.api.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Value;

@Getter // @SpringQueryMap은 빈(Bean) 프로퍼티의 getter를 통해 각 필드 값을 읽어서 쿼리 파라미터로 변환
@Builder
@Value // 왠지 모르겠는데 무조건 이녀석 있어야 정상 동작..
public class RequestQueryParams {
	String serviceKey;
	String solYear;
	String solMonth;
	String _type;
	String numOfRows;
}