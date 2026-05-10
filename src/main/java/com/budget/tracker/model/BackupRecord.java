package com.budget.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "backup_records")
@Getter
@Setter
public class BackupRecord extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String format;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false)
    private String status;
}
