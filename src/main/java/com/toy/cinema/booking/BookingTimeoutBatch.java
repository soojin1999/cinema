package com.toy.cinema.booking;

import com.toy.cinema.booking.domain.Booking;
import com.toy.cinema.seat.SeatFacade;
import com.toy.cinema.seat.dto.SeatReleaseCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T-04. 결제 도중 브라우저 종료 등으로 HELD/PENDING에 영구 잔류하는 booking을 회수한다.
 * booking 도메인이 스캔을 주도한다 — schedule_seat엔 booking_id가 없어(T-09, 도메인 간 FK 제거)
 * booking 쪽만 "언제 PENDING이 됐는지"를 안다. seat/payment는 예외 없이 Facade로만 접근한다(AGENT.md §1) —
 * Saga가 아닌 배치 호출자도 동일 규칙 적용.
 *
 * cutoff보다 먼저 결정을 내리면 안 되는 이유: 결제(PaymentGateway)가 정상적으로 진행 중인데도 죽은 hold로
 * 오판해서 좌석을 뺏을 수 있다 — 그래서 §2 지연 재조정과 같은 로직(tryConfirmIfPaid)을 여기서도 먼저 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingTimeoutBatch {

    private static final long TIMEOUT_MINUTES = 1;

    private final BookingService bookingService;
    private final BookingOrchestrator bookingOrchestrator;
    private final SeatFacade seatFacade;

    // @Scheduled(fixedDelay = 30_000)  // 실제 운영이면 이 주기로 자동 실행 — 지금은 /admin/batch/... 수동 트리거로 테스트 중 (Todo.md T-04)
    public void reconcilePendingBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Booking> staleBookings = bookingService.findStalePending(cutoff);

        for (Booking booking : staleBookings) {
            // 예약은 pending인데 결제가 success라면 수동 업데이트
            if (bookingOrchestrator.tryConfirmIfPaid(booking.bookingId(), booking.scheduleId(), booking.seatId())) {
                log.info("[BookingTimeoutBatch] 지연된 결제 성공 확인 → confirm 처리. bookingId={}", booking.bookingId());
                continue;
            }

            //실패했다면 보상처리
            seatFacade.release(new SeatReleaseCommand(booking.scheduleId(), booking.seatId()));
            bookingService.cancel(booking.bookingId());
            log.info("[BookingTimeoutBatch] 타임아웃 → release+cancel 처리. bookingId={}", booking.bookingId());
        }
    }
}
