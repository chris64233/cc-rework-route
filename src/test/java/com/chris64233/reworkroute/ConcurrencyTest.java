package com.chris64233.reworkroute;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.reworkroute.domain.ReinspectionDecision;
import com.chris64233.reworkroute.domain.ReworkStatus;
import com.chris64233.reworkroute.repo.DispositionRepository;
import com.chris64233.reworkroute.repo.ReworkSubBatchRepository;
import com.chris64233.reworkroute.repo.ScrapRecordRepository;
import com.chris64233.reworkroute.service.BatchService;
import com.chris64233.reworkroute.service.BusinessConflictException;
import com.chris64233.reworkroute.service.DispositionService;
import com.chris64233.reworkroute.service.ReworkService;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 并发语义测试：
 * - 同一受影响数量不能被两个处置重复消费；
 * - 相同业务号并发重放只能产生一份处置结果；
 * - 并发完成同一道工序只有一次生效，顺序不被破坏；
 * - 复验与工序完成并发时，复验只能在全部工序完成后落地；
 * - 复验合格后并发并回只能有一次把数量加回库存。
 */
@SpringBootTest
@ExtendWith(DatabaseCleaner.class)
class ConcurrencyTest {

    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private ReworkService reworkService;
    @Autowired
    private BatchService batchService;
    @Autowired
    private DispositionRepository dispositionRepository;
    @Autowired
    private ScrapRecordRepository scrapRepository;
    @Autowired
    private ReworkSubBatchRepository subBatchRepository;
    @Autowired
    private com.chris64233.reworkroute.repo.ReinspectionResultRepository resultRepository;
    @Autowired
    private TestFixtures fixtures;

