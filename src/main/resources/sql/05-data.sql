-- ============================================================
-- cinema 초기 시드 데이터
-- 목적: movie·theater·schedule은 "고정값"으로 두고
--       Saga·동시성 학습에만 집중하기 위한 테스트 기반 데이터
-- T-09 이후: 스키마가 도메인별로 나뉘어 USE로 스키마를 전환해가며 시딩한다.
--            booking/payment는 Saga가 런타임에 생성하므로 시드 데이터 없음.
-- ============================================================

USE screening_db;

-- 영화 1편
INSERT INTO movie (movie_id, title, duration)
VALUES (1, '인터스텔라', 169);

-- 상영관 1개 (5행 × 5열 = 25석)
INSERT INTO theater (theater_id, name)
VALUES (1, 'A관');

-- 상영 스케줄 1건
INSERT INTO schedule (schedule_id, movie_id, theater_id, start_time, end_time)
VALUES (1, 1, 1, '2026-07-20 14:00:00', '2026-07-20 16:49:00');

USE seat_db;

-- 물리 좌석 25개 (A~E행, 1~5열). theater_id=1은 screening_db.theater를 가리키지만
-- 스키마가 분리돼 FK로 강제할 수 없다 (T-09) — 값은 위에서 심은 theater_id와 맞춰서 수동으로 일치시킴.
INSERT INTO seat (theater_id, row_num, col_num)
VALUES (1, 'A', 1), (1, 'A', 2), (1, 'A', 3), (1, 'A', 4), (1, 'A', 5),
       (1, 'B', 1), (1, 'B', 2), (1, 'B', 3), (1, 'B', 4), (1, 'B', 5),
       (1, 'C', 1), (1, 'C', 2), (1, 'C', 3), (1, 'C', 4), (1, 'C', 5),
       (1, 'D', 1), (1, 'D', 2), (1, 'D', 3), (1, 'D', 4), (1, 'D', 5),
       (1, 'E', 1), (1, 'E', 2), (1, 'E', 3), (1, 'E', 4), (1, 'E', 5);

-- schedule_seat: 스케줄 1 × 좌석 25개 → 전부 AVAILABLE로 초기화
-- schedule_id=1은 screening_db.schedule을 가리키지만 위와 같은 이유로 FK 없이 값만 맞춤.
-- seat_id는 AUTO_INCREMENT이므로 위 INSERT 순서대로 1~25 (seat_db 내부 조회라 FK는 그대로 유효)
INSERT INTO schedule_seat (schedule_id, seat_id, status)
SELECT 1, seat_id, 'AVAILABLE'
FROM seat
WHERE theater_id = 1;
