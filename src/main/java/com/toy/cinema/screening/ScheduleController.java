package com.toy.cinema.screening;

import com.toy.cinema.screening.dto.ScheduleView;
import com.toy.cinema.seat.SeatFacade;
import com.toy.cinema.seat.dto.SeatGridItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 스케줄 탐색(목록 → 좌석 격자) JSON API. screening 도메인 밖(web 계층)이므로 다른 컨트롤러와 동일하게
 * Facade를 통해서만 접근한다 — ScreeningFacade(자기 도메인), SeatFacade(좌석 도메인 소유 데이터).
 * 화면(정적 HTML)이 fetch로 이 API를 호출해서 그린다 — 행(row) 단위 그룹핑 같은 렌더링 가공은
 * 서버가 아니라 클라이언트 JS 쪽 책임으로 옮겼다 (API는 평평한 리소스로 유지).
 */
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScreeningFacade screeningFacade;
    private final SeatFacade seatFacade;

    @GetMapping("/schedules")
    public List<ScheduleView> list() {
        return screeningFacade.findAllSchedules();
    }

    @GetMapping("/schedules/{scheduleId}")
    public ResponseEntity<ScheduleView> get(@PathVariable Long scheduleId) {
        ScheduleView schedule = screeningFacade.findScheduleById(scheduleId);
        return schedule == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(schedule);
    }

    // 초기 로딩과 polling 갱신 양쪽에 재사용되는 좌석 상태 조회
    @GetMapping("/schedules/{scheduleId}/seats")
    public List<SeatGridItem> seats(@PathVariable Long scheduleId) {
        return seatFacade.getSeatGrid(scheduleId);
    }
}
