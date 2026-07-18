package com.toy.cinema.booking;

import com.toy.cinema.booking.dto.BookingCreated;
import com.toy.cinema.booking.dto.BookingResult;
import com.toy.cinema.booking.dto.ReservationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON REST 진입점. BookingOrchestrator를 직접 참조하지 않고 BookingFacade를 통해서만 호출한다
 * (AGENT.md §2). 화면은 정적 HTML(static/)이 fetch로 이 API를 호출해서 그린다 — 서버는 View를 만들지 않는다.
 */
@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacade bookingFacade;

    @PostMapping("/bookings")
    public ResponseEntity<BookingCreated> reserve(@RequestBody ReservationRequest request) {
        Long bookingId = bookingFacade.reserve(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new BookingCreated(bookingId));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResult result(@PathVariable Long bookingId) {
        return bookingFacade.getResult(bookingId);
    }
}
