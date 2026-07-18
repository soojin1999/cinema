package com.toy.cinema.payment.gateway;

/**
 * 실제 PG 연동 지점. 지금은 MockPaymentGateway 하나뿐이지만, 나중에 진짜 PG로 교체할 때
 * 이 인터페이스는 그대로 두고 새 구현체(예: TossPaymentGateway)만 추가하면 된다 (PaymentService 변경 없음).
 */
public interface PaymentGateway {

    PgChargeResult pay(PgChargeRequest request);
}
