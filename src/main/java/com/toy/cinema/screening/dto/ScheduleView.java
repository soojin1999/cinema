package com.toy.cinema.screening.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * schedule ⋈ movie ⋈ theater 조인 결과. ScreeningMapper의 SELECT 결과가 가공·참조 없이 그대로
 * 클라이언트로 리턴되는 순수 조회 응답이라 record 대신 Lombok 클래스로 둔다 (AGENT.md §1 dto 규칙 참고) —
 * MyBatis가 <constructor> 매핑 없이 resultType만으로 setter 기반 자동 매핑을 할 수 있어 XML이 짧아진다.
 * 이 dto의 값을 중간에 참조/가공하는 호출부가 생기면 그때는 record로 되돌린다.
 */
@Data
public class ScheduleView {
    private Long scheduleId;
    private String movieTitle;
    private String theaterName;
    private LocalDateTime startTime;
}
