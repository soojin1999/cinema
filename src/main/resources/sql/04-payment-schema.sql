-- ============================================================
-- payment_db — payment (결제 도메인, Mock 게이트웨이 대상)
-- DB: MySQL 8.0
-- ============================================================

CREATE DATABASE IF NOT EXISTS payment_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE payment_db;

-- ============================================================
-- payment — 결제 기록
--    booking_id는 booking_db 소속이라 도메인을 건너뛰는 FK다 → T-09에서 제거.
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
    booking_id  BIGINT                            NOT NULL COMMENT '예매 ID (booking_db.booking 참조 — FK 아님)',
    payment_key VARCHAR(100)                      NOT NULL COMMENT '멱등성 키 (중복 결제 방지)',
    amount      INT                               NOT NULL COMMENT '결제 금액(원)',
    status      ENUM ('PENDING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '결제 상태',
    created_at  DATETIME                          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME                          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    UNIQUE KEY uq_payment_key (payment_key)  -- 멱등성: 동일 payment_key 중복 INSERT 차단
) COMMENT = '결제 (Mock 게이트웨이 대상)';
