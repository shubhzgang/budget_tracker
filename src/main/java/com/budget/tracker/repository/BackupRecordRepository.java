package com.budget.tracker.repository;

import com.budget.tracker.model.BackupRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BackupRecordRepository extends JpaRepository<BackupRecord, UUID> {
    List<BackupRecord> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
