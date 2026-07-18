package com.toy.cinema.common.exception;

/**
 * 모든 비즈니스 예외의 베이스. RuntimeException(unchecked)을 상속하는 이유:
 * Spring @Transactional은 기본적으로 unchecked 예외에서만 자동 롤백하기 때문에,
 * 이 계층의 예외가 @Transactional(REQUIRES_NEW) 메서드 안에서 던져지면 그 트랜잭션이 자동 롤백된다.
 * 나중에 컨트롤러 레벨 공통 예외 처리를 붙일 때도 이 타입 하나만 잡으면 하위 타입을 전부 포괄한다.
 */
public class CinemaException extends RuntimeException {

    public CinemaException(String message) {
        super(message);
    }

    public CinemaException(String message, Throwable cause) {
        super(message, cause);
    }
}
