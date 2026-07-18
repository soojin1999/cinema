package com.toy.cinema.screening;

import com.toy.cinema.screening.dto.ScheduleView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * screening 도메인의 유일한 DB 접근 지점. ScreeningFacadeImpl만 사용하며, 절대 screening 패키지 밖에서
 * import 금지 (다른 도메인의 Mapper와 동일한 규칙). movie/theater/schedule 조인은 셋 다 이 도메인 소유
 * 테이블이라 자유롭다 — 금지되는 건 "조인"이 아니라 "다른 도메인이 소유한 테이블을 직접 조인하는 것"이다.
 * 구현은 src/main/resources/mapper/screening/ScreeningMapper.xml.
 */
@Mapper
public interface ScreeningMapper {

    List<ScheduleView> findAllSchedules();

    ScheduleView findScheduleById(@Param("scheduleId") Long scheduleId);
}
