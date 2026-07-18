package com.toy.cinema.seat.domain;

import com.toy.cinema.common.enums.SeatStatus;

import java.time.LocalDateTime;

/**
 * schedule_seat 한 행을 그대로 옮겨 담는 SELECT 결과 스냅샷.
 * SeatService 내부에서 hold() 시 "지금 상태가 뭔지" 검증하는 용도로만 쓰이고 절대 Facade 밖으로 나가지 않는다
 * (AGENT.md "domain 클래스는 패키지 밖 import 금지" 규칙의 대상).
 * 상태를 바꾸는 행위는 이 객체를 고치는 게 아니라 항상 새 UPDATE SQL로 수행하므로 record(불변)로 충분하다.
 */
public record ScheduleSeat(Long scheduleId, Long seatId, SeatStatus status, Integer version, LocalDateTime updatedAt) {
}
