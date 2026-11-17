package com.stockinsight.service;

import com.stockinsight.api.ApiException;
import com.stockinsight.domain.*;
import com.stockinsight.provider.FinancialDataProvider;
import com.stockinsight.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.domain.DataTypes.*;

@Service
@Transactional(readOnly = true)
public class CompanyQueryService {
    private final CompanyRepository companies;
    private final FinancialStatementRepository statements;
    private final MarketPriceRepository prices;
    private final DataSyncHistoryRepository histories;
    private final FinancialDataProvider provider;

    public CompanyQueryService(CompanyRepository companies, FinancialStatementRepository statements,
                               MarketPriceRepository prices, DataSyncHistoryRepository histories,
                               FinancialDataProvider provider) {
        this.companies = companies;
        this.statements = statements;
        this.prices = prices;
        this.histories = histories;
        this.provider = provider;
    }

    public Envelope<List<CompanySummary>> search(String query, int limit) {
        String keyword = query.strip();
        // LIKE의 %, _를 검색어 문자로 취급해 임의의 전체 조회로 확장되지 않게 합니다.
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%")
            .replace("_", "!_") + "%";
        var found = companies.search(provider.mode(), keyword, pattern, PageRequest.of(0, limit));
        Instant collectedAt = found.stream().map(Company::getCollectedAt).max(Instant::compareTo).orElse(null);
        return Envelope.success(found.stream().map(this::summary).toList(),
            new Meta(provider.mode(), found.stream().map(Company::getSource).distinct().toList(),
                collectedAt, List.of(), null, false));
    }

    public Envelope<CompanyDetail> detail(String stockCode) {
        Company company = company(stockCode);
        MarketPrice price = prices.findFirstByCompanyOrderByClosePriceAsOfDescIdDesc(company).orElse(null);
        PriceValue value = price == null
            ? new PriceValue(null, null, "KRW", DataStatus.NOT_COLLECTED, "수집된 종가가 없습니다.")
            : new PriceValue(price.getClosePrice(), price.getMarketCap(), price.getCurrency(), DataStatus.AVAILABLE, null);
        CompanyDetail detail = new CompanyDetail(company.getStockCode(), company.getCorpCode(), company.getName(),
            company.getMarket(), company.getIndustryCode(), company.getIndustryName(), company.getFiscalMonth(),
            company.getHasPreferredShares(), company.isSupported(), value);
        return Envelope.success(detail, metadata(company, List.of(), price));
    }

    public Envelope<Financials> financials(String stockCode, int years) {
        Company company = company(stockCode);
        if (!company.isSupported()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_INDUSTRY",
                "금융업은 계정 구조가 달라 현재 재무 분석을 지원하지 않습니다.");
        }
        var all = statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(company, "11011");
        // 연도마다 연결을 우선 선택합니다. 연도 사이 기준 변경은 meta에 보존합니다.
        Map<Integer, FinancialStatement> selected = new TreeMap<>(Comparator.reverseOrder());
        for (FinancialStatement statement : all) {
            selected.merge(statement.getFiscalYear(), statement,
                (current, next) -> current.getBasis() == StatementBasis.CFS ? current : next);
        }
        var chosen = selected.values().stream().limit(years).toList();
        MarketPrice price = prices.findFirstByCompanyOrderByClosePriceAsOfDescIdDesc(company).orElse(null);
        Financials data = new Financials(summary(company),
            chosen.isEmpty() ? DataStatus.NOT_COLLECTED : DataStatus.AVAILABLE,
            chosen.isEmpty() ? "수집된 연간 재무제표가 없습니다." : null,
            chosen.stream().map(this::financialYear).toList());
        return Envelope.success(data, metadata(company, chosen, price));
    }

    private Company company(String stockCode) {
        return companies.findByDataModeAndStockCode(provider.mode(), stockCode)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND",
                "해당 종목코드의 기업을 찾을 수 없습니다."));
    }

    private CompanySummary summary(Company c) {
        return new CompanySummary(c.getStockCode(), c.getName(), c.getMarket(), c.isSupported());
    }

    private FinancialYear financialYear(FinancialStatement s) {
        Map<StandardAccount, FinancialAccount> byCode = s.getAccounts().stream()
            .collect(Collectors.toMap(FinancialAccount::getCode, Function.identity()));
        var accounts = Arrays.stream(StandardAccount.values()).map(code -> {
            FinancialAccount account = byCode.get(code);
            boolean available = account != null && account.getAmount() != null;
            OriginalAccount original = account == null ? null : new OriginalAccount(account.getOriginalAccountId(),
                account.getOriginalAccountName(), account.getOriginalAccountDetail(), account.getOriginalAmount());
            return new AccountValue(code, code.label(), available ? account.getAmount() : null, code.unit(),
                available ? DataStatus.AVAILABLE : DataStatus.NOT_COLLECTED,
                available ? null : "수집된 해당 계정 값이 없습니다.", original);
        }).toList();
        return new FinancialYear(s.getFiscalYear(), s.getBasis(), s.getCurrency(), accounts,
            new ShareDisclosure(s.getIssuedCommonShares(), s.getTreasuryCommonShares(),
                s.getOutstandingCommonShares(), s.getPreferredShares(), s.getSharesAsOf(), s.getSharesReceiptNo()),
            s.getNormalizationNotes() == null ? List.of() : s.getNormalizationNotes().lines().toList());
    }

    private Meta metadata(Company company, List<FinancialStatement> selected, MarketPrice price) {
        var periods = selected.stream().map(s -> new StatementMeta(s.getFiscalYear(), s.getReportCode(),
            s.getBasis(), s.getReceiptNo(), s.getFiledAt(), s.getPeriodStart(), s.getPeriodEnd(),
            s.getSource(), s.getCollectedAt())).toList();
        Set<DataSource> sources = new LinkedHashSet<>();
        sources.add(company.getSource());
        selected.forEach(s -> sources.add(s.getSource()));
        PriceMeta priceMeta = null;
        if (price != null) {
            sources.add(price.getClosePriceSource());
            if (price.getMarketCapSource() != null) { sources.add(price.getMarketCapSource()); }
            priceMeta = new PriceMeta(price.getClosePriceAsOf(), price.getClosePriceSource(),
                price.getMarketCapAsOf(), price.getMarketCapSource(), price.getCollectedAt(), "COMMON_STOCK");
        }
        List<Instant> collectionTimes = new ArrayList<>();
        collectionTimes.add(company.getCollectedAt());
        selected.forEach(s -> collectionTimes.add(s.getCollectedAt()));
        if (price != null) { collectionTimes.add(price.getCollectedAt()); }
        boolean stale = Arrays.stream(SyncType.values()).anyMatch(type -> histories
            .findFirstByCompanyAndSyncTypeOrderByCompletedAtDescIdDesc(company, type)
            .map(h -> h.getStatus() == SyncStatus.FAILED).orElse(false));
        return new Meta(provider.mode(), List.copyOf(sources),
            collectionTimes.stream().max(Instant::compareTo).orElse(null), periods, priceMeta, stale);
    }
}
