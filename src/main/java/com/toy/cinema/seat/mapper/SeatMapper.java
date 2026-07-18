package com.toy.cinema.seat.mapper;

import com.toy.cinema.seat.domain.ScheduleSeat;
import com.toy.cinema.seat.dto.SeatGridItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * seat 도메인의 유일한 DB 접근 지점. SeatService만 사용하며, 절대 seat 패키지 밖에서 import 금지.
 * 구현은 src/main/resources/mapper/seat/SeatMapper.xml.
 * find 2개 + update 2개로 대기형(비관적)/충돌감지형(낙관적) 락 양쪽 경로를 지원한다:
 *   findForUpdate            → 대기형(비관적) 락 (SELECT ... FOR UPDATE, 다른 요청을 기다리게 함)
 *   findByScheduleIdAndSeatId → 충돌감지형(낙관적) 락 (잠금 없는 SELECT)
 *   updateStatus              → status 조건부 UPDATE. hold(대기형)/confirm/release 공용
 *   updateStatusWithVersion   → version 조건부 UPDATE. holdOptimistic(충돌감지형) 전용
 *   findGridByScheduleId      → 좌석 격자 화면 조회 전용. seat + schedule_seat 조인, 잠금 없음
 */
@Mapper
public interface SeatMapper {

    ScheduleSeat findForUpdate(ScheduleSeatKey key);

    ScheduleSeat findByScheduleIdAndSeatId(ScheduleSeatKey key);

    int updateStatus(UpdateStatusParams params);

    int updateStatusWithVersion(UpdateVersionParams params);

    List<SeatGridItem> findGridByScheduleId(@Param("scheduleId") Long scheduleId);
}
