package com.stockinsight.provider.dart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockinsight.domain.Company;
import com.stockinsight.domain.FinancialStatement;
import com.stockinsight.provider.ProviderException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static com.stockinsight.domain.DataTypes.*;
import static com.stockinsight.provider.dart.DartModels.*;

@Component
public class DartNormalizer {
    private static final Map<String, StandardAccount> IDS = Map.ofEntries(
        Map.entry("Revenue", StandardAccount.REVENUE),
        Map.entry("OperatingIncomeLoss", StandardAccount.OPERATING_PROFIT),
        Map.entry("ProfitLoss", StandardAccount.NET_INCOME),
        Map.entry("ProfitLossAttributableToOwnersOfParent", StandardAccount.PARENT_NET_INCOME),
        Map.entry("Assets", StandardAccount.TOTAL_ASSETS),
        Map.entry("Liabilities", StandardAccount.TOTAL_LIABILITIES),
        Map.entry("Equity", StandardAccount.TOTAL_EQUITY),
        Map.entry("EquityAttributableToOwnersOfParent", StandardAccount.PARENT_EQUITY),
        Map.entry("CashFlowsFromUsedInOperatingActivities", StandardAccount.OPERATING_CASH_FLOW),
        Map.entry("CashFlowsFromUsedInInvestingActivities", StandardAccount.INVESTING_CASH_FLOW),
        Map.entry("CashFlowsFromUsedInFinancingActivities", StandardAccount.FINANCING_CASH_FLOW),
        Map.entry("BasicEarningsLossPerShare", StandardAccount.BASIC_EPS));
    private static final Pattern DATE = Pattern.compile("([0-9]{4})[.\\-/]([0-9]{1,2})[.\\-/]([0-9]{1,2})");
    private final ObjectMapper mapper;
    public DartNormalizer(ObjectMapper mapper) { this.mapper = mapper; }

