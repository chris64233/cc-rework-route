package com.chris64233.reworkroute.repo;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chris64233.reworkroute.domain.NonConformingRecord;

public interface NonConformingRecordRepository extends JpaRepository<NonConformingRecord, Long> {

    Optional<NonConformingRecord> findByNcNo(String ncNo);

    List<NonConformingRecord> findByBatchBatchNo(String batchNo);

    /**
     * 行级写锁加载不合格记录。确认处置时必须先持锁再校验剩余数量，
     * 保证两个并发处置不可能同时通过“数量未消费”检查。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NonConformingRecord n where n.ncNo = :ncNo")
    Optional<NonConformingRecord> findWithLockingByNcNo(@Param("ncNo") String ncNo);
}
