package com.toy.cinema.seat;

import com.toy.cinema.common.enums.SeatStatus;
import com.toy.cinema.common.exception.SeatConflictException;
import com.toy.cinema.common.exception.SeatNotAvailableException;
import com.toy.cinema.seat.domain.ScheduleSeat;
import com.toy.cinema.seat.dto.SeatConfirmCommand;
import com.toy.cinema.seat.dto.SeatGridItem;
import com.toy.cinema.seat.dto.SeatHoldCommand;
import com.toy.cinema.seat.dto.SeatReleaseCommand;
import com.toy.cinema.seat.mapper.ScheduleSeatKey;
import com.toy.cinema.seat.mapper.SeatMapper;
import com.toy.cinema.seat.mapper.UpdateStatusParams;
import com.toy.cinema.seat.mapper.UpdateVersionParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * seat 도메인의 상태 전이 + 동시성 제어 담당. SeatFacade를 통해서만 호출된다 (직접 호출 금지).
 * 각 메서드는 @Transactional(REQUIRES_NEW)로 독립 커밋되며, hold()만 대기형(비관적)/충돌감지형(낙관적) 락 두 방식으로 나뉜다
 * (confirm/release는 hold로 이미 좌석을 독점한 뒤에만 호출되므로 락 경합이 없어 나누지 않음).
 */
@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatMapper seatMapper;

    // 대기형(비관적 락): FOR UPDATE로 행을 잠근 뒤 검증. 경합 시 다른 요청을 기다리게(블로킹) 했다가 항상 성공으로 끝난다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void holdPessimistic(SeatHoldCommand cmd) {
        ScheduleSeatKey key = new ScheduleSeatKey(cmd.scheduleId(), cmd.seatId());
        ScheduleSeat current = seatMapper.findForUpdate(key);

        if (current.status() != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException(
                    "이미 점유된 좌석입니다. scheduleId=%d, seatId=%d".formatted(cmd.scheduleId(), cmd.seatId()));
        }

        seatMapper.updateStatus(
                new UpdateStatusParams(cmd.scheduleId(), cmd.seatId(), SeatStatus.HELD, SeatStatus.AVAILABLE));
    }

    // 충돌감지형(낙관적 락): 잠금 없이 조회 후 version 조건부 UPDATE. 기다리게 하지 않고 경합 시 즉시 SeatConflictException으로 실패
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void holdOptimistic(SeatHoldCommand cmd) {
        ScheduleSeatKey key = new ScheduleSeatKey(cmd.scheduleId(), cmd.seatId());
        ScheduleSeat current = seatMapper.findByScheduleIdAndSeatId(key);

        if (current.status() != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException(
                    "이미 점유된 좌석입니다. scheduleId=%d, seatId=%d".formatted(cmd.scheduleId(), cmd.seatId()));
        }

        int updated = seatMapper.updateStatusWithVersion(
                new UpdateVersionParams(cmd.scheduleId(), cmd.seatId(), SeatStatus.HELD, current.version()));

        if (updated == 0) {
            throw new SeatConflictException(
                    "동시 요청과 충돌했습니다. scheduleId=%d, seatId=%d".formatted(cmd.scheduleId(), cmd.seatId()));
        }
    }

    // Saga ③ 성공 경로. HELD -> BOOKED. affected==0(이미 확정됨 등)은 지금은 그냥 무시 (오케스트레이터 단계에서 재검토 예정)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void confirm(SeatConfirmCommand cmd) {
        seatMapper.updateStatus(
                new UpdateStatusParams(cmd.scheduleId(), cmd.seatId(), SeatStatus.BOOKED, SeatStatus.HELD));
    }

    // 보상 트랜잭션. HELD -> AVAILABLE. affected==0을 무시하는 덕분에 멱등적으로 동작함 (AGENT.md 요구사항)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(SeatReleaseCommand cmd) {
        seatMapper.updateStatus(
                new UpdateStatusParams(cmd.scheduleId(), cmd.seatId(), SeatStatus.AVAILABLE, SeatStatus.HELD));
    }

    // 좌석 격자 화면 조회. Saga 단계가 아니지만 다른 메서드와 동일하게 REQUIRES_NEW로 통일 (일관성)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<SeatGridItem> getSeatGrid(Long scheduleId) {
        return seatMapper.findGridByScheduleId(scheduleId);
    }
}
