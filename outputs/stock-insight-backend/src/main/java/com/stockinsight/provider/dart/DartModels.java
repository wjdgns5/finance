package com.stockinsight.provider.dart;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

// 외부 응답 DTO입니다. Entity·프론트엔드 응답 DTO와 구분합니다.
public final class DartModels {
    private DartModels() { }
    public record CorpEntry(String corpCode, String corpName, String stockCode) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompanyProfile(String status, @JsonProperty("corp_code") String corpCode,
                          @JsonProperty("corp_name") String corpName,
                          @JsonProperty("stock_code") String stockCode,
                          @JsonProperty("corp_cls") String corpClass,
                          @JsonProperty("induty_code") String industryCode,
                          @JsonProperty("acc_mt") String fiscalMonth) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinancialRow(@JsonProperty("rcept_no") String receiptNo,
        @JsonProperty("reprt_code") String reportCode, @JsonProperty("bsns_year") String fiscalYear,
        @JsonProperty("corp_code") String corpCode, @JsonProperty("sj_div") String statementType,
        @JsonProperty("account_id") String accountId, @JsonProperty("account_nm") String accountName,
        @JsonProperty("account_detail") String accountDetail, @JsonProperty("thstrm_amount") String amount,
        @JsonProperty("frmtrm_amount") String previousAmount, @JsonProperty("bfefrmtrm_amount") String beforePreviousAmount,
        String currency) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShareRow(@JsonProperty("rcept_no") String receiptNo,
        @JsonProperty("corp_code") String corpCode, String se,
        @JsonProperty("istc_totqy") String issued, @JsonProperty("tesstk_co") String treasury,
        @JsonProperty("distb_stock_co") String outstanding, @JsonProperty("stlm_dt") String asOf) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Filing(@JsonProperty("rcept_no") String receiptNo,
        @JsonProperty("rcept_dt") String filedAt, @JsonProperty("report_nm") String name,
        @JsonProperty("corp_code") String corpCode, String rm) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeriodRow(@JsonProperty("rcept_no") String receiptNo,
        @JsonProperty("bsns_year") String fiscalYear, @JsonProperty("reprt_code") String reportCode,
        @JsonProperty("fs_div") String basis, @JsonProperty("thstrm_dt") String date,
        @JsonProperty("sj_div") String statementType, @JsonProperty("stock_code") String stockCode) { }

    // 원본 JSON도 DTO에 함께 보관해 정규화가 모르는 필드를 버리지 않고 DB에 남깁니다.
    public record FinancialResponse(List<FinancialRow> rows, JsonNode raw) { }
    public record SharesResponse(List<ShareRow> rows, JsonNode raw) { }
    public record PeriodResponse(List<PeriodRow> rows, JsonNode raw) { }
}
