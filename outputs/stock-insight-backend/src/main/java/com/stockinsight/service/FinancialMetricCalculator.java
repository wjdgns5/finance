package com.stockinsight.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.api.MetricModels.*;
import static com.stockinsight.domain.DataTypes.*;

@Component
public class FinancialMetricCalculator {
    private static final int SCALE = 6;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public List<MetricValue> calculate(CompanyDetail company, FinancialYear current,
                                     FinancialYear previous, StatementMeta period,
                                     StatementMeta previousPeriod, PriceMeta priceMeta) {
        return calculateCodes(Arrays.asList(MetricCode.values()), company, current, previous, period, previousPeriod, priceMeta);
    }

    public List<MetricValue> profitability(FinancialYear current, StatementMeta period) {
        // 과거 시세를 가정하지 않습니다. 차트에 필요한 당해 연도 이익률 두 개만 같은 엔진으로 산출합니다.
        return calculateCodes(List.of(MetricCode.OPERATING_MARGIN, MetricCode.NET_MARGIN),
            null, current, null, period, null, null);
    }

    private List<MetricValue> calculateCodes(List<MetricCode> codes, CompanyDetail company,
        FinancialYear current, FinancialYear previous, StatementMeta period, StatementMeta previousPeriod, PriceMeta priceMeta) {
        return codes.stream().map(code -> {
            List<StandardAccount> accounts = accounts(code, current.basis());
            LocalDate priceDate = code == MetricCode.PER || code == MetricCode.PBR
                ? priceMeta == null ? null : priceMeta.closePriceAsOf() : null;
            try {
                validatePeriod(current, period);
                BigDecimal value = value(code, company, current, previous, period, previousPeriod, priceMeta);
                return result(code, accounts, value, DataStatus.AVAILABLE, null, null, priceDate);
            } catch (Unavailable ex) {
                return result(code, accounts, null, ex.status, ex.reason, ex.getMessage(), priceDate);
            }
        }).toList();
    }

    private BigDecimal value(MetricCode code, CompanyDetail company, FinancialYear current,
                             FinancialYear previous, StatementMeta period,
                             StatementMeta previousPeriod, PriceMeta priceMeta) {
        return switch (code) {
            case REVENUE_GROWTH -> growth(current, previous, period, previousPeriod);
            case OPERATING_MARGIN -> percentage(account(current, StandardAccount.OPERATING_PROFIT),
                positive(account(current, StandardAccount.REVENUE), MetricReason.NON_POSITIVE_REVENUE, "매출액이 0 이하입니다."));
            case NET_MARGIN -> percentage(account(current, StandardAccount.NET_INCOME),
                positive(account(current, StandardAccount.REVENUE), MetricReason.NON_POSITIVE_REVENUE, "매출액이 0 이하입니다."));
            case ROE -> percentage(account(current, StandardAccount.PARENT_NET_INCOME),
                positive(account(current, StandardAccount.PARENT_EQUITY), MetricReason.NON_POSITIVE_PARENT_EQUITY, "기말 지배주주 지분이 0 이하입니다."));
            case ROA -> percentage(account(current, StandardAccount.NET_INCOME),
                positive(account(current, StandardAccount.TOTAL_ASSETS), MetricReason.NON_POSITIVE_ASSETS, "기말 자산총계가 0 이하입니다."));
            case DEBT_RATIO -> {
                BigDecimal liabilities = account(current, StandardAccount.TOTAL_LIABILITIES);
                if (liabilities.signum() < 0) {
                    throw invalid(MetricReason.INVALID_ACCOUNT_VALUE, "부채총계가 음수인 비정상 입력입니다.");
                }
                yield percentage(liabilities, positive(account(current, StandardAccount.TOTAL_EQUITY),
                    MetricReason.NON_POSITIVE_EQUITY, "자본총계가 0 이하입니다."));
            }
            // EPS는 공시값을 보존합니다. 순이익과 주식수로 역산하거나 반올림하지 않습니다.
            case EPS -> account(current, StandardAccount.BASIC_EPS);
            case BPS -> {
                long shares = shares(company, current, period);
                yield divide(commonEquity(current), BigDecimal.valueOf(shares));
            }
            case PER -> {
                BigDecimal close = close(company, priceMeta);
                BigDecimal eps = positive(account(current, StandardAccount.BASIC_EPS),
                    MetricReason.NON_POSITIVE_EPS, "공시 기본주당이익이 0 이하라 PER을 산출하지 않습니다.");
                yield divide(close, eps);
            }
            case PBR -> {
                BigDecimal close = close(company, priceMeta);
                long shares = shares(company, current, period);
                BigDecimal equity = positive(commonEquity(current), MetricReason.NON_POSITIVE_BPS,
                    "보통주 귀속 순자산이 0 이하라 PBR을 산출하지 않습니다.");
                // close / (equity / shares)를 대수적으로 변형해 중간 BPS 반올림을 피합니다.
                yield divide(close.multiply(BigDecimal.valueOf(shares)), equity);
            }
        };
    }

