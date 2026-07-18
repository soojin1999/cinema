package com.toy.cinema.common.exception;

/**
 * SeatService.holdOptimistic() 전용. 조회 시점엔 AVAILABLE이었지만,
 * version 조건부 UPDATE(updateStatusWithVersion)의 영향받은 행이 0일 때 던진다 —
 * 그 사이 다른 트랜잭션이 먼저 상태를 바꿨다는 뜻 (충돌감지형/낙관적 락에서만 발생하는 충돌).
 * SeatNotAvailableException과 달리 "타이밍에서 졌을 뿐"이라 재시도 여지가 있다.
 * 지금은 재시도 로직 없이 그대로 던지기만 함 (재시도 정책은 Todo.md T-06에서 논의 예정).
 */
public class SeatConflictException extends CinemaException {

    public SeatConflictException(String message) {
        super(message);
    }
}
