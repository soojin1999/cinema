package com.toy.cinema.seat;

import com.toy.cinema.seat.dto.SeatConfirmCommand;
import com.toy.cinema.seat.dto.SeatGridItem;
import com.toy.cinema.seat.dto.SeatHoldCommand;
import com.toy.cinema.seat.dto.SeatReleaseCommand;

import java.util.List;

/**
 * seat 도메인 밖(주로 booking, 그리고 좌석 화면을 그리는 web 계층)이 좌석을 만지는 유일한 창구.
 * 인터페이스로 둔 이유: 나중에 seat을 별도 서비스로 물리 분리할 때, 이 인터페이스는 그대로 두고
 * 구현체만 인프로세스(SeatFacadeImpl) → HTTP 클라이언트로 교체하면 되기 때문 (호출부는 코드 변경 없음).
 * holdPessimistic/holdOptimistic 둘 다 노출하는 이유: 어느 락 전략을 쓸지는 호출자(BookingOrchestrator)가 고른다.
 * getSeatGrid는 Saga 단계는 아니지만, schedule_seat이 seat 도메인 소유 테이블이라 조회도 예외 없이 이 Facade를 거친다.
 */
public interface SeatFacade {

    void holdPessimistic(SeatHoldCommand command);

    void holdOptimistic(SeatHoldCommand command);

    void confirm(SeatConfirmCommand command);

    void release(SeatReleaseCommand command);

    List<SeatGridItem> getSeatGrid(Long scheduleId);
}