    private BigDecimal growth(FinancialYear current, FinancialYear previous, StatementMeta period,
                              StatementMeta previousPeriod) {
        if (previous == null || previous.fiscalYear() != current.fiscalYear() - 1) {
            throw missing(MetricReason.MISSING_PREVIOUS_YEAR, "바로 전 사업연도의 연간 재무제표가 없습니다.");
        }
        validatePeriod(previous, previousPeriod);
        if (current.basis() != previous.basis()) {
            throw invalid(MetricReason.BASIS_MISMATCH, "전기와 당기의 연결/별도 기준이 달라 비교하지 않습니다.");
        }
        if (!previousPeriod.periodEnd().isBefore(period.periodStart())) {
            throw invalid(MetricReason.INVALID_PERIOD, "비교 기간이 겹치는 비정상 입력입니다.");
        }
        BigDecimal priorRevenue = positive(account(previous, StandardAccount.REVENUE),
            MetricReason.NON_POSITIVE_PREVIOUS_REVENUE, "전기 매출액이 0 이하입니다.");
        return percentage(account(current, StandardAccount.REVENUE).subtract(priorRevenue), priorRevenue);
    }

    private void validatePeriod(FinancialYear year, StatementMeta period) {
        if (period == null || period.periodStart() == null || period.periodEnd() == null) {
            throw missing(MetricReason.MISSING_METADATA, "재무제표 기간 메타데이터가 없습니다.");
        }
        if (year.fiscalYear() <= 0 || year.basis() == null || period.fiscalYear() != year.fiscalYear()
            || period.basis() != year.basis() || !"11011".equals(period.reportCode())
            || period.periodStart().isAfter(period.periodEnd())
            || period.periodEnd().getYear() != year.fiscalYear()) {
            throw invalid(MetricReason.INVALID_PERIOD, "연간 재무제표의 연도·기간·연결/별도 정보가 일치하지 않습니다.");
        }
        if (!"KRW".equals(year.currency())) {
            throw invalid(MetricReason.INVALID_CURRENCY, "현재 계산 엔진은 원화 재무제표만 지원합니다.");
        }
    }

    private BigDecimal account(FinancialYear year, StandardAccount code) {
        var matches = year.accounts() == null ? List.<AccountValue>of() : year.accounts().stream()
            .filter(a -> a != null && a.code() == code).toList();
        if (matches.isEmpty()) {
            throw missing(MetricReason.MISSING_ACCOUNT, code.label() + " 계정이 없습니다.");
        }
        if (matches.size() != 1) {
            throw invalid(MetricReason.DUPLICATE_ACCOUNT, code.label() + " 계정이 중복되어 선택할 수 없습니다.");
        }
        AccountValue account = matches.getFirst();
        if (account.status() != DataStatus.AVAILABLE) {
            throw missing(MetricReason.ACCOUNT_NOT_AVAILABLE, code.label() + " 계정이 사용 가능한 상태가 아닙니다.");
        }
        if (account.value() == null) {
            throw missing(MetricReason.MISSING_ACCOUNT, code.label() + " 값이 없습니다.");
        }
        if (account.unit() != code.unit()) {
            throw invalid(MetricReason.INVALID_ACCOUNT_UNIT, code.label() + " 계정의 단위가 올바르지 않습니다.");
        }
        return account.value();
    }

    private BigDecimal commonEquity(FinancialYear year) {
        return account(year, year.basis() == StatementBasis.CFS
            ? StandardAccount.PARENT_EQUITY : StandardAccount.TOTAL_EQUITY);
    }

