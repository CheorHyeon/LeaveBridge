package com.leavebridge.calendar.api.dto;

import java.util.List;

public record ResponseWrapper(
	Response response
) {
	public record Response(
		Header header,
		Body   body
	) {}

	public record Header(
		String resultCode,
		String resultMsg
	) {}

	public record Body(
		Items items     // 페이징 필드 제거
	) {}

	public record Items(
		List<Item> item
	) {}

	public record Item(
		Integer locdate,
		Integer seq,
		String  dateKind,
		String  isHoliday,
		String  dateName
	) {}
}