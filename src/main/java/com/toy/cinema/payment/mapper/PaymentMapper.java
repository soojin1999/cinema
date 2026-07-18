package com.toy.cinema.payment.mapper;

import com.toy.cinema.payment.domain.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * payment 도메인의 유일한 DB 접근 지점. PaymentService만 사용.
 *   insertPending      → 결제 시도 기록 생성 (payment_key UNIQUE라 중복 시 DuplicateKeyException)
 *   updateStatus       → SUCCESS/FAILED로 갱신 (markSuccess/markFailed 공용)
 *   findByPaymentKey   → 중복 INSERT 감지 시, 기존 결제가 어떤 상태였는지 확인
 *   findByBookingId    → 나중에 "지연 재조정"(AGENT.md) 구현 시, booking 기준으로 결제 상태 조회
 */
@Mapper
public interface PaymentMapper {

    void insertPending(InsertPaymentParams params);

    int updateStatus(UpdatePaymentStatusParams params);

    Payment findByPaymentKey(@Param("paymentKey") String paymentKey);

    Payment findByBookingId(@Param("bookingId") Long bookingId);
}
