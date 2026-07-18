package com.toy.cinema.payment.gateway;

/**
 * PaymentGateway.pay() 전용 파라미터. 실제 PG API가 아는 값만 담는다 (amount, paymentKey).
 * bookingId는 우리 내부 FK일 뿐 PG가 알 필요도, 알 수도 없는 개념이라 여기 포함하지 않는다.
 * PaymentService가 dto/PaymentRequest를 받아서 이 타입으로 변환해 Gateway에 넘긴다.
 */
public record PgChargeRequest(Integer amount, String paymentKey) {
}
