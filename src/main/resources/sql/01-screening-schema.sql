-- ============================================================
-- screening_db — movie / theater / schedule (조회 전용 마스터 데이터)
-- DB: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS screening_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE screening_db;

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
-- 3. schedule — 상영 스케줄
--    movie × theater × 시간대 조합. movie/theater와 같은 스키마(screening_db)라
--    FK 유지 (T-09: 도메인 간 FK만 제거, 도메인 내부 FK는 그대로).
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
