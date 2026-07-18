package com.toy.cinema.common.exception;

/** GlobalExceptionHandler가 만드는 JSON 에러 바디의 공통 형태. */
public record ErrorResponse(String message) {
}
