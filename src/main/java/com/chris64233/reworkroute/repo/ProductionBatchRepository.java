package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.ProductionBatch;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    Optional<ProductionBatch> findByBatchNo(String batchNo);

    boolean existsByBatchNo(String batchNo);

    /** 取行级悲观写锁，串行化同一批次上的库存变动。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductionBatch b where b.id = :id")
    Optional<ProductionBatch> findWithLockById(@Param("id") Long id);
}
