package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.ScrapRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapRecordRepository extends JpaRepository<ScrapRecord, Long> {

    Optional<ScrapRecord> findByNcId(Long ncId);
}
