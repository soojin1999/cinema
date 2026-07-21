-- ============================================================
-- booking_db — booking (예매 도메인, Saga 오케스트레이터 상주)
-- DB: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS booking_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE booking_db;

-- ============================================================
-- booking — 예매 기록
--    Saga 오케스트레이터가 생성·업데이트하는 메인 집계.
--    (schedule_id, seat_id)는 seat_db.schedule_seat 소속이라 도메인을 건너뛰는
--    복합 FK다 → T-09에서 제거. 정합성은 이미 Saga의 hold() 단계가 애플리케이션
--    레벨에서 보장 중이라(성공해야만 booking이 생성됨) FK 제거 영향은 적음.
--    status:
--      PENDING   = Saga 진행 중 (seat HELD 상태와 함께 존재)
--      CONFIRMED = 결제 성공 → 좌석 BOOKED
--      CANCELLED = 결제 실패 or 보상 완료 → 좌석 AVAILABLE 복원
-- ============================================================
CREATE TABLE booking
(
    booking_id  BIGINT                                  NOT NULL AUTO_INCREMENT COMMENT '예매 ID',
    schedule_id BIGINT                                  NOT NULL COMMENT '스케줄 ID (seat_db.schedule_seat 참조 — FK 아님)',
    seat_id     BIGINT                                  NOT NULL COMMENT '좌석 ID (seat_db.schedule_seat 참조 — FK 아님)',
    user_id     VARCHAR(50)                             NOT NULL COMMENT '사용자 ID (임시: 인증 없이 파라미터)',
    status      ENUM ('PENDING', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '예매 상태',
    created_at  DATETIME                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id)
) COMMENT = '예매';
