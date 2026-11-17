package com.stockinsight.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.api.MetricModels.*;
import static com.stockinsight.domain.DataTypes.*;

@Service
@Transactional(readOnly = true)
public class MetricQueryService {
    private final CompanyQueryService companies;
    private final FinancialMetricCalculator calculator;

    public MetricQueryService(CompanyQueryService companies, FinancialMetricCalculator calculator) {
        this.companies = companies;
        this.calculator = calculator;
    }

    public Envelope<Metrics> metrics(String stockCode) {
        var financials = companies.financials(stockCode, 5);
        var years = financials.data().years();
        if (years.isEmpty()) {
            return Envelope.success(new Metrics(financials.data().company(), DataStatus.NOT_COLLECTED,
                "수집된 연간 재무제표가 없습니다.", null, null, "KRW", null, null, List.of()), financials.meta());
        }
        var detail = companies.detail(stockCode);
        var current = years.stream().max(Comparator.comparingInt(FinancialYear::fiscalYear)).orElseThrow();
        // 바로 전 사업연도가 없으면 더 오래된 연도로 대신 계산하지 않습니다.
        var previous = years.stream().filter(y -> y.fiscalYear() == current.fiscalYear() - 1).findFirst().orElse(null);
        var period = period(financials.meta(), current);
        var previousPeriod = previous == null ? null : period(financials.meta(), previous);
        var values = calculator.calculate(detail.data(), current, previous, period, previousPeriod, detail.meta().market());
        var data = new Metrics(financials.data().company(), DataStatus.AVAILABLE, null,
            current.fiscalYear(), current.basis(), current.currency(), current.shares(),
            "PER·PBR은 최근 사업연도 재무제표와 종가 기준일을 함께 확인해야 합니다. 실적이 오래되었을 수 있습니다.", values);
        var selected = financials.meta().financialPeriods().stream()
            .filter(p -> p.equals(period) || p.equals(previousPeriod)).toList();
        var meta = financials.meta();
        return Envelope.success(data, new Meta(meta.mode(), meta.sources(), meta.collectedAt(),
            selected, detail.meta().market(), meta.stale() || detail.meta().stale()));
    }

    private StatementMeta period(Meta meta, FinancialYear year) {
        return meta.financialPeriods().stream()
            .filter(p -> p.fiscalYear() == year.fiscalYear() && p.basis() == year.basis()).findFirst().orElse(null);
    }

    public Envelope<ProfitabilityHistory> profitability(String stockCode, int years) {
        var financials = companies.financials(stockCode, years);
        var data = financials.data();
        var history = data.years().stream().map(year -> new ProfitabilityYear(year.fiscalYear(), year.basis(),
            calculator.profitability(year, period(financials.meta(), year)))).toList();
        return Envelope.success(new ProfitabilityHistory(data.company(), data.status(), data.reason(), history), financials.meta());
    }
}
