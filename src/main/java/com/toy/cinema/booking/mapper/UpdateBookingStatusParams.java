package com.toy.cinema.booking.mapper;

import com.toy.cinema.common.enums.BookingStatus;

/** BookingMapper.updateStatus 전용 파라미터. confirm/cancel이 공용으로 사용한다. */
public record UpdateBookingStatusParams(Long bookingId, BookingStatus status) {
}
