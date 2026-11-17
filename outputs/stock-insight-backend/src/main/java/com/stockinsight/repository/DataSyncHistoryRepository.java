package com.stockinsight.repository;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.DataSyncHistory;
import com.stockinsight.domain.DataTypes.SyncType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSyncHistoryRepository extends JpaRepository<DataSyncHistory, Long> {
    Optional<DataSyncHistory> findFirstByCompanyAndSyncTypeOrderByCompletedAtDescIdDesc(
        Company company, SyncType syncType);
}
