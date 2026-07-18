package com.toy.cinema.seat.mapper;

/**
 * SeatMapper.findForUpdate / findByScheduleIdAndSeatId 공용 파라미터.
 * seat/dto의 Command들과 모양(scheduleId, seatId)은 같지만 별개 타입이다 —
 * 이건 "행을 식별하는 키"라는 SeatService~SeatMapper 사이의 내부 배관이지,
 * booking 등 외부 도메인이 알아야 할 개념(Facade dto)이 아니다.
 */
public record ScheduleSeatKey(Long scheduleId, Long seatId) {
}
