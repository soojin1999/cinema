package com.toy.cinema.booking.mapper;

/** BookingMapper.insertPending 전용 파라미터. status는 항상 PENDING으로 시작하므로 SQL에 고정한다. */
public record InsertBookingParams(Long scheduleId, Long seatId, String userId) {
}
