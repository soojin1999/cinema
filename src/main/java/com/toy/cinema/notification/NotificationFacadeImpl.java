package com.toy.cinema.notification;

import com.toy.cinema.notification.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * NotificationFacade의 현재 구현체. 로그만 남긴다 (README §범위 밖: 실제 알림 연동 없음).
 * DB도, 외부 호출도 없어 SeatService/PaymentService 같은 별도 Service 계층이 필요 없다 — Facade가 바로 처리.
 */
@Slf4j
@Component
public class NotificationFacadeImpl implements NotificationFacade {

    @Override
    public void send(NotificationRequest request) {
        log.info("[Notification] userId={}, message={}", request.userId(), request.message());
    }
}
