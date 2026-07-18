package com.toy.cinema.notification.dto;

/** NotificationFacade.send()의 파라미터. BookingOrchestrator가 Saga ③단계(성공 경로)에서 호출. */
public record NotificationRequest(String userId, String message) {
}
