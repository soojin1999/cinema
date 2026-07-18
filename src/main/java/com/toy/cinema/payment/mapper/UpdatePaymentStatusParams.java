package com.toy.cinema.payment.mapper;

import com.toy.cinema.common.enums.PaymentStatus;

/** PaymentMapper.updateStatus 전용 파라미터. markSuccess/markFailed가 공용으로 사용한다. */
public record UpdatePaymentStatusParams(String paymentKey, PaymentStatus status) {
}
