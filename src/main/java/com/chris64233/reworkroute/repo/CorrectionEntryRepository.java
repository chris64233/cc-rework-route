package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.CorrectionEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CorrectionEntryRepository extends JpaRepository<CorrectionEntry, Long> {

    List<CorrectionEntry> findByDispositionIdOrderByCreatedAtAsc(Long dispositionId);
}
