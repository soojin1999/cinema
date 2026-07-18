package com.toy.cinema.payment;

import com.toy.cinema.common.enums.PaymentStatus;
import com.toy.cinema.payment.dto.PaymentRequest;

/**
 * payment 도메인 밖(주로 booking)이 결제를 만지는 유일한 창구.
 * SeatFacade와 같은 이유로 인터페이스 — 지금은 인프로세스, 나중에 물리 분리 대비.
 */
public interface PaymentFacade {

    void pay(PaymentRequest request);

    /** 지연 재조정용 조회. 해당 booking에 대한 결제 시도가 없으면 null. */
    PaymentStatus findStatusByBookingId(Long bookingId);
}
