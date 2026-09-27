package com.chris64233.reworkroute.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.reworkroute.domain.BatchGenealogyEdge;

public interface BatchGenealogyEdgeRepository extends JpaRepository<BatchGenealogyEdge, Long> {

    List<BatchGenealogyEdge> findByNcRecordNcNoOrderByIdAsc(String ncNo);

    List<BatchGenealogyEdge> findByReworkSubBatchIdOrderByIdAsc(Long subBatchId);

    List<BatchGenealogyEdge> findBySourceBatchIdOrTargetBatchIdOrderByIdAsc(Long sourceId, Long targetId);
}