    private long shares(CompanyDetail company, FinancialYear year, StatementMeta period) {
        ShareDisclosure s = year.shares();
        if (Boolean.TRUE.equals(company.hasPreferredShares())
            || s != null && s.preferredShares() != null && s.preferredShares() > 0) {
            throw invalid(MetricReason.PREFERRED_SHARES,
                "우선주 발행 기업은 보통주 귀속 순자산을 분리할 수 없어 BPS·PBR을 산출하지 않습니다.");
        }
        if (!Boolean.FALSE.equals(company.hasPreferredShares()) || s == null || s.preferredShares() == null) {
            throw missing(MetricReason.UNKNOWN_PREFERRED_SHARES, "우선주 미발행 여부를 확인할 수 없습니다.");
        }
        if (s.outstandingCommonShares() == null || s.asOf() == null
            || s.receiptNo() == null || s.receiptNo().isBlank()) {
            throw missing(MetricReason.MISSING_SHARES, "기준일과 출처가 확인된 유통보통주식수가 없습니다.");
        }
        if (s.outstandingCommonShares() <= 0 || s.preferredShares() < 0
            || s.issuedCommonShares() != null && s.issuedCommonShares() < 0
            || s.treasuryCommonShares() != null && s.treasuryCommonShares() < 0
            || s.issuedCommonShares() != null && s.outstandingCommonShares() > s.issuedCommonShares()
            || s.issuedCommonShares() != null && s.treasuryCommonShares() != null
                && s.issuedCommonShares() - s.treasuryCommonShares() != s.outstandingCommonShares()) {
            throw invalid(MetricReason.INVALID_SHARES, "유통보통주식수가 0 이하이거나 발행·자기주식수와 일치하지 않습니다.");
        }
        if (!s.asOf().equals(period.periodEnd())) {
            throw invalid(MetricReason.SHARE_DATE_MISMATCH, "주식수 기준일이 재무제표 결산일과 다릅니다.");
        }
        return s.outstandingCommonShares();
    }

    private BigDecimal close(CompanyDetail company, PriceMeta meta) {
        PriceValue price = company.price();
        if (price == null || price.status() != DataStatus.AVAILABLE || price.closePrice() == null
            || meta == null || meta.closePriceAsOf() == null || meta.closePriceSource() == null) {
            throw missing(MetricReason.MISSING_PRICE, "기준일과 출처가 확인된 종가가 없습니다.");
        }
        if (price.closePrice().signum() <= 0 || !"KRW".equals(price.currency())
            || !"COMMON_STOCK".equals(meta.scope())) {
            throw invalid(MetricReason.INVALID_PRICE, "종가가 0 이하이거나 통화·보통주 범위가 올바르지 않습니다.");
        }
        return price.closePrice();
    }

