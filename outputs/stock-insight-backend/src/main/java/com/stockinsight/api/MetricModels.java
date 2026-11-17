package com.stockinsight.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.domain.DataTypes.*;

public final class MetricModels {
    private MetricModels() { }

    public enum MetricCode {
        REVENUE_GROWTH, OPERATING_MARGIN, NET_MARGIN, ROE, ROA, DEBT_RATIO, EPS, BPS, PER, PBR
    }

    public enum MetricUnit { PERCENT, KRW_PER_SHARE, TIMES }

    // 상태는 기존 DataStatus 하나로 관리하고, reasonCode는 N/A의 구체적인 원인을 전달합니다.
    public enum MetricReason {
        MISSING_ACCOUNT, ACCOUNT_NOT_AVAILABLE, INVALID_ACCOUNT_UNIT, INVALID_ACCOUNT_VALUE,
        DUPLICATE_ACCOUNT, MISSING_PREVIOUS_YEAR, BASIS_MISMATCH, INVALID_PERIOD,
        MISSING_METADATA, INVALID_CURRENCY, NON_POSITIVE_PREVIOUS_REVENUE,
        NON_POSITIVE_REVENUE, NON_POSITIVE_PARENT_EQUITY, NON_POSITIVE_ASSETS,
        NON_POSITIVE_EQUITY, NON_POSITIVE_EPS, NON_POSITIVE_BPS, PREFERRED_SHARES,
        UNKNOWN_PREFERRED_SHARES, MISSING_SHARES, INVALID_SHARES, SHARE_DATE_MISMATCH,
        MISSING_PRICE, INVALID_PRICE
    }

    @Schema(description = "최신 확보 연간 지표. 자료가 없으면 fiscalYear=null, metrics=[]입니다.")
    public record Metrics(CompanySummary company, DataStatus status, @Schema(nullable = true) String reason,
                          @Schema(nullable = true) Integer fiscalYear, @Schema(nullable = true) StatementBasis basis,
                          @Schema(nullable = true) String currency,
                          @Schema(nullable = true) ShareDisclosure shares, String priceNote, List<MetricValue> metrics) { }

    public record ProfitabilityHistory(CompanySummary company, DataStatus status, String reason,
                                       List<ProfitabilityYear> years) { }
    public record ProfitabilityYear(int fiscalYear, StatementBasis basis, List<MetricValue> metrics) { }

    public record MetricValue(MetricCode code, String label,
                              @Schema(implementation = String.class, type = "string", nullable = true, example = "10.000000",
                                  description = "계산 결과 문자열. PERCENT의 15는 15%. N/A는 null")
                              @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value,
                              MetricUnit unit, DataStatus status, @Schema(nullable = true) MetricReason reasonCode,
                              @Schema(nullable = true) String reason, String formula, List<StandardAccount> usedAccounts,
                              String explanation, String naConditions, String basisNote,
                              @Schema(nullable = true, description = "PER·PBR의 종가 기준일. 다른 지표는 null") LocalDate priceAsOf) { }
}
