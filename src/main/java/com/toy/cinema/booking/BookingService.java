package com.toy.cinema.booking;

import com.toy.cinema.booking.domain.Booking;
import com.toy.cinema.booking.mapper.BookingMapper;
import com.toy.cinema.booking.mapper.InsertBookingParams;
import com.toy.cinema.booking.mapper.UpdateBookingStatusParams;
import com.toy.cinema.common.enums.BookingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * booking 도메인의 상태 관리 담당. BookingOrchestrator가 호출한다 (같은 도메인 내부라 mapper 타입을
 * 직접 주고받아도 경계 규칙 위반 아님 — "패키지 밖 import 금지"는 booking 도메인 밖의 다른 도메인 기준).
 * 각 메서드는 SeatService/PaymentService와 같은 이유로 REQUIRES_NEW.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingMapper bookingMapper;

    /** INSERT 직후 같은 커넥션에서 LAST_INSERT_ID()를 회수해서 리턴한다 (record 파라미터 불변성 유지). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long insertPending(InsertBookingParams params) {
        bookingMapper.insertPending(params);
        return bookingMapper.selectLastInsertId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(Long bookingId) {
        bookingMapper.updateStatus(new UpdateBookingStatusParams(bookingId, BookingStatus.CONFIRMED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(Long bookingId) {
        bookingMapper.updateStatus(new UpdateBookingStatusParams(bookingId, BookingStatus.CANCELLED));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking findById(Long bookingId) {
        return bookingMapper.findById(bookingId);
    }
}