    public FinancialStatement normalize(Company company, int year, StatementBasis basis, Filing filing,
        FinancialResponse response, PeriodResponse periods, SharesResponse shares, Instant collectedAt) {
        if (response.rows().isEmpty() || filing == null || !company.getCorpCode().equals(filing.corpCode())
            || filing.receiptNo() == null || !filing.receiptNo().matches("[0-9]{14}")) { throw ProviderException.invalid(); }
        String latest = response.rows().stream().map(FinancialRow::receiptNo)
            .filter(r -> r != null && r.matches("[0-9]{14}")).max(String::compareTo).orElseThrow(ProviderException::invalid);
        if (!latest.equals(filing.receiptNo())) {
            throw new ProviderException(org.springframework.http.HttpStatus.BAD_GATEWAY, "DART_REVISION_PENDING",
                "최신 공시와 재무 API의 접수번호가 달라 정정본 반영 여부를 확인해야 합니다.");
        }
        List<String> notes = new ArrayList<>();
        List<LocalDate> range = period(company, year, basis, latest, periods.rows());
        LocalDate filedAt;
        try { filedAt = LocalDate.parse(filing.filedAt(), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception ex) { throw ProviderException.invalid(); }
        var statement = new FinancialStatement(company, year, basis, latest,
            filedAt, range.get(0), range.get(1),
            "KRW", DataSource.DART, collectedAt);
        shareDisclosure(statement, company, shares.rows(), notes);
        Map<StandardAccount, List<FinancialRow>> candidates = new EnumMap<>(StandardAccount.class);
        for (FinancialRow row : response.rows()) {
            if (!latest.equals(row.receiptNo())) { notes.add("OLDER_RECEIPT_IGNORED:" + row.receiptNo()); continue; }
            if (!company.getCorpCode().equals(row.corpCode()) || !String.valueOf(year).equals(row.fiscalYear())
                || !"11011".equals(row.reportCode()) || !"KRW".equals(row.currency())) { throw ProviderException.invalid(); }
            if ("SCE".equals(row.statementType())) { continue; }
            StandardAccount code = mapped(row);
            if (code == null) { notes.add("UNMAPPED_ACCOUNT:" + row.statementType() + ":" + row.accountId() + ":" + row.accountName()); continue; }
            if (code == StandardAccount.BASIC_EPS && !Long.valueOf(0).equals(statement.getPreferredShares())
                && (row.accountName() == null || !row.accountName().contains("보통주") || row.accountName().contains("우선"))) {
                notes.add("EPS_COMMON_SCOPE_UNCONFIRMED:" + row.accountId()); continue;
            }
            candidates.computeIfAbsent(code, ignored -> new ArrayList<>()).add(row);
        }
        candidates.forEach((code, rows) -> {
            var row = singleAccount(rows);
            if (row == null) { notes.add("AMBIGUOUS_ACCOUNT:" + code); return; }
            try {
                BigDecimal amount = amount(row.amount());
                if (amount == null) { notes.add("MISSING_AMOUNT:" + code); return; }
                statement.addAccount(code, amount, row.accountId(), row.accountName(), row.accountDetail(), row.amount());
            } catch (IllegalArgumentException ex) { notes.add("INVALID_AMOUNT:" + code); }
        });
        List<String> uniqueNotes = notes.stream().distinct()
            .sorted(java.util.Comparator.comparing(note -> note.startsWith("UNMAPPED_ACCOUNT:"))).toList();
        var retainedNotes = new ArrayList<>(uniqueNotes.stream().limit(100)
            .map(note -> note.length() > 180 ? note.substring(0, 180) : note).toList());
        if (uniqueNotes.size() > 100) { retainedNotes.add("WARNINGS_TRUNCATED_SEE_SOURCE"); }
        try {
            statement.retainSource(mapper.writeValueAsString(Map.of("financials", response.raw(),
                "periods", periods.raw(), "shares", shares.raw(), "filing", filing)),
                String.join("\n", retainedNotes));
        } catch (Exception ex) { throw ProviderException.invalid(); }
        return statement;
    }

    private FinancialRow singleAccount(List<FinancialRow> rows) {
        if (rows.size() == 1) { return rows.getFirst(); }
        // 실제 DART는 같은 당기순이익을 IS와 CIS에 각각 표시합니다.
        // 동일 표준 ID·이름·상세·원금액이 일치하는 한 쌍만 IS를 선택하고 두 원본을 모두 보존합니다.
        // 같은 표 안의 중복이나 값이 다른 행은 계속 미매핑으로 남깁니다.
        if (rows.size() != 2) { return null; }
        var income = rows.stream().filter(r -> "IS".equals(r.statementType())).findFirst();
        var comprehensive = rows.stream().filter(r -> "CIS".equals(r.statementType())).findFirst();
        if (income.isEmpty() || comprehensive.isEmpty()) { return null; }
        var a = income.get();
        var b = comprehensive.get();
        if (!java.util.Objects.equals(a.accountId(), b.accountId())
            || !java.util.Objects.equals(a.accountName(), b.accountName())
            || !java.util.Objects.equals(a.accountDetail(), b.accountDetail())
            || !java.util.Objects.equals(a.amount(), b.amount())) { return null; }
        return a;
    }

    private StandardAccount mapped(FinancialRow row) {
        String id = row.accountId();
        if (id == null || !(id.startsWith("ifrs-full_") || id.startsWith("ifrs_") || id.equals("dart_OperatingIncomeLoss"))) { return null; }
        StandardAccount code = IDS.get(id.substring(id.indexOf('_') + 1));
        if (code == null) { return null; }
        String required = switch (code) {
            case TOTAL_ASSETS, TOTAL_LIABILITIES, TOTAL_EQUITY, PARENT_EQUITY -> "BS";
            case OPERATING_CASH_FLOW, INVESTING_CASH_FLOW, FINANCING_CASH_FLOW -> "CF";
            default -> "INCOME";
        };
        return (required.equals(row.statementType()) || required.equals("INCOME")
            && ("IS".equals(row.statementType()) || "CIS".equals(row.statementType()))) ? code : null;
    }

    private List<LocalDate> period(Company company, int year, StatementBasis basis, String receipt, List<PeriodRow> rows) {
        List<List<LocalDate>> ranges = new ArrayList<>();
        for (PeriodRow row : rows) {
            if (!receipt.equals(row.receiptNo()) || !basis.name().equals(row.basis())) { continue; }
            if (!String.valueOf(year).equals(row.fiscalYear()) || !"11011".equals(row.reportCode())
                || !company.getStockCode().equals(row.stockCode())) { throw ProviderException.invalid(); }
            if (!("IS".equals(row.statementType()) || "CIS".equals(row.statementType()))) { continue; }
            List<LocalDate> dates = dates(row.date());
            if (dates.size() == 2) { ranges.add(dates); }
        }
        if (ranges.isEmpty() || ranges.stream().distinct().count() != 1) {
            throw new ProviderException(org.springframework.http.HttpStatus.BAD_GATEWAY, "DART_PERIOD_UNCONFIRMED",
                "공시된 당기 시작일·종료일을 확인할 수 없어 재무 기간을 추정하지 않습니다.");
        }
        var range = ranges.getFirst();
        if (range.get(0).isAfter(range.get(1)) || range.get(1).getYear() != year) { throw ProviderException.invalid(); }
        return range;
    }

    private void shareDisclosure(FinancialStatement statement, Company company, List<ShareRow> all, List<String> notes) {
        List<ShareRow> rows = all.stream().filter(r -> statement.getReceiptNo().equals(r.receiptNo())
            && company.getCorpCode().equals(r.corpCode())).toList();
        var common = rows.stream().filter(r -> "보통주".equals(r.se())).toList();
        if (common.size() != 1) { notes.add("SHARES_COMMON_UNCONFIRMED"); return; }
        try {
            ShareRow c = common.getFirst();
            List<LocalDate> dates = dates(c.asOf());
            if (dates.size() != 1 || !statement.getPeriodEnd().equals(dates.getFirst())) {
                notes.add("SHARES_DATE_MISMATCH"); return;
            }
            Long issued = count(c.issued()), treasury = count(c.treasury()), outstanding = count(c.outstanding());
            Long preferred = null;
            var p = rows.stream().filter(r -> "우선주".equals(r.se()) || "종류주식".equals(r.se())).toList();
            if (p.size() == 1 && dates(p.getFirst().asOf()).equals(dates)) { preferred = count(p.getFirst().issued()); }
            var total = rows.stream().filter(r -> "합계".equals(r.se())).toList();
            if (preferred == null && issued != null && total.size() == 1 && dates(total.getFirst().asOf()).equals(dates)
                && issued.equals(count(total.getFirst().issued()))) { preferred = 0L; }
            statement.setShareDisclosure(issued, treasury, outstanding, preferred, dates.getFirst(), c.receiptNo());
            if (preferred == null) { notes.add("PREFERRED_SHARES_UNCONFIRMED"); }
        } catch (IllegalArgumentException | ProviderException ex) { notes.add("INVALID_SHARE_DISCLOSURE"); }
    }

    public static BigDecimal amount(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().equals("-")) { return null; }
        String text = raw.strip();
        if (text.startsWith("(") && text.endsWith(")")) { text = "-" + text.substring(1, text.length() - 1); }
        if (!text.matches("[+-]?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")) { throw new IllegalArgumentException(); }
        BigDecimal value = new BigDecimal(text.replace(",", ""));
        if (value.scale() > 6 || value.precision() - value.scale() > 24) { throw new IllegalArgumentException(); }
        return value;
    }

    private Long count(String raw) {
        BigDecimal value = amount(raw);
        if (value == null) { return null; }
        try {
            long count = value.longValueExact();
            if (count < 0) { throw new IllegalArgumentException(); }
            return count;
        } catch (ArithmeticException ex) { throw new IllegalArgumentException(); }
    }

    private List<LocalDate> dates(String raw) {
        if (raw == null) { return List.of(); }
        var matcher = DATE.matcher(raw);
        List<LocalDate> dates = new ArrayList<>();
        try {
            while (matcher.find()) { dates.add(LocalDate.of(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)))); }
        } catch (java.time.DateTimeException ex) { throw ProviderException.invalid(); }
        return dates;
    }
}
