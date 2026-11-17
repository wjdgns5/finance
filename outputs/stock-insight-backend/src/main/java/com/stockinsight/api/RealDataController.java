package com.stockinsight.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockinsight.repository.CompanyRepository;
import com.stockinsight.repository.FinancialStatementRepository;
import com.stockinsight.service.CompanyQueryService;
import com.stockinsight.service.RealDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.domain.DataTypes.*;

@RestController
@Profile("real")
@RequestMapping("/api/v1/companies")
@Validated
@Tag(name = "실제자료 관리", description = "REAL 전용 갱신·저장 원본 조회. MOCK에서는 표시되지 않습니다.")
public class RealDataController {
    private final RealDataService collector;
    private final CompanyQueryService query;
    private final CompanyRepository companies;
    private final FinancialStatementRepository statements;
    private final ObjectMapper mapper;

    public RealDataController(RealDataService collector, CompanyQueryService query, CompanyRepository companies,
                              FinancialStatementRepository statements, ObjectMapper mapper) {
        this.collector = collector; this.query = query; this.companies = companies; this.statements = statements; this.mapper = mapper;
    }

    @PostMapping("/{stockCode}/sync")
    @Operation(operationId = "syncCompany", summary = "실제 기업·재무·시세 갱신",
        description = "외부 API를 다시 호출하고 DB를 갱신합니다. 실패한 수집은 기존 정상 스냅샷을 보존합니다. HTTP 200이어도 FINANCIALS·MARKET 각각의 outcomes.status와 errorCode를 확인하세요. 여러 외부 요청으로 시간이 걸릴 수 있습니다.")
    public Envelope<CollectionResult> sync(@Parameter(description = "갱신할 실제 기업의 6자리 종목코드")
        @PathVariable @Pattern(regexp = "[0-9]{6}") String stockCode) {
        var result = collector.refresh(stockCode);
        return Envelope.success(result, query.detail(stockCode).meta());
    }

    @GetMapping("/{stockCode}/financials/{year}/source")
    @Operation(operationId = "getFinancialSource", summary = "저장된 공시 원본 조회",
        description = "이미 저장한 연도·연결/별도 기준의 재무·기간·주식수·공시 식별정보 JSON을 조회합니다. 새 수집은 시작하지 않으며 키·요청 헤더·전체 요청 URL을 포함하지 않습니다. 해당 원본이 없으면 SOURCE_NOT_FOUND(404)입니다.")
    public Envelope<JsonNode> source(@Parameter(description = "저장된 실제 기업의 6자리 종목코드")
        @PathVariable @Pattern(regexp = "[0-9]{6}") String stockCode,
        @Parameter(description = "사업연도", example = "2025") @PathVariable @Min(2015) @Max(2100) int year,
        @Parameter(description = "저장된 재무제표 기준. CFS 연결 / OFS 별도")
        @RequestParam(defaultValue = "CFS") StatementBasis basis) {
        var company = companies.findByDataModeAndStockCode(DataMode.REAL, stockCode)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "저장된 실데이터 기업이 없습니다."));
        var statement = statements.findByCompanyAndReportCodeOrderByFiscalYearDesc(company, "11011").stream()
            .filter(s -> s.getFiscalYear() == year && s.getBasis() == basis && s.getRawPayload() != null).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SOURCE_NOT_FOUND", "저장된 해당 사업연도·기준의 원본 응답이 없습니다."));
        try {
            return Envelope.success(mapper.readTree(statement.getRawPayload()), query.financials(stockCode, 5).meta());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SOURCE_READ_ERROR", "저장된 원본 응답을 읽을 수 없습니다.");
        }
    }
}
