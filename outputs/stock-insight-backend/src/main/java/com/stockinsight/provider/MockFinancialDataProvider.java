package com.stockinsight.provider;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static com.stockinsight.domain.DataTypes.*;

@Component
@Profile("mock")
@ConditionalOnProperty(name = "app.data-mode", havingValue = "MOCK")
public class MockFinancialDataProvider implements FinancialDataProvider {
    private final Clock clock;

    public MockFinancialDataProvider(Clock clock) { this.clock = clock; }

    @Override public DataMode mode() { return DataMode.MOCK; }

    @Override
    public List<Company> companies() {
        Instant collectedAt = clock.instant();
        return List.of(
            company("990001", "99000001", "[가상] 한빛제조", "MOCK-C", "제조업", 12, false, true, collectedAt),
            company("990002", "99000002", "[가상] 새봄소재", "MOCK-C", "소재 제조업", 12, false, true, collectedAt),
            company("990003", "99000003", "[가상] 다온전자", "MOCK-C", "전자 제조업", 12, true, true, collectedAt),
            company("990004", "99000004", "[가상] 누리산업", "MOCK-C", "일반 산업", 3, null, true, collectedAt),
            company("990005", "99000005", "[가상] 가람금융", "MOCK-K", "금융업", 12, false, false, collectedAt)
        );
    }

    private Company company(String stock, String corp, String name, String industry, String industryName,
                            int fiscalMonth, Boolean preferred, boolean supported, Instant collectedAt) {
        return new Company(stock, corp, name, Market.KOSPI, industry, industryName, fiscalMonth,
            preferred, supported, DataMode.MOCK, DataSource.MOCK, collectedAt);
    }

    @Override
    public List<FinancialStatement> financialStatements(Company company) {
        if (!company.isSupported()) { return List.of(); }
        List<FinancialStatement> statements = new ArrayList<>();
        int firstYear = company.getStockCode().equals("990004") ? 2024 : 2021;
        for (int year = firstYear; year <= 2025; year++) {
            StatementBasis basis = company.getStockCode().equals("990004") && year == 2024
                ? StatementBasis.OFS : StatementBasis.CFS;
            statements.add(statement(company, year, basis));
        }
        // 같은 연도의 연결/별도가 모두 있어도 연결만 선택하는 조회 정책을 검증합니다.
        if (company.getStockCode().equals("990001")) {
            statements.add(statement(company, 2025, StatementBasis.OFS));
        }
        return statements;
    }

    private FinancialStatement statement(Company company, int year, StatementBasis basis) {
        boolean sparse = company.getStockCode().equals("990004");
        boolean loss = company.getStockCode().equals("990002");
        LocalDate end = LocalDate.of(year, company.getFiscalMonth(), 1)
            .with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        FinancialStatement statement = new FinancialStatement(company, year, basis,
            "MOCK-" + company.getStockCode() + "-" + year + "-" + basis,
            end.plusMonths(3).withDayOfMonth(20), end.minusYears(1).plusDays(1), end,
            "KRW", DataSource.MOCK, clock.instant());

        // 아래 수치는 공시에서 가져온 값이 아닌, 테스트용으로 직접 만든 가상 자료입니다.
        long revenue = 100_000_000_000L + (year - 2021L) * 20_000_000_000L;
        if (basis == StatementBasis.OFS && !sparse) { revenue = 80_000_000_000L; }
        add(statement, StandardAccount.REVENUE, revenue);
        if (!sparse) {
            add(statement, StandardAccount.OPERATING_PROFIT, loss ? -8_000_000_000L : 18_000_000_000L);
            add(statement, StandardAccount.PARENT_NET_INCOME, loss ? -6_000_000_000L : 11_000_000_000L);
            add(statement, StandardAccount.PARENT_EQUITY, 100_000_000_000L);
            add(statement, StandardAccount.BASIC_EPS, loss ? -600L : 1100L);
            statement.setShareDisclosure(10_000_000L, 500_000L, 9_500_000L,
                Boolean.TRUE.equals(company.getHasPreferredShares()) ? 1_000_000L : 0L,
                end, "MOCK-SHARES-" + year + "-" + company.getStockCode());
        }
        add(statement, StandardAccount.NET_INCOME, loss ? -6_000_000_000L : 12_000_000_000L);
        add(statement, StandardAccount.TOTAL_ASSETS, 300_000_000_000L);
        add(statement, StandardAccount.TOTAL_LIABILITIES, 180_000_000_000L);
        add(statement, StandardAccount.TOTAL_EQUITY, 120_000_000_000L);
        add(statement, StandardAccount.OPERATING_CASH_FLOW, loss ? -3_000_000_000L : 20_000_000_000L);
        add(statement, StandardAccount.INVESTING_CASH_FLOW, -15_000_000_000L);
        add(statement, StandardAccount.FINANCING_CASH_FLOW, 0L);
        return statement;
    }

    private void add(FinancialStatement statement, StandardAccount code, long value) {
        String original = Long.toString(value);
        statement.addAccount(code, new BigDecimal(original), "MOCK_" + code.name(),
            code.label(), "가상 예시 계정", original);
    }
}
