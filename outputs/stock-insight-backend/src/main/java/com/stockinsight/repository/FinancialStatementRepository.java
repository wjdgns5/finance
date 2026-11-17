package com.stockinsight.repository;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialStatementRepository extends JpaRepository<FinancialStatement, Long> {
    @EntityGraph(attributePaths = "accounts")
    List<FinancialStatement> findByCompanyAndReportCodeOrderByFiscalYearDesc(Company company, String reportCode);
}
