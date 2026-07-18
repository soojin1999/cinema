package com.toy.cinema.payment.gateway;

/**
 * PaymentGateway.pay()의 응답. 카드 거절 같은 결제 실패는 PG 입장에서 일상적인 정상 응답이라
 * 예외가 아니라 이 값으로 전달한다. 성공/실패 판단(=PaymentFailedException을 던질지)은
 * 이 결과를 받은 PaymentService가 담당한다 — Gateway는 사실만 리턴, 판단은 상위 레이어에서.
 */
public record PgChargeResult(boolean success, String message) {
}
