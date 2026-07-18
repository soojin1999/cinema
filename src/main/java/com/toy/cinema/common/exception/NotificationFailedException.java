package com.toy.cinema.common.exception;

/**
 * NotificationFacade.send() 실패 시 던진다. 지금은 로그만 찍는 구현이라 실제로 발생할 일이 거의 없지만,
 * 나중에 진짜 이메일/SMS 발송으로 교체됐을 때를 대비해 계약(예외 타입)을 미리 정의해둔다.
 * PaymentFailedException과 달리 보상 트랜잭션 트리거가 아니다 — 예매(booking)는 이미 확정된 뒤라
 * 되돌릴 이유가 없음. BookingOrchestrator가 이 타입을 별도로 catch해서 "예매는 성공, 알림만 실패"
 * 같은 응답을 만들지는 BookingOrchestrator 구현 시점에 논의 (Todo.md).
 */
public class NotificationFailedException extends CinemaException {

    public NotificationFailedException(String message) {
        super(message);
    }
}
