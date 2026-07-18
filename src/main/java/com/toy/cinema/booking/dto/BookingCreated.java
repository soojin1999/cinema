package com.toy.cinema.booking.dto;

/** POST /bookings 응답 바디. BookingFacade.reserve()는 Long만 반환하므로 컨트롤러가 JSON 계약용으로 감싼다. */
public record BookingCreated(Long bookingId) {
}
