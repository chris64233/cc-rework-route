package com.chris64233.reworkroute.repo;

import com.chris64233.reworkroute.domain.NonconformingRecord;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NonconformingRecordRepository extends JpaRepository<NonconformingRecord, Long> {

    Optional<NonconformingRecord> findByNcNo(String ncNo);

    @Query("select n.id from NonconformingRecord n where n.ncNo = :ncNo")
    Optional<Long> findIdByNcNo(@Param("ncNo") String ncNo);

    @Query("select n.batch.id from NonconformingRecord n where n.ncNo = :ncNo")
    Optional<Long> findBatchIdByNcNo(@Param("ncNo") String ncNo);

    /** 行级悲观写锁：同一不合格记录的两个处置不可能同时确认，杜绝重复消费。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NonconformingRecord n where n.id = :id")
    Optional<NonconformingRecord> findWithLockById(@Param("id") Long id);
}
