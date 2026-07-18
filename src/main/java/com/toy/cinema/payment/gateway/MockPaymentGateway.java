package com.toy.cinema.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 실제 결제 PG 구현체 — 여기서 결제 모듈을 흉내냄 (backend.md §7).
 * 20% 확률로 실패, 3초 인위적 지연 — 이 지연이 있어야 "결제 중엔 DB 락을 물고 있으면 안 된다"는
 * Saga 설계 이유(backend.md §5)가 실제로 체감된다.
 */
@Slf4j
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final double FAILURE_RATE = 0.2;
    private static final long SIMULATED_DELAY_MS = 3000;

    @Override
    public PgChargeResult pay(PgChargeRequest request) {
        simulateNetworkDelay();

        boolean approved = ThreadLocalRandom.current().nextDouble() >= FAILURE_RATE;
        PgChargeResult result = approved
                ? new PgChargeResult(true, "결제 승인")
                : new PgChargeResult(false, "카드 결제가 거절되었습니다");

        log.info("[MockPaymentGateway] paymentKey={}, amount={} -> {}",
                request.paymentKey(), request.amount(), result);
        return result;
    }

    private void simulateNetworkDelay() {
        try {
            Thread.sleep(SIMULATED_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("결제 지연 시뮬레이션 중 인터럽트 발생", e);
        }
    }
}
