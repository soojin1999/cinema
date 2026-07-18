package com.toy.cinema.seat.mapper;

import com.toy.cinema.common.enums.SeatStatus;

/**
 * SeatMapper.updateStatusWithVersion 전용 파라미터. version 조건부 UPDATE
 * (WHERE version = expectedVersion)에 쓰이며 holdOptimistic()만 사용한다.
 * 영향받은 행이 0이면 그 사이 다른 트랜잭션이 먼저 version을 올렸다는 뜻 → SeatConflictException.
 */
public record UpdateVersionParams(Long scheduleId, Long seatId, SeatStatus newStatus, Integer expectedVersion) {
}
