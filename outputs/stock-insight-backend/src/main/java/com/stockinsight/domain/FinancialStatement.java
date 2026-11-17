package com.stockinsight.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.stockinsight.domain.DataTypes.*;

@Entity
@Table(name = "financial_statement", uniqueConstraints = @UniqueConstraint(
    name = "uk_statement_period_basis", columnNames = {"company_id", "fiscal_year", "report_code", "basis"}))
public class FinancialStatement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(name = "fiscal_year", nullable = false)
    private int fiscalYear;
    @Column(name = "report_code", nullable = false, length = 5)
    private String reportCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 8)
    private StatementBasis basis;
    @Column(name = "receipt_no", nullable = false, length = 64)
    private String receiptNo;
    @Column(name = "filed_at", nullable = false)
    private LocalDate filedAt;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private DataSource source;
    @Column(name = "collected_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant collectedAt;
    @Column(name = "issued_common_shares")
    private Long issuedCommonShares;
    @Column(name = "treasury_common_shares")
    private Long treasuryCommonShares;
    @Column(name = "outstanding_common_shares")
    private Long outstandingCommonShares;
    @Column(name = "preferred_shares")
    private Long preferredShares;
    @Column(name = "shares_as_of")
    private LocalDate sharesAsOf;
    @Column(name = "shares_receipt_no", length = 64)
    private String sharesReceiptNo;
    @Column(name = "raw_payload", columnDefinition = "longtext")
    private String rawPayload;
    @Column(name = "normalization_notes", columnDefinition = "text")
    private String normalizationNotes;
    @OneToMany(mappedBy = "statement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("code ASC")
    private List<FinancialAccount> accounts = new ArrayList<>();

    protected FinancialStatement() { }

    public FinancialStatement(Company company, int fiscalYear, StatementBasis basis, String receiptNo,
                              LocalDate filedAt, LocalDate periodStart, LocalDate periodEnd,
                              String currency, DataSource source, Instant collectedAt) {
        this.company = company;
        this.fiscalYear = fiscalYear;
        this.reportCode = "11011";
        this.basis = basis;
        this.receiptNo = receiptNo;
        this.filedAt = filedAt;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.currency = currency;
        this.source = source;
        this.collectedAt = collectedAt;
    }

    public void addAccount(StandardAccount code, BigDecimal amount, String originalId,
                           String originalName, String originalDetail, String originalAmount) {
        // 표준 계정과 원본 식별정보를 함께 저장해 변환 결과를 역추적합니다.
        accounts.add(new FinancialAccount(this, code, amount, originalId, originalName,
            originalDetail, originalAmount));
    }

    public void setShareDisclosure(Long issued, Long treasury, Long outstanding, Long preferred,
                                   LocalDate asOf, String receiptNo) {
        this.issuedCommonShares = issued;
        this.treasuryCommonShares = treasury;
        this.outstandingCommonShares = outstanding;
        this.preferredShares = preferred;
        this.sharesAsOf = asOf;
        this.sharesReceiptNo = receiptNo;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public int getFiscalYear() { return fiscalYear; }
    public String getReportCode() { return reportCode; }
    public StatementBasis getBasis() { return basis; }
    public String getReceiptNo() { return receiptNo; }
    public LocalDate getFiledAt() { return filedAt; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public String getCurrency() { return currency; }
    public DataSource getSource() { return source; }
    public Instant getCollectedAt() { return collectedAt; }
    public Long getIssuedCommonShares() { return issuedCommonShares; }
    public Long getTreasuryCommonShares() { return treasuryCommonShares; }
    public Long getOutstandingCommonShares() { return outstandingCommonShares; }
    public Long getPreferredShares() { return preferredShares; }
    public LocalDate getSharesAsOf() { return sharesAsOf; }
    public String getSharesReceiptNo() { return sharesReceiptNo; }
    public List<FinancialAccount> getAccounts() { return Collections.unmodifiableList(accounts); }
    public String getRawPayload() { return rawPayload; }
    public String getNormalizationNotes() { return normalizationNotes; }

    public void retainSource(String rawPayload, String normalizationNotes) {
        this.rawPayload = rawPayload;
        this.normalizationNotes = normalizationNotes;
    }

    public void replaceFrom(FinancialStatement incoming) {
        this.receiptNo = incoming.receiptNo;
        this.filedAt = incoming.filedAt;
        this.periodStart = incoming.periodStart;
        this.periodEnd = incoming.periodEnd;
        this.currency = incoming.currency;
        this.source = incoming.source;
        this.collectedAt = incoming.collectedAt;
        setShareDisclosure(incoming.issuedCommonShares, incoming.treasuryCommonShares,
            incoming.outstandingCommonShares, incoming.preferredShares, incoming.sharesAsOf, incoming.sharesReceiptNo);
        retainSource(incoming.rawPayload, incoming.normalizationNotes);
        // 기존 계정의 ID를 유지해 같은 표준 계정의 삭제·재삽입 충돌을 피합니다.
        accounts.removeIf(old -> incoming.accounts.stream().noneMatch(next -> next.getCode() == old.getCode()));
        for (FinancialAccount next : incoming.accounts) {
            var old = accounts.stream().filter(a -> a.getCode() == next.getCode()).findFirst();
            if (old.isPresent()) { old.get().updateFrom(next); }
            else { addAccount(next.getCode(), next.getAmount(), next.getOriginalAccountId(),
                next.getOriginalAccountName(), next.getOriginalAccountDetail(), next.getOriginalAmount()); }
        }
    }
}
