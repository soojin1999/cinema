package com.toy.cinema.seat;

import com.toy.cinema.common.enums.SeatStatus;
import com.toy.cinema.common.exception.SeatConflictException;
import com.toy.cinema.common.exception.SeatNotAvailableException;
import com.toy.cinema.seat.domain.ScheduleSeat;
import com.toy.cinema.seat.dto.SeatHoldCommand;
import com.toy.cinema.seat.mapper.ScheduleSeatKey;
import com.toy.cinema.seat.mapper.SeatMapper;
import com.toy.cinema.seat.mapper.UpdateStatusParams;
import com.toy.cinema.seat.mapper.UpdateVersionParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatServiceTest {

    SeatMapper mockMapper;
    SeatService seatService;

    @BeforeEach
    void setUp() {
        mockMapper = mock(SeatMapper.class);
        seatService = new SeatService(mockMapper);
    }

    @Test
    void 이미_HELD인_좌석에_holdPessimistic을_호출하면_예외가_터진다() {
        Long scheduleId = 1L;
        Long seatId = 1L;
        ScheduleSeat heldSeat = new ScheduleSeat(scheduleId, seatId, SeatStatus.HELD, 0, null);

        when(mockMapper.findForUpdate(new ScheduleSeatKey(scheduleId, seatId)))
                .thenReturn(heldSeat);

        assertThrows(SeatNotAvailableException.class,
                () -> seatService.holdPessimistic(new SeatHoldCommand(scheduleId, seatId)));
    }

    @Test
    void AVAILABLE_좌석에_holdPessimistic을_호출하면_updateStatus가_호출된다() {
        Long scheduleId = 1L;
        Long seatId = 1L;
        //AVAILABLE 상태 객체 생성하여 준비
        ScheduleSeat availableSeat = new ScheduleSeat(scheduleId, seatId, SeatStatus.AVAILABLE, 0, null);
        //실제 객체에 접근했을때 findForUpdate 를 실행할 경우 availableSeat를 RETURN
        when(mockMapper.findForUpdate(new ScheduleSeatKey(scheduleId, seatId)))
                .thenReturn(availableSeat);
        //테스트하려는 메서드 실행
        seatService.holdPessimistic(new SeatHoldCommand(scheduleId, seatId));
        //holdPessimistic 안에 updateStatus가 실제로 실행 됐는지 확인 + 파라미터 객체로 해당 메서드가 실행됐는지
        verify(mockMapper).updateStatus(
                new UpdateStatusParams(scheduleId, seatId, SeatStatus.HELD, SeatStatus.AVAILABLE));
    }

    @Test
    void holdOptimistic_버전충돌시_SeatConflictException이_터진다() {
        Long scheduleId = 1L;
        Long seatId = 1L;
        ScheduleSeat current = new ScheduleSeat(scheduleId, seatId,SeatStatus.AVAILABLE,0,null);

        when(mockMapper.findByScheduleIdAndSeatId(new ScheduleSeatKey(scheduleId, seatId)))
                .thenReturn(current);

        when(mockMapper.updateStatusWithVersion(
                new UpdateVersionParams(scheduleId, seatId, SeatStatus.HELD, 0)))
                .thenReturn(0);

        assertThrows(SeatConflictException.class,
                () -> seatService.holdOptimistic(new SeatHoldCommand(scheduleId, seatId)));
    }

    @Test
    void holdOptimistic_충돌없으면_updateStatusWithVersion이_호출된다() {
        Long scheduleId = 1L;
        Long seatId = 1L;
        ScheduleSeat current = new ScheduleSeat(scheduleId, seatId, SeatStatus.AVAILABLE, 0, null);

        when(mockMapper.findByScheduleIdAndSeatId(new ScheduleSeatKey(scheduleId, seatId)))
                .thenReturn(current);

        when(mockMapper.updateStatusWithVersion(
                new UpdateVersionParams(scheduleId, seatId, SeatStatus.HELD, 0)))
                .thenReturn(1);

        seatService.holdOptimistic(new SeatHoldCommand(scheduleId, seatId));

        verify(mockMapper).updateStatusWithVersion(
                new UpdateVersionParams(scheduleId, seatId, SeatStatus.HELD, 0));
    }
}
