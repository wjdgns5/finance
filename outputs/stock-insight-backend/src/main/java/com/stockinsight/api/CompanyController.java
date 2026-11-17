package com.stockinsight.api;

import com.stockinsight.service.CompanyQueryService;
import com.stockinsight.service.RealDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static com.stockinsight.api.ApiModels.*;

@RestController
@RequestMapping("/api/v1/companies")
@Validated
@Tag(name = "기업·재무", description = "기업 검색·기본정보·연간 재무 조회")
public class CompanyController {
    private final CompanyQueryService service;
    private final ObjectProvider<RealDataService> real;

    public CompanyController(CompanyQueryService service, ObjectProvider<RealDataService> real) {
        this.service = service; this.real = real;
    }

    @GetMapping
    @Operation(operationId = "searchCompanies", summary = "기업 검색",
        description = "기업명 또는 종목코드 포함 검색. 결과 없음은 200과 빈 배열입니다. REAL은 필요한 기업 정보를 수집할 수 있습니다.")
    public Envelope<List<CompanySummary>> search(
        @Parameter(description = "앞뒤 공백을 제외한 기업명 또는 종목코드. %, _, !는 문자로 검색합니다.",
            schema = @Schema(minLength = 1, maxLength = 100))
        @RequestParam @NotBlank(message = "검색어를 입력해 주세요.")
        @Size(max = 100, message = "검색어는 100자 이하여야 합니다.") String query,
        @Parameter(description = "최대 검색 결과 개수")
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        real.ifAvailable(data -> data.prepareSearch(query, limit));
        return service.search(query, limit);
    }

    @GetMapping("/{stockCode}")
    @Operation(operationId = "getCompany", summary = "기업 기본정보·시세 조회",
        description = "업종·결산월·우선주 확인 상태와 확보한 보통주 종가·시가총액을 제공합니다. 시세가 없으면 null과 상태·사유로 표시합니다.")
    public Envelope<CompanyDetail> detail(
        @Parameter(description = "선행 0을 유지한 6자리 종목코드")
        @PathVariable @Pattern(regexp = "[0-9]{6}", message = "종목코드는 숫자 6자리여야 합니다.") String stockCode) {
        real.ifAvailable(data -> data.prepareDetail(stockCode));
        return service.detail(stockCode);
    }

    @GetMapping("/{stockCode}/financials")
    @Operation(operationId = "getFinancials", summary = "연간 재무 조회",
        description = "최대 5개년 연간 사업보고서(11011). 같은 연도는 CFS 우선·자료 없음 시 OFS입니다. 빠진 연도를 0이나 오래된 값으로 채우지 않습니다. REAL 최초 조회는 외부 수집을 포함할 수 있습니다.")
    public Envelope<Financials> financials(
        @Parameter(description = "선행 0을 유지한 6자리 종목코드")
        @PathVariable @Pattern(regexp = "[0-9]{6}", message = "종목코드는 숫자 6자리여야 합니다.") String stockCode,
        @Parameter(description = "최신 확보 연도부터 반환할 최대 연도 개수. 연속된 연도라는 보장은 없습니다.")
        @RequestParam(defaultValue = "5") @Min(1) @Max(5) int years) {
        real.ifAvailable(data -> data.prepareFinancials(stockCode));
        return service.financials(stockCode, years);
    }
}
