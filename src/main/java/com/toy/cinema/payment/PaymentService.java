package com.toy.cinema.payment;

import com.toy.cinema.common.enums.PaymentStatus;
import com.toy.cinema.common.exception.PaymentFailedException;
import com.toy.cinema.payment.domain.Payment;
import com.toy.cinema.payment.dto.PaymentRequest;
import com.toy.cinema.payment.mapper.InsertPaymentParams;
import com.toy.cinema.payment.mapper.PaymentMapper;
import com.toy.cinema.payment.mapper.UpdatePaymentStatusParams;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * payment 도메인의 상태 관리 담당. PaymentFacade를 통해서만 호출된다.
 * 각 메서드는 SQL 한 문장짜리 독립 트랜잭션이라 SeatService와 같은 이유로 REQUIRES_NEW를 쓴다.
 * insertPending~markSuccess/markFailed의 "순서 조립"은 여기가 아니라 PaymentFacadeImpl이 담당한다
 * (같은 클래스 안에서 이 메서드들을 서로 호출하면 Spring 프록시를 안 타서 @Transactional이 무시되기 때문 — self-invocation 문제).
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;

    /**
     * true를 리턴하면 "새로 생성됨 → PaymentFacadeImpl이 gateway를 호출해야 함".
     * false를 리턴하면 "이미 SUCCESS로 끝난 결제가 있음 → gateway를 다시 부를 필요 없음 (멱등)".
     * payment_key가 FAILED/PENDING 상태로 이미 있으면 이 자리에서 바로 예외를 던진다.
     */
    @Transactional(transactionManager = "paymentTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public boolean insertPending(PaymentRequest request) {
        try {
            paymentMapper.insertPending(
                    new InsertPaymentParams(request.bookingId(), request.paymentKey(), request.amount()));
            return true;
        } catch (DuplicateKeyException e) {
            return handleDuplicatePaymentKey(request.paymentKey());
        }
    }

    private boolean handleDuplicatePaymentKey(String paymentKey) {
        Payment existing = paymentMapper.findByPaymentKey(paymentKey);
        return switch (existing.status()) {
            case SUCCESS -> false;
            case FAILED -> throw new PaymentFailedException("이미 실패 처리된 결제입니다. paymentKey=" + paymentKey);
            case PENDING -> throw new IllegalStateException("이전 결제 시도가 아직 진행 중입니다. paymentKey=" + paymentKey);
        };
    }

    @Transactional(transactionManager = "paymentTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markSuccess(String paymentKey) {
        paymentMapper.updateStatus(new UpdatePaymentStatusParams(paymentKey, PaymentStatus.SUCCESS));
    }

    @Transactional(transactionManager = "paymentTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String paymentKey) {
        paymentMapper.updateStatus(new UpdatePaymentStatusParams(paymentKey, PaymentStatus.FAILED));
    }

    /** 지연 재조정(AGENT.md §2)에서 사용 — 이 booking의 결제가 어디까지 진행됐는지 조회. 결제 시도가 없으면 null. */
    @Transactional(transactionManager = "paymentTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public Payment findByBookingId(Long bookingId) {
        return paymentMapper.findByBookingId(bookingId);
    }
}
