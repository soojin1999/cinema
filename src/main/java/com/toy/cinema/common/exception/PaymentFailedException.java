package com.toy.cinema.common.exception;

/**
 * PaymentService.pay() 단계에서 (Mock) 결제가 거절됐을 때 던진다.
 * BookingOrchestrator.reserve()가 이 타입만 별도로 catch해서 보상 트랜잭션
 * (seatFacade.release() + bookingService.cancel())을 발동시키는 트리거 역할.
 */
public class PaymentFailedException extends CinemaException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