    @Test
    void sameQuantityNotConsumedTwiceConcurrently() throws Exception {
        String ncNo = fixtures.batchWithNc(100, 10);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<String> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                dispositionService.submitDisposition(
                        Thread.currentThread().getName(), ncNo, 10, 0, 0, List.of("OP1"));
                return "OK";
            } catch (BusinessConflictException e) {
                return "CONFLICT";
            }
        };

        List<Future<String>> futures = pool.invokeAll(List.of(task, task), 30, TimeUnit.SECONDS);
        long ok = futures.stream().filter(f -> {
            try {
                return f.get().equals("OK");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).count();

        assertThat(ok).isEqualTo(1);
        assertThat(dispositionRepository.count()).isEqualTo(1);
        assertThat(batchService.getNc(ncNo).getStatus().name()).isEqualTo("DISPOSED");
        // 冻结的 10 全部在返工在途，可用仍为 90，没有被两个处置重复扣减。
        assertThat(batchService.getBatch(
                batchService.getNc(ncNo).getBatch().getBatchNo()).getAvailableQuantity()).isEqualTo(90);
        pool.shutdownNow();
    }

    @Test
    void concurrentSameBusinessNoProducesSingleOutcome() throws Exception {
        String ncNo = fixtures.batchWithNc(100, 30);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<DispositionService.SubmitResult> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return dispositionService.submitDisposition(
                    "DSP-RACE-IDEM", ncNo, 10, 10, 10, List.of("OP1"));
        };

        List<Future<DispositionService.SubmitResult>> futures =
                pool.invokeAll(List.of(task, task), 30, TimeUnit.SECONDS);
        DispositionService.SubmitResult r1 = futures.get(0).get(10, TimeUnit.SECONDS);
        DispositionService.SubmitResult r2 = futures.get(1).get(10, TimeUnit.SECONDS);

        assertThat(r1.disposition().getId()).isEqualTo(r2.disposition().getId());
        assertThat(dispositionRepository.count()).isEqualTo(1);
        assertThat(scrapRepository.count()).isEqualTo(1);
        // 让步 10 只释放一次：100 - 30 + 10 = 80。
        assertThat(batchService.getBatch(
                batchService.getNc(ncNo).getBatch().getBatchNo()).getAvailableQuantity()).isEqualTo(80);
        pool.shutdownNow();
    }

    @Test
    void concurrentSameOperationOnlyOneTakesEffect() throws Exception {
        String ncNo = fixtures.batchWithNc(100, 10);
        String sub = fixtures.disposeAllRework(ncNo, 10, List.of("CUT", "WELD"))
                .disposition().getReworkSubBatch().getSubBatchNo();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<String> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                reworkService.completeOperation(sub, "CUT");
                return "OK";
            } catch (BusinessConflictException e) {
                return "CONFLICT";
            }
        };

        List<Future<String>> futures = pool.invokeAll(List.of(task, task), 30, TimeUnit.SECONDS);
        long ok = futures.stream().filter(f -> {
            try {
                return f.get().equals("OK");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).count();

        assertThat(ok).isEqualTo(1);
        var stored = subBatchRepository.findBySubBatchNo(sub).orElseThrow();
        assertThat(stored.getCompletedSteps()).hasSize(1);
        pool.shutdownNow();
    }

    @Test
    void reinspectionAndLastOperationRaceKeepsOrder() throws Exception {
        // 多跑几个独立子批次提高竞交覆盖：完成全部工序与提交复验并发时，
        // 复验永远只能在工序全部完成后存在。
        ExecutorService pool = Executors.newFixedThreadPool(6);
        for (int round = 0; round < 5; round++) {
            String ncNo = fixtures.batchWithNc(1000, 10);
            String sub = fixtures.disposeAllRework(ncNo, 10, List.of("ONLY"))
                    .disposition().getReworkSubBatch().getSubBatchNo();
            CyclicBarrier barrier = new CyclicBarrier(2);

            Callable<String> complete = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    reworkService.completeOperation(sub, "ONLY");
                    return "DONE";
                } catch (BusinessConflictException e) {
                    return "CONFLICT";
                }
            };
            Callable<String> inspect = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    reworkService.submitReinspection(sub, ReinspectionDecision.PASS, null, "i");
                    return "INSPECTED";
                } catch (BusinessConflictException e) {
                    return "CONFLICT";
                }
            };

            List<Future<String>> futures = pool.invokeAll(List.of(complete, inspect), 30, TimeUnit.SECONDS);
            for (Future<String> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
            var stored = subBatchRepository.findBySubBatchNo(sub).orElseThrow();
            if (stored.getStatus() == ReworkStatus.COMPLETED && stored.getCompletedSteps().size() == 1
                    && hasReinspection(stored.getId())) {
                // 允许：工序先于复验落地。
                continue;
            }
            // 否则复验不得存在（工序尚未被两个线程都完成时，复验一定被拒）。
            org.assertj.core.api.Assertions.assertThat(hasReinspection(stored.getId()))
                    .as("round %d: reinspection must not precede operation completion", round)
                    .isFalse();
        }
        pool.shutdownNow();
    }

    @Test
    void concurrentMergeBackOnlyOnce() throws Exception {
        String batchNo = fixtures.unique("B");
        String ncNo = fixtures.unique("NC");
        batchService.createBatch(batchNo, "P", 100);
        batchService.openNonconformance(ncNo, batchNo, "D", 20);
        String sub = fixtures.disposeAllRework(ncNo, 20, List.of("FIX"))
                .disposition().getReworkSubBatch().getSubBatchNo();
        reworkService.completeOperation(sub, "FIX");
        reworkService.submitReinspection(sub, ReinspectionDecision.PASS, null, "i");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<String> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                reworkService.mergeBack(sub, 1);
                return "MERGED";
            } catch (BusinessConflictException e) {
                return "CONFLICT";
            }
        };

        List<Future<String>> futures = pool.invokeAll(List.of(task, task), 30, TimeUnit.SECONDS);
        long merged = futures.stream().filter(f -> {
            try {
                return f.get().equals("MERGED");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).count();

        assertThat(merged).isEqualTo(1);
        // 只加回一次：100 - 20 + 20 = 100，而不是 120。
        assertThat(batchService.getBatch(batchNo).getAvailableQuantity()).isEqualTo(100);
        pool.shutdownNow();
    }

    private boolean hasReinspection(Long subBatchId) {
        return resultRepository.countBySubBatchId(subBatchId) > 0;
    }
}
