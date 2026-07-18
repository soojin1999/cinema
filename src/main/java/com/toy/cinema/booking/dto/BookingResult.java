package com.toy.cinema.booking.dto;

import com.toy.cinema.common.enums.BookingStatus;

/** BookingFacade.getResult()의 리턴 타입. GET /bookings/{id} 결과 화면이 표시할 최소 정보. */
public record BookingResult(Long bookingId, BookingStatus status) {
}
