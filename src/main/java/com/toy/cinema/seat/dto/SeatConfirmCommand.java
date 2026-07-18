package com.toy.cinema.seat.dto;

/**
 * SeatFacade.confirm()의 파라미터. Saga 3단계 성공 경로(seatFacade.confirm() → bookingService.confirm()
 * → notificationFacade.send())에서 booking 도메인이 호출한다. HELD -> BOOKED 전이에 사용.
 */
public record SeatConfirmCommand(Long scheduleId, Long seatId) {
}
