package com.toy.cinema.payment.domain;

import com.toy.cinema.common.enums.PaymentStatus;

import java.time.LocalDateTime;

/**
 * payment 한 행의 SELECT 결과 스냅샷. ScheduleSeat과 같은 역할 — PaymentService 내부에서
 * (중복 payment_key 처리, 재조정 조회 등) 검증용으로만 쓰이고 Facade 밖으로 나가지 않는다.
 */
public record Payment(Long paymentId, Long bookingId, String paymentKey, Integer amount,
                       PaymentStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
