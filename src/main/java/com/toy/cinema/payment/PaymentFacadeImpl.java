package com.toy.cinema.payment;

import com.toy.cinema.common.enums.PaymentStatus;
import com.toy.cinema.common.exception.PaymentFailedException;
import com.toy.cinema.payment.domain.Payment;
import com.toy.cinema.payment.dto.PaymentRequest;
import com.toy.cinema.payment.gateway.PaymentGateway;
import com.toy.cinema.payment.gateway.PgChargeRequest;
import com.toy.cinema.payment.gateway.PgChargeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PaymentFacade의 현재 구현체. insertPending → gateway 호출 → markSuccess/markFailed 순서를
 * 조립하는 "작은 Saga" 오케스트레이터 — BookingOrchestrator가 최상위에서 하는 일을
 * payment 도메인 안에서 한 번 더 축소판으로 수행한다. PaymentService와 다른 빈이라
 * self-invocation 문제 없이 각 단계의 @Transactional(REQUIRES_NEW)이 정상 작동한다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFacadeImpl implements PaymentFacade {

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    @Override
    public void pay(PaymentRequest request) {
        boolean shouldCallGateway = paymentService.insertPending(request);
        if (!shouldCallGateway) {
            return; // 이미 SUCCESS로 끝난 결제와 같은 요청 (멱등)
        }

        //결제 준비 완료 => 실제 결제
        PgChargeResult result = paymentGateway.pay(new PgChargeRequest(request.amount(), request.paymentKey()));

        //결제 상태를 확인하고 insert한 결제정보가 정상적으로 끝났는지, 실패했는지 마킹
        if (result.success()) {
            paymentService.markSuccess(request.paymentKey());
        } else {
            paymentService.markFailed(request.paymentKey());
            throw new PaymentFailedException("결제가 거절되었습니다: " + result.message());
        }
    }

    @Override
    public PaymentStatus findStatusByBookingId(Long bookingId) {
        Payment payment = paymentService.findByBookingId(bookingId);
        return payment != null ? payment.status() : null;
    }
}
