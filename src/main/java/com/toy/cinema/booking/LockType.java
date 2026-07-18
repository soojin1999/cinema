package com.toy.cinema.booking;

/**
 * hold() 단계에서 어느 락 방식을 쓸지 외부(요청 파라미터)에서 선택하기 위한 타입.
 *   PESSIMISTIC (대기형/비관적) — 잠그고 다른 요청을 기다리게 함
 *   OPTIMISTIC  (충돌감지형/낙관적) — 잠그지 않고 진행하다가 충돌 시 즉시 실패
 * BookingController가 요청 파라미터(예: ?lockType=PESSIMISTIC)를 파싱해 BookingOrchestrator.reserve()에 전달한다.
 * seat 도메인 자체(SeatFacade/SeatHoldCommand)는 이 타입을 모른다 — 어느 락을 쓸지는 호출자(BookingOrchestrator)가
 * seatFacade.holdPessimistic()/holdOptimistic() 중 무엇을 부를지로 결정한다.
 */
public enum LockType {
    /** 대기형 — 잠그고 다른 요청을 기다리게 함 */
    PESSIMISTIC,
    /** 충돌감지형 — 잠그지 않고 진행하다가 충돌 시 즉시 실패 */
    OPTIMISTIC
}
