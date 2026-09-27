package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.StockMovement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByNcIdOrderByCreatedAtAsc(Long ncId);

    List<StockMovement> findByBatchIdOrderByCreatedAtAsc(Long batchId);
}
