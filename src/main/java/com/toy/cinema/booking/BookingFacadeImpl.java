package com.toy.cinema.booking;

import com.toy.cinema.booking.dto.BookingResult;
import com.toy.cinema.booking.dto.ReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** BookingFacade의 현재 구현체. 실제 로직은 전부 BookingOrchestrator에 위임한다. */
@Component
@RequiredArgsConstructor
public class BookingFacadeImpl implements BookingFacade {

    private final BookingOrchestrator bookingOrchestrator;

    @Override
    public Long reserve(ReservationRequest request) {
        return bookingOrchestrator.reserve(request);
    }

    @Override
    public BookingResult getResult(Long bookingId) {
        return bookingOrchestrator.getResult(bookingId);
    }
}
