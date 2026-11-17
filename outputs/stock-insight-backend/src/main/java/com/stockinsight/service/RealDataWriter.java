package com.stockinsight.service;

import com.stockinsight.domain.*;
import com.stockinsight.provider.ProviderException;
import com.stockinsight.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.stockinsight.domain.DataTypes.*;

@Service
@Profile("real")
public class RealDataWriter {
    private final CompanyRepository companies;
    private final FinancialStatementRepository statements;
    private final MarketPriceRepository prices;
    private final DataSyncHistoryRepository histories;
    private final Clock clock;

    public RealDataWriter(CompanyRepository companies, FinancialStatementRepository statements,
                          MarketPriceRepository prices, DataSyncHistoryRepository histories, Clock clock) {
        this.companies = companies; this.statements = statements; this.prices = prices;
        this.histories = histories; this.clock = clock;
    }

    @Transactional
    public Company company(Company incoming) {
        if (incoming.getDataMode() != DataMode.REAL || incoming.getSource() != DataSource.DART) { throw ProviderException.invalid(); }
        var existing = companies.findByDataModeAndStockCode(DataMode.REAL, incoming.getStockCode());
        if (existing.isEmpty()) { return companies.saveAndFlush(incoming); }
        var company = existing.get();
        if (!company.getCorpCode().equals(incoming.getCorpCode())) { throw ProviderException.invalid(); }
        company.updateProfile(incoming);
        return company;
    }

    @Transactional
    public int financials(Company company, List<FinancialStatement> incoming, Instant startedAt) {
        var managed = companies.findById(company.getId()).orElseThrow();
        if (managed.getDataMode() != DataMode.REAL) { throw ProviderException.invalid(); }
        var saved = statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(managed, "11011");
        var byPeriod = new HashMap<String, FinancialStatement>();
        saved.forEach(s -> byPeriod.put(s.getFiscalYear() + ":" + s.getBasis(), s));
        int count = 0;
        for (FinancialStatement next : incoming) {
            if (!managed.getCorpCode().equals(next.getCompany().getCorpCode()) || next.getSource() != DataSource.DART) {
                throw ProviderException.invalid();
            }
            String key = next.getFiscalYear() + ":" + next.getBasis();
            if (next.getBasis() == StatementBasis.OFS) {
                String cfsKey = next.getFiscalYear() + ":" + StatementBasis.CFS;
                var oldCfs = byPeriod.get(cfsKey);
                if (oldCfs != null && oldCfs.getReceiptNo().compareTo(next.getReceiptNo()) <= 0) {
                    statements.delete(oldCfs);
                    byPeriod.remove(cfsKey);
                }
            }
            FinancialStatement old = byPeriod.get(key);
            if (old != null && old.getReceiptNo().compareTo(next.getReceiptNo()) > 0) { continue; }
            if (old == null) { byPeriod.put(key, statements.save(next)); }
            else { old.replaceFrom(next); }
            count++;
        }
        byPeriod.values().stream().max(Comparator.comparingInt(FinancialStatement::getFiscalYear)
            .thenComparing(s -> s.getBasis() == StatementBasis.CFS)).ifPresent(latest -> {
                Long preferred = latest.getPreferredShares();
                managed.updatePreferredShares(preferred == null ? null : preferred > 0);
            });
        histories.save(new DataSyncHistory(managed, SyncType.FINANCIALS, SyncStatus.SUCCESS, DataSource.DART,
            startedAt, clock.instant(), count, null, null));
        return count;
    }

    @Transactional
    public void price(Company company, MarketPrice incoming, Instant startedAt) {
        if (company.getDataMode() != DataMode.REAL || incoming.getClosePriceSource() != DataSource.FSC) { throw ProviderException.invalid(); }
        var existing = prices.findByCompanyAndClosePriceAsOfAndClosePriceSource(company,
            incoming.getClosePriceAsOf(), incoming.getClosePriceSource());
        if (existing.isEmpty()) { prices.save(incoming); }
        else { existing.get().updateFrom(incoming); }
        histories.save(new DataSyncHistory(company, SyncType.MARKET, SyncStatus.SUCCESS, DataSource.FSC,
            startedAt, clock.instant(), 1, null, null));
    }

    @Transactional
    public void failure(Company company, SyncType type, Instant startedAt, String code, String message) {
        histories.save(new DataSyncHistory(company, type, SyncStatus.FAILED,
            type == SyncType.FINANCIALS ? DataSource.DART : DataSource.FSC, startedAt, clock.instant(), 0, code, message));
    }
}
