-- ============================================================
-- cinema 초기 시드 데이터
-- 목적: movie·theater·schedule은 "고정값"으로 두고
--       Saga·동시성 학습에만 집중하기 위한 테스트 기반 데이터
-- ============================================================

USE cinema;

-- 영화 1편
INSERT INTO movie (movie_id, title, duration)
VALUES (1, '인터스텔라', 169);

-- 상영관 1개 (5행 × 5열 = 25석)
INSERT INTO theater (theater_id, name)
VALUES (1, 'A관');

-- 물리 좌석 25개 (A~E행, 1~5열)
INSERT INTO seat (theater_id, row_num, col_num)
VALUES (1, 'A', 1), (1, 'A', 2), (1, 'A', 3), (1, 'A', 4), (1, 'A', 5),
       (1, 'B', 1), (1, 'B', 2), (1, 'B', 3), (1, 'B', 4), (1, 'B', 5),
       (1, 'C', 1), (1, 'C', 2), (1, 'C', 3), (1, 'C', 4), (1, 'C', 5),
       (1, 'D', 1), (1, 'D', 2), (1, 'D', 3), (1, 'D', 4), (1, 'D', 5),
       (1, 'E', 1), (1, 'E', 2), (1, 'E', 3), (1, 'E', 4), (1, 'E', 5);

-- 상영 스케줄 1건
INSERT INTO schedule (schedule_id, movie_id, theater_id, start_time, end_time)
VALUES (1, 1, 1, '2026-07-20 14:00:00', '2026-07-20 16:49:00');

-- schedule_seat: 스케줄 1 × 좌석 25개 → 전부 AVAILABLE로 초기화
-- seat_id는 AUTO_INCREMENT이므로 위 INSERT 순서대로 1~25
INSERT INTO schedule_seat (schedule_id, seat_id, status)
SELECT 1, seat_id, 'AVAILABLE'
FROM seat
WHERE theater_id = 1;
