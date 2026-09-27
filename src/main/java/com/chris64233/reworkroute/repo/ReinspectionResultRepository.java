package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.ReinspectionResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReinspectionResultRepository extends JpaRepository<ReinspectionResult, Long> {

    long countBySubBatchId(Long subBatchId);

    List<ReinspectionResult> findBySubBatchIdOrderByResultVersionAsc(Long subBatchId);

    Optional<ReinspectionResult> findTopBySubBatchIdOrderByResultVersionDesc(Long subBatchId);

    @Query("select coalesce(max(r.resultVersion), 0) from ReinspectionResult r "
            + "where r.subBatch.id = :subBatchId")
    int findMaxVersion(@Param("subBatchId") Long subBatchId);
}
