package com.chris64233.reworkroute.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.reworkroute.domain.InspectionResult;

public interface InspectionResultRepository extends JpaRepository<InspectionResult, Long> {

    List<InspectionResult> findByReworkSubBatchIdOrderByVersionAsc(Long subBatchId);

    Optional<InspectionResult> findByReworkSubBatchIdAndVersion(Long subBatchId, int version);
}
