package com.toy.cinema.seat;

import com.toy.cinema.common.exception.SeatConflictException;
import com.toy.cinema.common.exception.SeatNotAvailableException;
import com.toy.cinema.seat.dto.SeatHoldCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-10 4단계 — 진짜 DB(seat_db) 위에서 스레드 2개가 같은 좌석을 동시에 hold할 때
 * 대기형(비관적)/충돌감지형(낙관적) 락이 실제로 어떻게 다르게 실패하는지 관찰한다.
 * SeatMapper를 mock하지 않고 실제 SqlSessionFactory(SeatDataSourceConfig)를 그대로 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SeatServiceConcurrencyTest {

    @Autowired
    SeatService seatService;

    @Autowired
    @Qualifier("seatDataSource")
    DataSource seatDataSource;

    final Long scheduleId = 1L;
    final Long seatId = 1L;

    @BeforeEach
    void resetSeatToAvailable() throws Exception {
        try (Connection conn = seatDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE schedule_seat SET status = 'AVAILABLE', version = 0 WHERE schedule_id = ? AND seat_id = ?")) {
            ps.setLong(1, scheduleId);
            ps.setLong(2, seatId);
            ps.executeUpdate();
        }
    }

    @Test
    void 대기형_락에_동시_2명이_요청하면_1명만_성공한다() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        // startSignal(count=1): 작업 스레드들이 await()로 여기서 대기하다가, 메인이 countDown() 한 번 부르면
        //   전부 동시에 풀려남 — "출발 신호". 스레드 수와 무관하게 항상 1.
        // doneSignal(count=threadCount): 반대로 메인이 await()로 대기하고, 작업 스레드 각자가 끝날 때마다
        //   countDown() 한 번씩 불러줌 — "완료 신호". 전원이 다 불러야 0이 되므로 스레드 수만큼.
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    seatService.holdPessimistic(new SeatHoldCommand(scheduleId, seatId));
                    successCount.incrementAndGet();
                } catch (SeatNotAvailableException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        doneSignal.await();
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, conflictCount.get());
    }

    @Test
    void 충돌감지형_락에_동시_2명이_요청하면_1명만_성공한다() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        AtomicInteger successCnt = new AtomicInteger();
        AtomicInteger failCnt = new AtomicInteger();

        for(int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await();
                    seatService.holdOptimistic(new SeatHoldCommand(scheduleId, seatId));
                    successCnt.incrementAndGet();
                } catch(SeatConflictException e) {
                    failCnt.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown();
        doneSignal.await();
        executor.shutdown();

        assertEquals(1, successCnt.get());
        assertEquals(1, failCnt.get());
    }
}
