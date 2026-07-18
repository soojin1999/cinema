package com.toy.cinema.booking.domain;

import com.toy.cinema.common.enums.BookingStatus;

import java.time.LocalDateTime;

/**
 * booking 한 행의 SELECT 결과 스냅샷. ScheduleSeat/Payment와 같은 역할 —
 * BookingService 내부(재조정 조회 등)에서만 쓰이고 Facade 밖으로 나가지 않는다.
 */
public record Booking(Long bookingId, Long scheduleId, Long seatId, String userId,
                       BookingStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
