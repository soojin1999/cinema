package com.toy.cinema.common.exception;

/**
 * SeatService.holdOptimistic() 전용. 조회 시점엔 AVAILABLE이었지만,
 * version 조건부 UPDATE(updateStatusWithVersion)의 영향받은 행이 0일 때 던진다 —
 * 그 사이 다른 트랜잭션이 먼저 상태를 바꿨다는 뜻 (충돌감지형/낙관적 락에서만 발생하는 충돌).
 * 재시도 로직 없이 그대로 던진다 (확정, Todo.md T-06, 2026-07-24) — 좌석 hold는 배타적 자원이라
 * 이 충돌은 기술적 노이즈가 아니라 "이미 다른 사람이 가져간" 진짜 비즈니스 결과이기 때문.
 * 재시도해도 재조회 시 이미 HELD/BOOKED라 결국 SeatNotAvailableException으로 귀결된다.
 */
public class SeatConflictException extends CinemaException {

    public SeatConflictException(String message) {
        super(message);
    }
}
