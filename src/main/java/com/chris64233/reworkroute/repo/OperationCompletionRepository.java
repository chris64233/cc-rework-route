package com.chris64233.reworkroute.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.reworkroute.domain.OperationCompletion;

public interface OperationCompletionRepository extends JpaRepository<OperationCompletion, Long> {

    List<OperationCompletion> findByReworkSubBatchIdOrderByStepIndexAsc(Long subBatchId);

    boolean existsByReworkSubBatchIdAndStepIndex(Long subBatchId, int stepIndex);
}
