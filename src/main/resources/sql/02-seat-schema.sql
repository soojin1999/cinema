-- ============================================================
-- seat_db — seat / schedule_seat (좌석 도메인, Saga 동시성 제어 핵심)
-- DB: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS seat_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE seat_db;

-- ============================================================
-- 1. seat — 물리 좌석
--    theater에 속하는 물리적 좌석. 상태(status) 없음, 상태는 schedule_seat에 위임.
--    theater_id는 screening_db 소속이라 도메인을 건너뛰는 FK다 → T-09에서 제거.
--    유효성은 이제 DB가 아닌 애플리케이션 레벨에서 보장한다 (지금은 시드 데이터로만
--    채워져서 당장 위험은 없음 — T-08 관리자 CRUD 착수 시 검증 로직 추가 예정, Todo.md T-09 참고).
-- ============================================================
CREATE TABLE seat
(
    seat_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '좌석 ID',
    theater_id BIGINT      NOT NULL COMMENT '상영관 ID (screening_db.theater 참조, FK 아님 — 도메인 간 참조는 애플리케이션 레벨 책임)',
    row_num    VARCHAR(5)  NOT NULL COMMENT '행 (A, B, C ...)',
    col_num    INT         NOT NULL COMMENT '열 (1, 2, 3 ...)',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (seat_id),
    UNIQUE KEY uq_seat (theater_id, row_num, col_num)  -- 같은 관에 동일 좌석 중복 방지
) COMMENT = '물리 좌석';

-- ============================================================
-- 2. schedule_seat — 상영별 좌석 상태 ⭐ 핵심 테이블
--    복합 PK (schedule_id, seat_id) — 동시성 제어의 타깃
--    schedule_id는 screening_db 소속이라 도메인을 건너뛰는 FK다 → T-09에서 제거.
--    seat_id는 같은 스키마(seat_db) 소속이라 FK 유지.
--
--    [상태 머신]
--    AVAILABLE ──hold──▶ HELD ──confirm──▶ BOOKED
--                         └──release(보상)──▶ AVAILABLE
-- ============================================================
CREATE TABLE schedule_seat
(
    schedule_id BIGINT                              NOT NULL COMMENT '스케줄 ID (복합 PK, screening_db.schedule 참조 — FK 아님)',
    seat_id     BIGINT                              NOT NULL COMMENT '좌석 ID (복합 PK)',
    status      ENUM ('AVAILABLE', 'HELD', 'BOOKED') NOT NULL DEFAULT 'AVAILABLE' COMMENT '좌석 상태',
    version     INT                                 NOT NULL DEFAULT 0 COMMENT '충돌감지형(낙관적) 락 버전 - 값이 다르면 다른 요청이 먼저 바꿨다는 뜻 (UPDATE 시마다 +1)',
    updated_at  DATETIME                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id, seat_id),             -- 복합 PK: 락의 최소 단위
    CONSTRAINT fk_ss_seat FOREIGN KEY (seat_id) REFERENCES seat (seat_id)
) COMMENT = '상영별 좌석 상태 (Saga 동시성 제어 핵심)';
