package com.leavebridge.member.converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.leavebridge.member.entitiy.MemberRole;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class MemberRoleListConverter implements AttributeConverter<List<MemberRole>, String> {

	private static final String SPLIT_CHAR = ", ";

	// DB 저장 시: List<MemberRole> -> "ROLE_MEMBER, ROLE_ADMIN"
	@Override
	public String convertToDatabaseColumn(List<MemberRole> attribute) {
		if (attribute == null || attribute.isEmpty()) {
			return "";
		}
		return attribute.stream()
			.map(MemberRole::name)
			.collect(Collectors.joining(SPLIT_CHAR));
	}

	// 조회 시: "ROLE_MEMBER, ROLE_ADMIN" -> List<MemberRole>
	@Override
	public List<MemberRole> convertToEntityAttribute(String dbData) {
		if (dbData == null || dbData.isBlank()) {
			return Collections.emptyList();
		}
		return Arrays.stream(dbData.split(SPLIT_CHAR))
			.map(String::trim)
			.map(MemberRole::valueOf)
			.toList();
	}
}