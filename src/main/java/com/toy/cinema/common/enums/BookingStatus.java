package com.toy.cinema.common.enums;

/**
 * booking.status ENUM('PENDING','CONFIRMED','CANCELLED')과 1:1 매핑.
 * Saga 진행 상태를 나타낸다: hold() 직후 PENDING 생성 → confirm() 성공 시 CONFIRMED,
 * release() 보상 발동 시 CANCELLED.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED
}
