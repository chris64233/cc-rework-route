package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.ReworkSubBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReworkSubBatchRepository extends JpaRepository<ReworkSubBatch, Long> {

    Optional<ReworkSubBatch> findBySubBatchNo(String subBatchNo);

    Optional<ReworkSubBatch> findByDispositionBusinessNo(String businessNo);

    @Query("select r.id from ReworkSubBatch r where r.subBatchNo = :subBatchNo")
    Optional<Long> findIdBySubBatchNo(@Param("subBatchNo") String subBatchNo);

    @Query("select r.parentBatch.id from ReworkSubBatch r where r.subBatchNo = :subBatchNo")
    Optional<Long> findBatchIdBySubBatchNo(@Param("subBatchNo") String subBatchNo);

    /** 工序完成 / 复验提交 / 放行都在子批次行锁内串行，保证顺序与版本判定。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReworkSubBatch r where r.id = :id")
    Optional<ReworkSubBatch> findWithLockById(@Param("id") Long id);
}
