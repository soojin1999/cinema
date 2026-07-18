package com.toy.cinema.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST API 공통 예외 처리. CinemaException 하나만 잡으면 하위 비즈니스 예외 전부를 포괄한다
 * (CinemaException 클래스 주석에 이미 예고돼 있던 지점). SeatNotAvailable/SeatConflict는
 * "요청은 정상이지만 지금 이 상태로는 처리할 수 없다"는 의미라 409 CONFLICT로 매핑한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({SeatNotAvailableException.class, SeatConflictException.class})
    public ResponseEntity<ErrorResponse> handleConflict(CinemaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(CinemaException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(CinemaException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }
}
