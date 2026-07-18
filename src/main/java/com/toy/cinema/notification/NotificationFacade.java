package com.toy.cinema.notification;

import com.toy.cinema.notification.dto.NotificationRequest;

/**
 * notification 도메인 밖(주로 booking)이 알림을 보내는 유일한 창구.
 * 다른 Facade와 같은 이유로 인터페이스 — 지금은 로그 출력, 나중에 진짜 이메일/SMS 발송으로 교체 가능.
 */
public interface NotificationFacade {

    void send(NotificationRequest request);
}
