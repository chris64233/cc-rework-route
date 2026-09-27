package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.Disposition;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispositionRepository extends JpaRepository<Disposition, Long> {

    Optional<Disposition> findByBusinessNo(String businessNo);

    Optional<Disposition> findByNcId(Long ncId);

    /** 幂等重放时加锁，避免两个相同业务号请求同时插入/同时判定。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Disposition d where d.businessNo = :businessNo")
    Optional<Disposition> findWithLockByBusinessNo(@Param("businessNo") String businessNo);
}
