package com.toy.cinema.common.enums;

/**
 * schedule_seat.status ENUM('AVAILABLE','HELD','BOOKED')과 1:1 매핑.
 * MyBatis가 상수명을 그대로 문자열로 변환해 DB에 넣고 읽으므로, 이름이 DB ENUM 값과 정확히 같아야 한다.
 * seat 도메인의 상태 머신: AVAILABLE -hold-> HELD -confirm-> BOOKED, HELD -release(보상)-> AVAILABLE.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}
