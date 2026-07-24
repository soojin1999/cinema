package com.toy.cinema.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BookingTimeoutBatch(T-04) 수동 트리거 — @Scheduled를 아직 안 켜뒀으니 테스트 중엔 이걸로 직접 호출한다.
 * 나중에 BookingTimeoutBatch의 @Scheduled 주석을 풀면 이 컨트롤러는 지워도 되고, 즉시 한 번 더 돌리고
 * 싶을 때 쓰는 용도로 남겨둬도 된다.
 */
@RestController
@RequiredArgsConstructor
public class BookingTimeoutBatchController {

    private final BookingTimeoutBatch bookingTimeoutBatch;

    @PostMapping("/admin/batch/reconcile-pending-bookings")
    public void trigger() {
        bookingTimeoutBatch.reconcilePendingBookings();
    }
}
