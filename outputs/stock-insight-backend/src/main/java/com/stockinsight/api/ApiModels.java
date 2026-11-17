package com.stockinsight.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static com.stockinsight.domain.DataTypes.*;

public final class ApiModels {
    private ApiModels() { }

    @Schema(description = "공통 응답. 성공 시 error=null, 오류 시 data=null입니다.")
    public record Envelope<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) T data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Meta meta,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ErrorDetail error) {
        public static <T> Envelope<T> success(T data, Meta meta) {
            return new Envelope<>(data, meta, null);
        }
    }

    @Schema(description = "모드·출처·공시 기간·시세 기준일. 수집 시각은 UTC입니다.")
    public record Meta(DataMode mode, List<DataSource> sources,
                       @Schema(nullable = true, description = "응답을 구성한 자료의 최대 수집 시각. 오류에서는 null") Instant collectedAt,
                       List<StatementMeta> financialPeriods,
                       @Schema(nullable = true, description = "시세가 없으면 null") PriceMeta market,
                       @Schema(description = "재무 또는 시세 최신 수집 이력에 실패가 있는지 여부. false가 최신성을 보장하지 않음") boolean stale) { }

    @Schema(description = "실제 공시 기간·접수번호·연결/별도 기준. 서로 다른 응답의 기준을 맞출 때 사용합니다.")
    public record StatementMeta(int fiscalYear, @Schema(example = "11011") String reportCode, StatementBasis basis,
                                String receiptNo, LocalDate filedAt, LocalDate periodStart,
                                LocalDate periodEnd, DataSource source, Instant collectedAt) { }

    public record PriceMeta(LocalDate closePriceAsOf, DataSource closePriceSource,
                            LocalDate marketCapAsOf, DataSource marketCapSource,
                            Instant collectedAt, String scope) { }

    public record CompanySummary(@Schema(description = "6자리 문자열. 선행 0 유지", pattern = "^[0-9]{6}$") String stockCode,
                                 String name, Market market, boolean supported) { }

    public record CompanyDetail(String stockCode, String corpCode, String name, Market market,
                                String industryCode, String industryName, int fiscalMonth,
                                @Schema(nullable = true, description = "true 발행 / false 미발행 확인 / null 미확인") Boolean hasPreferredShares,
                                boolean supported, PriceValue price) { }

    public record PriceValue(@Schema(implementation = String.class, type = "string", nullable = true, example = "32000.000000", description = "원 단위 종가. 시세가 없으면 null")
                             @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal closePrice,
                             @Schema(implementation = String.class, type = "string", nullable = true, example = "304000000000.000000", description = "보통주 종목 범위 시가총액, 원 단위")
                             @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal marketCap,
                             String currency, DataStatus status, String reason) { }

    @Schema(description = "연간 재무. 자료가 없으면 NOT_COLLECTED와 빈 years를 반환할 수 있습니다.")
    public record Financials(CompanySummary company, DataStatus status, @Schema(nullable = true) String reason,
                            List<FinancialYear> years) { }

    public record FinancialYear(int fiscalYear, StatementBasis basis, String currency,
                                List<AccountValue> accounts, ShareDisclosure shares,
                                List<String> normalizationWarnings) {
        public FinancialYear(int fiscalYear, StatementBasis basis, String currency,
                             List<AccountValue> accounts, ShareDisclosure shares) {
            this(fiscalYear, basis, currency, accounts, shares, List.of());
        }
    }

    @Schema(description = "재무·시세 각각의 결과. sync HTTP 200만으로 전체 성공이라고 판단하지 않습니다.")
    public record SyncOutcome(SyncType type, SyncStatus status, int itemsSaved, @Schema(nullable = true) String errorCode, String message) { }
    public record CollectionResult(String stockCode, List<SyncOutcome> outcomes) { }

    public record AccountValue(StandardAccount code, String label,
                               @Schema(implementation = String.class, type = "string", nullable = true, example = "180000000000.000000",
                                   description = "원 단위 금액 또는 공시 EPS 문자열. N/A는 null이며 실제 0·음수와 구분")
                               @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value,
                               AccountUnit unit, DataStatus status, @Schema(nullable = true) String reason,
                               OriginalAccount original) { }

    public record OriginalAccount(String accountId, String accountName, String accountDetail,
                                  String amount) { }

    public record ShareDisclosure(
        @Schema(implementation = String.class, type = "string", nullable = true, example = "10000000", description = "발행보통주식수")
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long issuedCommonShares,
        @Schema(implementation = String.class, type = "string", nullable = true, example = "500000", description = "자기주식 중 보통주식수")
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long treasuryCommonShares,
        @Schema(implementation = String.class, type = "string", nullable = true, example = "9500000", description = "자기주식 제외 유통보통주식수. 임의 역산하지 않음")
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long outstandingCommonShares,
        @Schema(implementation = String.class, type = "string", nullable = true, example = "0", description = "공시에서 확인한 우선주식수. null은 미확인")
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long preferredShares,
        @Schema(nullable = true, description = "주식수 기준일. BPS는 재무 결산일과 일치해야 함") LocalDate asOf,
        @Schema(nullable = true) String receiptNo) { }

    public record ErrorDetail(String code, String message, List<FieldError> fields) { }
    public record FieldError(String field, String message) { }
}
