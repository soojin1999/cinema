package com.toy.cinema.booking.dto;

import com.toy.cinema.booking.LockType;

/**
 * BookingFacade.reserve()의 파라미터. BookingController가 요청 파라미터를 바인딩해서 만든다.
 * scheduleId/seatId처럼 같은 타입(Long)이 여럿이라 원시값 나열 대신 dto로 묶음 (AGENT.md 코딩 컨벤션).
 */
public record ReservationRequest(Long scheduleId, Long seatId, String userId, Integer amount, LockType lockType) {
}
