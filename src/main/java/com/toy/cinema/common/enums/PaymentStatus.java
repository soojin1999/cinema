package com.toy.cinema.common.enums;

/**
 * payment.status ENUM('PENDING','SUCCESS','FAILED')과 1:1 매핑.
 * MockPaymentGateway 호출 직전 PENDING → 결과에 따라 SUCCESS 또는 FAILED.
 * FAILED는 BookingOrchestrator의 보상 트랜잭션(release+cancel) 발동 트리거.
 */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
