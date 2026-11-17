package com.stockinsight.domain;

import jakarta.persistence.*;
import java.time.Instant;
import static com.stockinsight.domain.DataTypes.*;

@Entity
@Table(name = "data_sync_history", indexes = @Index(
    name = "ix_sync_company_type_completed", columnList = "company_id,sync_type,completed_at"))
public class DataSyncHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Enumerated(EnumType.STRING) @Column(name = "sync_type", nullable = false, length = 16)
    private SyncType syncType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private SyncStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private DataSource source;
    @Column(name = "started_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant startedAt;
    @Column(name = "completed_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant completedAt;
    @Column(name = "items_saved", nullable = false)
    private int itemsSaved;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected DataSyncHistory() { }

    public DataSyncHistory(Company company, SyncType syncType, SyncStatus status, DataSource source,
                           Instant startedAt, Instant completedAt, int itemsSaved,
                           String errorCode, String errorMessage) {
        this.company = company;
        this.syncType = syncType;
        this.status = status;
        this.source = source;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.itemsSaved = itemsSaved;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public SyncStatus getStatus() { return status; }
    public SyncType getSyncType() { return syncType; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
}
