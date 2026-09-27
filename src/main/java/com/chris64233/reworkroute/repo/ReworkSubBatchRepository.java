package com.chris64233.reworkroute.repo;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.reworkroute.domain.ReworkSubBatch;

public interface ReworkSubBatchRepository extends JpaRepository<ReworkSubBatch, Long> {

    Optional<ReworkSubBatch> findBySubBatchNo(String subBatchNo);

    List<ReworkSubBatch> findByNcRecordNcNo(String ncNo);

    /** 行级写锁，串行化同一返工子批次的工序完成与复验提交。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReworkSubBatch r where r.subBatchNo = :subBatchNo")
    Optional<ReworkSubBatch> findWithLockingBySubBatchNo(@Param("subBatchNo") String subBatchNo);
}
