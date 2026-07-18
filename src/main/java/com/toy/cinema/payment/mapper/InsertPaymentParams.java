package com.toy.cinema.payment.mapper;

/** PaymentMapper.insertPending 전용 파라미터. status는 항상 PENDING으로 시작하므로 값을 받지 않고 SQL에 고정한다. */
public record InsertPaymentParams(Long bookingId, String paymentKey, Integer amount) {
}
