package com.toy.cinema.payment.dto;

/**
 * PaymentFacade.pay()의 파라미터. BookingOrchestrator가 Saga ②단계에서 호출할 때 넘긴다.
 * paymentKey는 멱등성 키 — 호출자(booking 쪽)가 생성해서 넘겨야, 같은 논리적 요청이 재시도돼도
 * 같은 키를 유지해서 payment.payment_key UNIQUE 제약으로 중복 결제를 막을 수 있다.
 */
public record PaymentRequest(Long bookingId, Integer amount, String paymentKey) {
}
