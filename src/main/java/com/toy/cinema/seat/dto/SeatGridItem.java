package com.toy.cinema.seat.dto;

import com.toy.cinema.common.enums.SeatStatus;
import lombok.Data;

/**
 * SeatFacade.getSeatGrid()의 리턴 원소. SeatMapper의 SELECT 결과(seat+schedule_seat 조인)가
 * 가공·참조 없이 그대로 클라이언트로 리턴되는 순수 조회 응답이라 record 대신 Lombok 클래스로 둔다
 * (AGENT.md §1 dto 규칙 참고) — MyBatis가 <constructor> 매핑 없이 resultType만으로 자동 매핑할 수 있다.
 * 이 dto의 값을 중간에 참조/가공하는 호출부가 생기면 그때는 record로 되돌린다.
 */
@Data
public class SeatGridItem {
    private Long seatId;
    private String rowNum;
    private Integer colNum;
    private SeatStatus status;
}
