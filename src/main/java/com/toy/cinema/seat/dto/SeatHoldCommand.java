package com.toy.cinema.seat.dto;

/**
 * SeatFacade.hold()의 파라미터. booking 도메인(BookingOrchestrator)이 만들어서 넘긴다.
 * hold/confirm/release는 시점·호출자가 다른 독립된 명령이라 dto를 공유하지 않고 따로 둔다
 * (지금은 필드가 같아도, 나중에 hold에만 필요한 필드가 생겨도 다른 두 dto엔 영향 없음).
 */
public record SeatHoldCommand(Long scheduleId, Long seatId) {
}
