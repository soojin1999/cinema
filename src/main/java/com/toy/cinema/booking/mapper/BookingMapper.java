package com.toy.cinema.booking.mapper;

import com.toy.cinema.booking.domain.Booking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * booking 도메인의 유일한 DB 접근 지점. BookingService만 사용.
 *   insertPending      → PENDING 행 생성 (status는 SQL에 고정)
 *   selectLastInsertId → 방금 이 커넥션에서 INSERT한 booking_id 회수 (record 불변성 유지를 위해 useGeneratedKeys 대신 사용)
 *   updateStatus       → CONFIRMED/CANCELLED로 갱신 (confirm/cancel 공용)
 *   findById           → 지연 재조정(GET /bookings/{id})에서 현재 상태 확인
 *   findStalePending   → T-04 배치용. cutoff 이전에 생성된 채 아직 PENDING인 booking 조회
 */
@Mapper
public interface BookingMapper {

    void insertPending(InsertBookingParams params);

    Long selectLastInsertId();

    int updateStatus(UpdateBookingStatusParams params);

    Booking findById(@Param("bookingId") Long bookingId);

    List<Booking> findStalePending(@Param("cutoff") LocalDateTime cutoff);
}
