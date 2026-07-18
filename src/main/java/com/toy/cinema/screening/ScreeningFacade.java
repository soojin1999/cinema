package com.toy.cinema.screening;

import com.toy.cinema.screening.dto.ScheduleView;

import java.util.List;

/**
 * screening 도메인 밖(web 계층)이 스케줄 정보를 조회하는 유일한 창구. 다른 도메인의 Facade와 동일한 자리 —
 * 상태 전이 로직이 없어 Service는 두지 않지만(빈 통과 계층은 과설계), 경계 일관성을 위해 Facade는 둔다.
 * 나중에 screening을 물리 분리해도 구현체만 HTTP 클라이언트로 교체하면 되는 지점이라는 의미도 동일하게 가진다.
 */
public interface ScreeningFacade {

    List<ScheduleView> findAllSchedules();

    ScheduleView findScheduleById(Long scheduleId);
}
