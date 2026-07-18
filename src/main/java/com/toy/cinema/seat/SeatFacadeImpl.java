package com.toy.cinema.seat;

import com.toy.cinema.seat.dto.SeatConfirmCommand;
import com.toy.cinema.seat.dto.SeatGridItem;
import com.toy.cinema.seat.dto.SeatHoldCommand;
import com.toy.cinema.seat.dto.SeatReleaseCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SeatFacade의 현재 구현체 (인프로세스 호출). 자체 로직 없이 SeatService로 그대로 위임만 한다 —
 * Facade의 역할은 "경계를 긋는 것"이지 비즈니스 로직을 갖는 게 아니므로 이 얇음(thin)이 정상이다.
 * @Service가 아니라 @Component를 쓴 이유: 이 클래스는 도메인 로직(SeatService)이 아니라
 * 경계를 위한 라우팅 계층이라는 걸 이름 이상으로도 구분하기 위함 (Spring 입장에선 기능 차이 없음).
 */
@Component
@RequiredArgsConstructor
public class SeatFacadeImpl implements SeatFacade {

    private final SeatService seatService;

    @Override
    public void holdPessimistic(SeatHoldCommand command) {
        seatService.holdPessimistic(command);
    }

    @Override
    public void holdOptimistic(SeatHoldCommand command) {
        seatService.holdOptimistic(command);
    }

    @Override
    public void confirm(SeatConfirmCommand command) {
        seatService.confirm(command);
    }

    @Override
    public void release(SeatReleaseCommand command) {
        seatService.release(command);
    }

    @Override
    public List<SeatGridItem> getSeatGrid(Long scheduleId) {
        return seatService.getSeatGrid(scheduleId);
    }
}
