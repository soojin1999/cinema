package com.toy.cinema.booking;

import com.toy.cinema.booking.domain.Booking;
import com.toy.cinema.booking.dto.BookingResult;
import com.toy.cinema.booking.dto.ReservationRequest;
import com.toy.cinema.booking.mapper.InsertBookingParams;
import com.toy.cinema.common.enums.BookingStatus;
import com.toy.cinema.common.enums.PaymentStatus;
import com.toy.cinema.common.exception.PaymentFailedException;
import com.toy.cinema.notification.NotificationFacade;
import com.toy.cinema.notification.dto.NotificationRequest;
import com.toy.cinema.payment.PaymentFacade;
import com.toy.cinema.payment.dto.PaymentRequest;
import com.toy.cinema.seat.SeatFacade;
import com.toy.cinema.seat.dto.SeatConfirmCommand;
import com.toy.cinema.seat.dto.SeatHoldCommand;
import com.toy.cinema.seat.dto.SeatReleaseCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Saga 흐름을 조율하는 핵심 클래스. Facade가 아니며 booking 도메인 내부(BookingFacadeImpl)에서만 호출된다.
 * @Transactional을 절대 이 클래스/메서드에 걸지 않는다 (AGENT.md §1) — 각 단계(seat/payment/booking의
 * REQUIRES_NEW 메서드)가 알아서 독립 커밋되고, reserve() 자체는 그 커밋들을 순서대로 호출만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingOrchestrator {

    private final SeatFacade seatFacade;
    private final PaymentFacade paymentFacade;
    private final NotificationFacade notificationFacade;
    private final BookingService bookingService;

    public Long reserve(ReservationRequest request) {
        Long scheduleId = request.scheduleId();
        Long seatId = request.seatId();

        // ① hold — lockType에 따라 SeatFacade의 두 메서드 중 하나를 고른다 (분기는 여기서만 함)
        SeatHoldCommand holdCommand = new SeatHoldCommand(scheduleId, seatId);
        if (request.lockType() == LockType.PESSIMISTIC) {
            seatFacade.holdPessimistic(holdCommand);
        } else {
            seatFacade.holdOptimistic(holdCommand);
        }

        // hold 성공 후에만 booking을 PENDING으로 생성 — bookingId가 있어야 결제를 시도할 수 있음
        Long bookingId = bookingService.insertPending(
                new InsertBookingParams(scheduleId, seatId, request.userId()));

        // ② pay — paymentKey(멱등성 키)는 호출자인 여기서 생성해서 넘긴다
        String paymentKey = UUID.randomUUID().toString();
        try {
            paymentFacade.pay(new PaymentRequest(bookingId, request.amount(), paymentKey));
        } catch (PaymentFailedException e) {
            // 예측된 비즈니스 실패 → 보상
            seatFacade.release(new SeatReleaseCommand(scheduleId, seatId));
            bookingService.cancel(bookingId);
            return bookingId;
        } catch (Exception e) {
            // 예측 외 기술 오류 → 원칙적으로 보상 없이 위로 던짐, 좌석 HELD 잔류 (Todo.md T-04, 배치가 회수)
            // 단, 결제가 실제로는 성공했는데 confirm 단계만 실패한 경우는 그 자리에서 확정 — 확실한 경우만 즉시 처리
            try {
                if(tryConfirmIfPaid(bookingId, scheduleId, seatId)) return bookingId;
            } catch(Exception recoveryEx) {
                log.warn("결제 상태 확인/재조정 실패. bookingId={}", bookingId, recoveryEx);
            }
            throw e;
        }

        // ③ 성공
        seatFacade.confirm(new SeatConfirmCommand(scheduleId, seatId));
        bookingService.confirm(bookingId);
        try {
            notificationFacade.send(new NotificationRequest(request.userId(), "예매가 완료되었습니다."));
        } catch (Exception e) {
            // booking은 이미 CONFIRMED로 커밋된 뒤라 되돌릴 게 없음 — 알림 실패가 예매 성공을 덮지 않도록 로그만 남김 (Todo.md T-07)
            log.warn("알림 전송 실패. bookingId={}", bookingId, e);
        }

        return bookingId;
    }

    /**
     * 지연 재조정 (AGENT.md §2). payment는 SUCCESS로 끝났는데 confirm 단계가 기술적 오류로 실패해서
     * booking이 PENDING에 남아있는 경우, 조회 시점에 그 자리에서 confirm을 재시도한다.
     */
    public BookingResult getResult(Long bookingId) {
        Booking booking = bookingService.findById(bookingId);

        if(booking.status() == BookingStatus.PENDING && tryConfirmIfPaid(bookingId, booking.scheduleId(), booking.seatId()))
            return new BookingResult(bookingId, BookingStatus.CONFIRMED);
        return new BookingResult(bookingId, booking.status());
    }

    /**
     * 결제를 했다면 booking (예약 상태), schedule_seat(좌석 상태) 업데이트.
     * package-private — 같은 booking 패키지의 BookingTimeoutBatch(T-04)도 재사용한다.
     * @param bookingId
     * @param scheduleId
     * @param seatId
     * @return
     */
    boolean tryConfirmIfPaid(Long bookingId, Long scheduleId, Long seatId) {
        if(paymentFacade.findStatusByBookingId(bookingId) == PaymentStatus.SUCCESS) {
            seatFacade.confirm(new SeatConfirmCommand(scheduleId, seatId));
            bookingService.confirm(bookingId);
            return true;
        }
        return false;
    }
}
