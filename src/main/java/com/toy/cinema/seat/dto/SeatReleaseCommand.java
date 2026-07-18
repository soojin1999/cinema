package com.toy.cinema.seat.dto;

/**
 * SeatFacade.release()의 파라미터 (보상 트랜잭션). 두 곳에서 호출될 수 있다:
 * ① BookingOrchestrator가 PaymentFailedException catch 시 (HELD -> AVAILABLE)
 * ② 나중에 HELD 타임아웃 배치(@Scheduled)가 호출 (Todo.md T-04)
 * SeatService.release()는 멱등적으로 구현되어 있어 중복 호출돼도 안전하다.
 */
public record SeatReleaseCommand(Long scheduleId, Long seatId) {
}
