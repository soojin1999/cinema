package com.toy.cinema.screening;

import com.toy.cinema.screening.dto.ScheduleView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ScreeningFacade의 현재 구현체. Service 없이 Mapper로 바로 위임한다 — 상태 전이가 없는 단순 조회라
 * 중간에 낄 로직이 없다 (SeatFacadeImpl이 SeatService를 감싸는 것과 같은 자리, 다만 이 도메인엔 Service가 없음).
 */
@Component
@RequiredArgsConstructor
public class ScreeningFacadeImpl implements ScreeningFacade {

    private final ScreeningMapper screeningMapper;

    @Override
    public List<ScheduleView> findAllSchedules() {
        return screeningMapper.findAllSchedules();
    }

    @Override
    public ScheduleView findScheduleById(Long scheduleId) {
        return screeningMapper.findScheduleById(scheduleId);
    }
}
