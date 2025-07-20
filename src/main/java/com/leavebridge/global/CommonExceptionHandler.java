package com.leavebridge.global;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class CommonExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<String> handleStatusEx(ResponseStatusException exception) {
		log.error("익셉션 발생 :: ", exception);
		return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException exception) {
		log.error("익셉션 발생 :: ", exception);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<String> handleAccessDenied(AccessDeniedException exception) {
		log.warn("익셉션 발생 :: ", exception);
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<String> handleParseError(HttpMessageNotReadableException exception) {
		log.error("익셉션 발생 :: ", exception);
		return ResponseEntity.badRequest().body("잘못된 요청 형식: " + exception.getMostSpecificCause().getMessage());
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<String> handleRuntimeException(RuntimeException exception) {
		log.error("익셉션 발생 :: ", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleAll(Exception exception) {
		log.error("익셉션 발생 ::", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 처리 중 오류가 발생했습니다.");
	}
}