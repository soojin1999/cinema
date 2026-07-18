-- ============================================================
-- cinema 토이 프로젝트 DDL
-- DB: MySQL 8.0
-- 설계 원칙: schedule_seat(복합 PK) 중심 구조
--            status는 ENUM으로 DB 레벨 제약 확보
-- ============================================================

CREATE DATABASE IF NOT EXISTS cinema
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE cinema;

-- ============================================================
-- 1. movie — 영화 정보
--    초기엔 시드 데이터 1건으로 고정, 관리 UI는 나중에
-- ============================================================
CREATE TABLE movie
(
    movie_id   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '영화 ID',
    title      VARCHAR(200) NOT NULL COMMENT '영화 제목',
    duration   INT          NOT NULL COMMENT '상영 시간(분)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (movie_id)
) COMMENT = '영화';

-- ============================================================
-- 2. theater — 상영관 정보
--    초기엔 시드 데이터 1건으로 고정
-- ============================================================
CREATE TABLE theater
(
    theater_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '상영관 ID',
    name       VARCHAR(100) NOT NULL COMMENT '상영관 이름 (예: A관)',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (theater_id)
) COMMENT = '상영관';

-- ============================================================
-- 3. seat — 물리 좌석
--    theater에 속하는 물리적 좌석. 상태(status) 없음.
--    상태는 schedule_seat에 위임.
--    (row_num + col_num) 조합이 theater 내에서 유일해야 함 → UNIQUE KEY
-- ============================================================
CREATE TABLE seat
(
    seat_id    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '좌석 ID',
    theater_id BIGINT      NOT NULL COMMENT '상영관 ID (FK)',
    row_num    VARCHAR(5)  NOT NULL COMMENT '행 (A, B, C ...)',
    col_num    INT         NOT NULL COMMENT '열 (1, 2, 3 ...)',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (seat_id),
    UNIQUE KEY uq_seat (theater_id, row_num, col_num),  -- 같은 관에 동일 좌석 중복 방지
    CONSTRAINT fk_seat_theater FOREIGN KEY (theater_id) REFERENCES theater (theater_id)
) COMMENT = '물리 좌석';

-- ============================================================
-- 4. schedule — 상영 스케줄
--    movie × theater × 시간대 조합.
--    초기엔 시드 데이터 1건으로 고정.
-- ============================================================
CREATE TABLE schedule
(
    schedule_id BIGINT   NOT NULL AUTO_INCREMENT COMMENT '스케줄 ID',
    movie_id    BIGINT   NOT NULL COMMENT '영화 ID (FK)',
    theater_id  BIGINT   NOT NULL COMMENT '상영관 ID (FK)',
    start_time  DATETIME NOT NULL COMMENT '상영 시작 시각',
    end_time    DATETIME NOT NULL COMMENT '상영 종료 시각',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id),
    CONSTRAINT fk_schedule_movie   FOREIGN KEY (movie_id)   REFERENCES movie (movie_id),
    CONSTRAINT fk_schedule_theater FOREIGN KEY (theater_id) REFERENCES theater (theater_id)
) COMMENT = '상영 스케줄';

-- ============================================================
-- 5. schedule_seat — 상영 × 좌석 상태  ← 이 프로젝트의 핵심 테이블
--    복합 PK (schedule_id, seat_id) — 동시성 제어의 타깃
--    status ENUM: DB 레벨에서 유효하지 않은 상태값 차단
--    updated_at: 디버깅·감사(audit)용. 동시성 이슈 추적에 유용.
--
--    [상태 머신]
--    AVAILABLE ──hold──▶ HELD ──confirm──▶ BOOKED
--                         └──release(보상)──▶ AVAILABLE
-- ============================================================
CREATE TABLE schedule_seat
(
    schedule_id BIGINT                              NOT NULL COMMENT '스케줄 ID (복합 PK)',
    seat_id     BIGINT                              NOT NULL COMMENT '좌석 ID (복합 PK)',
    status      ENUM ('AVAILABLE', 'HELD', 'BOOKED') NOT NULL DEFAULT 'AVAILABLE' COMMENT '좌석 상태',
    version     INT                                 NOT NULL DEFAULT 0 COMMENT '충돌감지형(낙관적) 락 버전 - 값이 다르면 다른 요청이 먼저 바꿨다는 뜻 (UPDATE 시마다 +1)',
    updated_at  DATETIME                            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (schedule_id, seat_id),             -- 복합 PK: 락의 최소 단위
    CONSTRAINT fk_ss_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (schedule_id),
    CONSTRAINT fk_ss_seat     FOREIGN KEY (seat_id)     REFERENCES seat (seat_id)
) COMMENT = '상영별 좌석 상태 (Saga 동시성 제어 핵심)';

-- ============================================================
-- 6. booking — 예매 기록
--    Saga 오케스트레이터가 생성·업데이트하는 메인 집계.
--    (schedule_id, seat_id) → schedule_seat 복합 FK.
--    status:
--      PENDING   = Saga 진행 중 (seat HELD 상태와 함께 존재)
--      CONFIRMED = 결제 성공 → 좌석 BOOKED
--      CANCELLED = 결제 실패 or 보상 완료 → 좌석 AVAILABLE 복원
-- ============================================================
CREATE TABLE booking
(
    booking_id  BIGINT                                  NOT NULL AUTO_INCREMENT COMMENT '예매 ID',
    schedule_id BIGINT                                  NOT NULL COMMENT '스케줄 ID',
    seat_id     BIGINT                                  NOT NULL COMMENT '좌석 ID',
    user_id     VARCHAR(50)                             NOT NULL COMMENT '사용자 ID (임시: 인증 없이 파라미터)',
    status      ENUM ('PENDING', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '예매 상태',
    created_at  DATETIME                                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME                                NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (booking_id),
    -- schedule_seat 복합 FK: booking은 반드시 schedule_seat 행이 있어야만 생성 가능
    CONSTRAINT fk_booking_ss FOREIGN KEY (schedule_id, seat_id)
        REFERENCES schedule_seat (schedule_id, seat_id)
) COMMENT = '예매';

-- ============================================================
-- 7. payment — 결제 기록
--    payment_key: 멱등성 키. 같은 요청이 두 번 와도 한 번만 처리.
--    (MockPaymentGateway가 이 키로 중복 처리를 막음)
--    status:
--      PENDING = Mock 게이트웨이 호출 직전
--      SUCCESS = 결제 성공
--      FAILED  = 결제 실패 (보상 트랜잭션 발동 트리거)
-- ============================================================
CREATE TABLE payment
(
    payment_id  BIGINT                            NOT NULL AUTO_INCREMENT COMMENT '결제 ID',
    booking_id  BIGINT                            NOT NULL COMMENT '예매 ID (FK)',
    payment_key VARCHAR(100)                      NOT NULL COMMENT '멱등성 키 (중복 결제 방지)',
    amount      INT                               NOT NULL COMMENT '결제 금액(원)',
    status      ENUM ('PENDING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '결제 상태',
    created_at  DATETIME                          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME                          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uq_payment_key (payment_key),      -- 멱등성: 동일 payment_key 중복 INSERT 차단
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES booking (booking_id)
) COMMENT = '결제 (Mock 게이트웨이 대상)';
