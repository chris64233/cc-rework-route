package com.chris64233.reworkroute.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chris64233.reworkroute.domain.DispositionOrder;

public interface DispositionOrderRepository extends JpaRepository<DispositionOrder, Long> {

    Optional<DispositionOrder> findByBusinessNo(String businessNo);

    List<DispositionOrder> findByNcRecordNcNoOrderByIdAsc(String ncNo);
}
