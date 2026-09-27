package com.chris64233.reworkroute.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.chris64233.reworkroute.domain.DispositionStatus;
import com.chris64233.reworkroute.domain.InspectionVerdict;
import com.chris64233.reworkroute.domain.NcStatus;
import com.chris64233.reworkroute.domain.NonConformingRecord;
import com.chris64233.reworkroute.domain.ProductionBatch;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.support.TestFixtures;
import com.chris64233.reworkroute.web.dto.CompleteOperationRequest;
import com.chris64233.reworkroute.web.dto.CreateDispositionRequest;
import com.chris64233.reworkroute.web.dto.SubmitInspectionRequest;

/**
 * 并发场景：两个处置不能重复消费同一原始数量；并发完成工序遵守顺序；
 * 并发提交复验只有一个版本生效；相同业务号并发重放只产生一个处置。
 */
@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private ReworkService reworkService;

    private NonConformingRecord newNc(String batchQty, String ncQty) {
        ProductionBatch batch = TestFixtures.createBatch(batchService, batchQty);
        return TestFixtures.registerNc(batchService, batch.getBatchNo(), ncQty);
    }

    private CreateDispositionRequest confirmedRequest(String bizNo, String ncNo,
                                                      String rework, String scrap, String concession) {
        return new CreateDispositionRequest(bizNo, ncNo, new BigDecimal(rework),
                new BigDecimal(scrap), new BigDecimal(concession), java.util.List.of("OP1"), true);
    }

    @Test
    void 两个处置并发确认只有一个消费成功() throws Exception {
        NonConformingRecord nc = newNc("100", "10");
        String d1 = TestFixtures.unique("D");
        String d2 = TestFixtures.unique("D");
        dispositionService.createOrReplay(new CreateDispositionRequest(
                d1, nc.getNcNo(), new BigDecimal("4"), new BigDecimal("3"), new BigDecimal("3"),
                java.util.List.of("OP1"), false));
        dispositionService.createOrReplay(new CreateDispositionRequest(
                d2, nc.getNcNo(), new BigDecimal("6"), new BigDecimal("2"), new BigDecimal("2"),
                java.util.List.of("OP1"), false));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        Runnable confirmD1 = () -> awaitAndCount(start, () -> dispositionService.confirm(d1), success, failure);
        Runnable confirmD2 = () -> awaitAndCount(start, () -> dispositionService.confirm(d2), success, failure);
        Future<?> f1 = pool.submit(confirmD1);
        Future<?> f2 = pool.submit(confirmD2);
        start.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);
        assertThat(batchService.requireNc(nc.getNcNo()).getConsumedQuantity()).isEqualByComparingTo("10");
        // 恰有一个 CONFIRMED
        long confirmed = java.util.stream.Stream.of(d1, d2)
                .filter(b -> dispositionService.requireByBusinessNo(b).getStatus()
                        == DispositionStatus.CONFIRMED)
                .count();
        assertThat(confirmed).isEqualTo(1);
    }

    @Test
    void 相同业务号并发提交只创建一个处置_另一方命中幂等重放() throws Exception {
        NonConformingRecord nc = newNc("100", "10");
        String bizNo = TestFixtures.unique("D");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger replayed = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        var tasks = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> pool.submit((Runnable) () -> awaitAndCount(start, () -> {
                    var r = dispositionService.createOrReplay(
                            confirmedRequest(bizNo, nc.getNcNo(), "4", "3", "3"));
                    if (r.replayed()) {
                        replayed.incrementAndGet();
                    } else {
                        created.incrementAndGet();
                    }
                }, new AtomicInteger(), failure)))
                .toList();
        start.countDown();
        for (Future<?> t : tasks) {
            t.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(failure.get()).isZero();
        assertThat(created.get()).isEqualTo(1);
        assertThat(replayed.get()).isEqualTo(threads - 1);
        assertThat(dispositionService.requireByBusinessNo(bizNo).getStatus())
                .isEqualTo(DispositionStatus.CONFIRMED);
        assertThat(batchService.requireNc(nc.getNcNo()).getStatus()).isEqualTo(NcStatus.IN_REWORK);
        assertThat(batchService.requireNc(nc.getNcNo()).getConsumedQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void 并发完成同一道工序只有一次生效_顺序不被打乱() throws Exception {
        NonConformingRecord nc = newNc("100", "10");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(new CreateDispositionRequest(
                bizNo, nc.getNcNo(), new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("3"),
                java.util.List.of("OP1", "OP2"), true));
        String subBatchNo = "RW-" + bizNo;

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        var tasks = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> pool.submit((Runnable) () -> awaitAndCount(start,
                        () -> reworkService.completeOperation(subBatchNo,
                                new CompleteOperationRequest("OP1", "w" + i)),
                        success, failure)))
                .toList();
        start.countDown();
        for (Future<?> t : tasks) {
            t.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threads - 1);
        // 只完成了 OP1，OP2 尚未完成
        assertThat(reworkService.requireSubBatch(subBatchNo).getCompletedOperationCount()).isEqualTo(1);
        assertThat(reworkService.requireSubBatch(subBatchNo).getStatus())
                .isEqualTo(ReworkStatus.IN_PROGRESS);
    }

    @Test
    void 并发提交复验只有一个新版本生效_旧版本提交失败() throws Exception {
        NonConformingRecord nc = newNc("100", "10");
        String bizNo = TestFixtures.unique("D");
        dispositionService.createOrReplay(new CreateDispositionRequest(
                bizNo, nc.getNcNo(), new BigDecimal("5"), new BigDecimal("2"), new BigDecimal("3"),
                java.util.List.of("OP1"), true));
        String subBatchNo = "RW-" + bizNo;
        reworkService.completeOperation(subBatchNo, new CompleteOperationRequest("OP1", "w"));

        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        var tasks = java.util.stream.IntStream.range(0, threads)
                .mapToObj(i -> pool.submit((Runnable) () -> awaitAndCount(start,
                        () -> reworkService.submitInspection(subBatchNo,
                                new SubmitInspectionRequest(InspectionVerdict.PASSED, 0, "insp", null)),
                        success, failure)))
                .toList();
        start.countDown();
        for (Future<?> t : tasks) {
            t.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(threads - 1);
        assertThat(reworkService.requireSubBatch(subBatchNo).getInspectionVersion()).isEqualTo(1);
    }

    private void awaitAndCount(CountDownLatch start, ThrowingRunnable action,
                               AtomicInteger success, AtomicInteger failure) {
        try {
            start.await();
            action.run();
            success.incrementAndGet();
        } catch (Exception e) {
            failure.incrementAndGet();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
