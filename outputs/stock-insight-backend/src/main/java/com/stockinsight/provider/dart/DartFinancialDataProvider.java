package com.stockinsight.provider.dart;

import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import com.stockinsight.provider.FinancialDataProvider;
import com.stockinsight.provider.ProviderException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import static com.stockinsight.domain.DataTypes.*;
import static com.stockinsight.provider.dart.DartModels.*;

@Component
@Profile("real")
@ConditionalOnProperty(name = "app.data-mode", havingValue = "REAL")
public class DartFinancialDataProvider implements FinancialDataProvider {
    private final DartClient client;
    private final DartNormalizer normalizer;
    private final Clock clock;

    public DartFinancialDataProvider(DartClient client, DartNormalizer normalizer, Clock clock) {
        this.client = client; this.normalizer = normalizer; this.clock = clock;
    }

    @Override public DataMode mode() { return DataMode.REAL; }
    // 실데이터 전체 기업 개황을 시작할 때 수천 번 호출하지 않습니다. 검색된 기업만 등록합니다.
    @Override public List<Company> companies() { return List.of(); }

    @Override public Optional<Company> company(String stockCode) {
        return client.directory().stream().filter(c -> c.stockCode().equals(stockCode)).findFirst().map(this::profile);
    }

    @Override public List<Company> search(String query, int limit) {
        return matchingStockCodes(query, limit).stream().map(code -> company(code).orElseThrow()).toList();
    }

    @Override public List<String> matchingStockCodes(String query, int limit) {
        String keyword = query.toLowerCase(Locale.ROOT);
        return client.directory().stream().filter(c -> c.stockCode().contains(keyword)
            || c.corpName().toLowerCase(Locale.ROOT).contains(keyword))
            .sorted(Comparator.comparing((CorpEntry c) -> !c.stockCode().equals(query)).thenComparing(CorpEntry::corpName))
            .limit(limit).map(CorpEntry::stockCode).toList();
    }

    private Company profile(CorpEntry entry) {
        CompanyProfile p = client.profile(entry.corpCode());
        if (!"000".equals(p.status()) || !entry.corpCode().equals(p.corpCode()) || !entry.stockCode().equals(p.stockCode())
            || p.corpName() == null || p.corpName().isBlank() || p.industryCode() == null
            || !p.industryCode().matches("[0-9]{2,6}") || p.corpClass() == null
            || p.fiscalMonth() == null || !p.fiscalMonth().matches("[0-9]{1,2}")) {
            throw ProviderException.invalid();
        }
        Market market = switch (p.corpClass()) {
            case "Y" -> Market.KOSPI;
            case "K" -> Market.KOSDAQ;
            case "N" -> Market.KONEX;
            default -> throw ProviderException.invalid();
        };
        int month = Integer.parseInt(p.fiscalMonth());
        if (month < 1 || month > 12) { throw ProviderException.invalid(); }
        boolean supported = !List.of("64", "65", "66").contains(p.industryCode().substring(0, 2));
        return new Company(p.stockCode(), p.corpCode(), p.corpName(), market, p.industryCode(),
            supported ? "업종코드 " + p.industryCode() : "금융 및 보험업", month, null, supported,
            DataMode.REAL, DataSource.DART, clock.instant());
    }

    @Override public List<FinancialStatement> financialStatements(Company company) {
        if (company.getDataMode() != DataMode.REAL) { throw ProviderException.invalid(); }
        if (!company.isSupported()) { return List.of(); }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        int latest = today.getYear();
        if (!YearMonth.of(latest, company.getFiscalMonth()).atEndOfMonth().isBefore(today)) { latest--; }
        var filings = client.annualFilings(company.getCorpCode(), 2015);
        // 결산은 끝났지만 사업보고서가 아직 공시되지 않은 연도는 최신 확보 연도로 세지 않습니다.
        int closedYear = latest;
        Pattern annualName = Pattern.compile(".*사업보고서\\s*\\(\\s*([0-9]{4})\\.[0-9]{2}\\s*\\).*", Pattern.DOTALL);
        latest = filings.stream().map(Filing::name).filter(java.util.Objects::nonNull).map(annualName::matcher)
            .filter(java.util.regex.Matcher::matches).mapToInt(m -> Integer.parseInt(m.group(1)))
            .filter(y -> y >= 2015 && y <= closedYear).max().orElse(2014);
        List<FinancialStatement> statements = new ArrayList<>();
        for (int year = latest; year >= Math.max(2015, latest - 4); year--) {
            Pattern name = Pattern.compile(".*사업보고서\\s*\\(\\s*" + year + "\\.[0-9]{2}\\s*\\).*", Pattern.DOTALL);
            Filing filing = filings.stream().filter(f -> f.name() != null && name.matcher(f.name()).matches()
                && company.getCorpCode().equals(f.corpCode()) && f.receiptNo() != null && f.receiptNo().matches("[0-9]{14}")
                && (f.rm() == null || !f.rm().contains("철")))
                .max(Comparator.comparing(Filing::receiptNo)).orElse(null);
            if (filing == null) { continue; }
            var financials = client.financials(company.getCorpCode(), year, "CFS");
            StatementBasis basis = StatementBasis.CFS;
            if (financials.rows().isEmpty()) {
                financials = client.financials(company.getCorpCode(), year, "OFS");
                basis = StatementBasis.OFS;
            }
            if (financials.rows().isEmpty()) { continue; }
            // thstrm_amount만 각 연도의 원본으로 사용합니다. 전기·전전기 값은 다른 연도에 덮어쓰지 않습니다.
            statements.add(normalizer.normalize(company, year, basis, filing, financials,
                client.periods(company.getCorpCode(), year), client.shares(company.getCorpCode(), year), clock.instant()));
        }
        return List.copyOf(statements);
    }
}
