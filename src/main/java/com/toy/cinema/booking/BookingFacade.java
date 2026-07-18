package com.toy.cinema.booking;

import com.toy.cinema.booking.dto.BookingResult;
import com.toy.cinema.booking.dto.ReservationRequest;

/**
 * booking 도메인 밖(예: 나중의 회원 도메인)이 예매를 만지는 유일한 창구.
 * 지금은 BookingController만 사용하지만, 다른 도메인 Facade와 대칭을 맞추기 위해 인터페이스로 둔다.
 */
public interface BookingFacade {

    Long reserve(ReservationRequest request);

    BookingResult getResult(Long bookingId);
}
