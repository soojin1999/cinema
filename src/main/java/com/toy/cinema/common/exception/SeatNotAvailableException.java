package com.toy.cinema.common.exception;

/**
 * SeatService.holdPessimistic()/holdOptimistic()에서, 조회한 시점에 이미 좌석이
 * HELD/BOOKED 상태일 때 던진다. 이 시점엔 아직 booking이 생성되지 않았으므로
 * BookingOrchestrator가 별도로 보상 트랜잭션을 발동할 필요 없이 그대로 위로 버블업된다.
 * SeatConflictException과 달리 재시도해도 의미가 없다 (진짜로 자리가 없는 것).
 */
public class SeatNotAvailableException extends CinemaException {

    public SeatNotAvailableException(String message) {
        super(message);
    }
}
