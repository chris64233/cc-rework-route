package com.chris64233.reworkroute.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.reworkroute.domain.CorrectionRecord;

public interface CorrectionRecordRepository extends JpaRepository<CorrectionRecord, Long> {

    List<CorrectionRecord> findByDispositionIdOrderByIdAsc(Long dispositionId);
}