    private BigDecimal positive(BigDecimal value, MetricReason reason, String message) {
        if (value.signum() <= 0) { throw invalid(reason, message); }
        return value;
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        return divide(numerator.multiply(HUNDRED), denominator);
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private static Unavailable missing(MetricReason reason, String message) {
        return new Unavailable(DataStatus.NOT_COLLECTED, reason, message);
    }

    private static Unavailable invalid(MetricReason reason, String message) {
        return new Unavailable(DataStatus.NOT_APPLICABLE, reason, message);
    }

    private static final class Unavailable extends RuntimeException {
        private final DataStatus status;
        private final MetricReason reason;
        private Unavailable(DataStatus status, MetricReason reason, String message) {
            super(message);
            this.status = status;
            this.reason = reason;
        }
    }

    private List<StandardAccount> accounts(MetricCode code, StatementBasis basis) {
        return switch (code) {
            case REVENUE_GROWTH -> List.of(StandardAccount.REVENUE);
            case OPERATING_MARGIN -> List.of(StandardAccount.OPERATING_PROFIT, StandardAccount.REVENUE);
            case NET_MARGIN -> List.of(StandardAccount.NET_INCOME, StandardAccount.REVENUE);
            case ROE -> List.of(StandardAccount.PARENT_NET_INCOME, StandardAccount.PARENT_EQUITY);
            case ROA -> List.of(StandardAccount.NET_INCOME, StandardAccount.TOTAL_ASSETS);
            case DEBT_RATIO -> List.of(StandardAccount.TOTAL_LIABILITIES, StandardAccount.TOTAL_EQUITY);
            case EPS, PER -> List.of(StandardAccount.BASIC_EPS);
            case BPS, PBR -> List.of(basis == StatementBasis.CFS ? StandardAccount.PARENT_EQUITY : StandardAccount.TOTAL_EQUITY);
        };
    }

    private MetricValue result(MetricCode code, List<StandardAccount> accounts, BigDecimal value,
                               DataStatus status, MetricReason reasonCode, String reason, LocalDate priceDate) {
        String label = switch (code) {
            case REVENUE_GROWTH -> "매출액 증가율";
            case OPERATING_MARGIN -> "영업이익률";
            case NET_MARGIN -> "순이익률";
            case ROE -> "ROE";
            case ROA -> "ROA";
            case DEBT_RATIO -> "부채비율";
            case EPS -> "EPS";
            case BPS -> "BPS";
            case PER -> "PER";
            case PBR -> "PBR";
        };
        String formula = switch (code) {
            case REVENUE_GROWTH -> "(당기 매출액 − 전기 매출액) ÷ 전기 매출액 × 100";
            case OPERATING_MARGIN -> "영업이익 ÷ 매출액 × 100";
            case NET_MARGIN -> "당기순이익 ÷ 매출액 × 100";
            case ROE -> "지배주주 귀속 순이익 ÷ 기말 지배주주 지분 × 100";
            case ROA -> "당기순이익 ÷ 기말 자산총계 × 100";
            case DEBT_RATIO -> "부채총계 ÷ 자본총계 × 100";
            case EPS -> "공시된 보통주 기본주당이익";
            case BPS -> "보통주 귀속 순자산 ÷ 결산일 유통보통주식수(자기주식 제외)";
            case PER -> "종가 ÷ 공시 기본주당이익";
            case PBR -> "종가 ÷ BPS";
        };
        String explanation = switch (code) {
            case REVENUE_GROWTH -> "같은 재무제표 기준에서 매출이 전년보다 얼마나 변했는지 보여줍니다.";
            case OPERATING_MARGIN -> "매출 중 영업이익이 차지하는 비율입니다.";
            case NET_MARGIN -> "매출 중 최종 순이익이 차지하는 비율입니다.";
            case ROE -> "기말 지배주주 지분에 비해 지배주주 귀속 순이익이 얼마나 발생했는지 보여줍니다.";
            case ROA -> "기말 자산에 비해 순이익이 얼마나 발생했는지 보여줍니다.";
            case DEBT_RATIO -> "자본에 비해 부채가 어느 정도인지 보여줍니다.";
            case EPS -> "공시된 보통주 한 주당 기본이익이며 직접 추정하지 않습니다.";
            case BPS -> "확인된 보통주 한 주당 순자산입니다. 우선주 발행 기업은 산출하지 않습니다.";
            case PER -> "주가가 최근 사업연도 공시 기본주당이익의 몇 배인지 보여줍니다.";
            case PBR -> "주가가 최근 사업연도 보통주 한 주당 순자산의 몇 배인지 보여줍니다.";
        };
        String naConditions = switch (code) {
            case REVENUE_GROWTH -> "전기 없음, 전기 매출액 0 이하, 연결/별도 기준 변경, 계정·메타데이터 부족 또는 비정상";
            case OPERATING_MARGIN, NET_MARGIN -> "매출액 0 이하, 계정·메타데이터 부족 또는 비정상";
            case ROE -> "기말 지배주주 지분 0 이하, 계정·메타데이터 부족 또는 비정상";
            case ROA -> "기말 자산총계 0 이하, 계정·메타데이터 부족 또는 비정상";
            case DEBT_RATIO -> "자본총계 0 이하, 부채 음수, 계정·메타데이터 부족 또는 비정상";
            case EPS -> "공시 보통주 기본주당이익 없음, 계정·메타데이터 부족 또는 비정상";
            case BPS -> "우선주 발행·확인 불가, 결산일 유통보통주식수 없음·불일치, 계정 부족 또는 비정상";
            case PER -> "EPS 0 이하·N/A, 종가·기준일·출처 없음 또는 비정상";
            case PBR -> "BPS 0 이하·N/A, 종가·기준일·출처 없음 또는 비정상";
        };
        MetricUnit unit = switch (code) {
            case EPS, BPS -> MetricUnit.KRW_PER_SHARE;
            case PER, PBR -> MetricUnit.TIMES;
            default -> MetricUnit.PERCENT;
        };
        String basisNote = switch (code) {
            case ROE, ROA -> "연간 · 기말 기준";
            case EPS -> "연간 · 공시 보통주 기본주당이익";
            case BPS -> "연간 · 결산일 보통주 기준";
            case PER, PBR -> "최근 사업연도 재무제표와 표시된 기준일의 보통주 종가 사용";
            default -> "연간 기준";
        };
        return new MetricValue(code, label, value, unit, status, reasonCode, reason,
            formula, accounts, explanation, naConditions, basisNote, priceDate);
    }
}
