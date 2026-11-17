package com.stockinsight.api;

import com.stockinsight.service.MetricQueryService;
import com.stockinsight.service.RealDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.api.MetricModels.*;

@RestController
@RequestMapping("/api/v1/companies")
@Validated
@Tag(name = "투자지표", description = "BigDecimal 기반 최신 지표와 연도별 이익률")
public class MetricController {
    private final MetricQueryService service;
    private final ObjectProvider<RealDataService> real;

    public MetricController(MetricQueryService service, ObjectProvider<RealDataService> real) {
        this.service = service;
        this.real = real;
    }

    @GetMapping("/{stockCode}/metrics")
    @Operation(operationId = "getMetrics", summary = "최신 연간 투자지표 10개",
        description = "매출 증가율·영업/순이익률·ROE·ROA·부채비율·EPS·BPS·PER·PBR. 값은 문자열이며 N/A는 null과 status/reasonCode/reason입니다. ROE·ROA는 기말 기준이며 재무 연도와 시세 기준일은 다를 수 있습니다.")
    public Envelope<Metrics> metrics(@Parameter(description = "선행 0을 유지한 6자리 종목코드")
        @PathVariable @Pattern(regexp = "\\d{6}", message = "종목코드는 숫자 6자리여야 합니다.") String stockCode) {
        real.ifAvailable(data -> data.prepareFinancials(stockCode));
        return service.metrics(stockCode);
    }

    @GetMapping("/{stockCode}/metrics/profitability")
    @Operation(operationId = "getProfitability", summary = "연도별 영업·순이익률",
        description = "확보한 각 연도의 이익률을 동일 계산 엔진으로 제공합니다. financialPeriods의 연도·CFS/OFS·접수번호·기간을 재무 응답과 맞춰 사용하세요. 과거 PER·PBR은 생성하지 않습니다.")
    public Envelope<ProfitabilityHistory> profitability(
        @Parameter(description = "선행 0을 유지한 6자리 종목코드")
        @PathVariable @Pattern(regexp = "[0-9]{6}") String stockCode,
        @Parameter(description = "최신 확보 연도부터 반환할 최대 연도 개수")
        @RequestParam(defaultValue = "5") @Min(1) @Max(5) int years) {
        real.ifAvailable(data -> data.prepareFinancials(stockCode));
        return service.profitability(stockCode, years);
    }
}
