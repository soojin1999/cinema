package com.toy.cinema.seat.mapper;

import com.toy.cinema.common.enums.SeatStatus;

/**
 * SeatMapper.updateStatus 전용 파라미터. status 조건부 UPDATE
 * (WHERE status = expectedStatus) 에 쓰이며, holdPessimistic/confirm/release가 공용으로 사용한다.
 * 같은 타입(SeatStatus)이 두 필드라 호출부에서 순서를 실수로 바꿔도 컴파일러가 못 잡아낸다는 한계는 있음 —
 * 그래도 필드 이름이 코드에 남아 있어 @Param 여러 개보다 리뷰 시 더 잘 보인다.
 */
public record UpdateStatusParams(Long scheduleId, Long seatId, SeatStatus newStatus, SeatStatus expectedStatus) {
}
