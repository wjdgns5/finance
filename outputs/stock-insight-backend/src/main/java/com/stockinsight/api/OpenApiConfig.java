package com.stockinsight.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.servers.Server;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private final String mode;

    public OpenApiConfig(@Value("${app.data-mode:MOCK}") String mode) { this.mode = mode; }

    @Bean
    OpenAPI stockInsightOpenApi() {
        return new OpenAPI().info(new Info().title("Stock Insight API").version("0.6.0")
            .description("""
                국내 상장사의 연간 재무·투자지표를 조회하는 로컬 API입니다.

                **현재 데이터 모드: %s**. MOCK은 가상 기업·수치이며 REAL 실패를 가상값으로 대체하지 않습니다.
                MOCK은 조회 API 5개, REAL은 갱신·원본 조회를 포함한 7개가 표시됩니다.

                - 성공·오류 모두 `data`, `meta`, `error` 구조를 사용합니다.
                - 금액·지표·주식수는 **JSON 문자열**, N/A는 `null`과 상태·사유로 전달합니다.
                - GET도 REAL 최초 조회 시 외부 수집·DB 저장이 발생할 수 있습니다.
                - `POST sync`는 재무·시세를 갱신합니다. HTTP 200이어도 각 `outcomes`를 확인하세요.
                - DART·FSC 키는 서버에서 설정합니다. 이 화면에 인증키를 입력할 필요가 없습니다.
                - 재무 기간·연결/별도·공시 접수번호·시세 기준일을 함께 확인하세요.
                """.formatted(mode)))
            // Relative to this backend: works with IDE and every launcher port.
            .servers(List.of(new Server().url("/").description("현재 Swagger 화면의 백엔드")));
    }

    @Bean
    OperationCustomizer stockInsightOperations() {
        return (operation, handler) -> {
            if (operation.getParameters() != null) {
                operation.getParameters().forEach(parameter -> {
                    if ("stockCode".equals(parameter.getName())) { parameter.setExample("REAL".equals(mode) ? "000270" : "990001"); }
                    if ("query".equals(parameter.getName())) { parameter.setExample("REAL".equals(mode) ? "기아" : "한빛"); }
                });
            }
            ApiResponses responses = operation.getResponses();
            if (responses == null) { responses = new ApiResponses(); operation.setResponses(responses); }
            responses.addApiResponse("400", responseRef("InvalidRequest"));
            responses.addApiResponse("403", responseRef("LocalRequestRequired"));
            responses.addApiResponse("405", responseRef("MethodNotAllowed"));
            responses.addApiResponse("500", responseRef("InternalError"));
            if (!"searchCompanies".equals(operation.getOperationId())) { responses.addApiResponse("404", responseRef("NotFound")); }
            if (List.of("getFinancials", "getMetrics", "getProfitability", "syncCompany").contains(operation.getOperationId())) {
                responses.addApiResponse("422", responseRef("UnsupportedIndustry"));
            }
            if (!"getFinancialSource".equals(operation.getOperationId())) {
                responses.addApiResponse("502", responseRef("ProviderFailure"));
                responses.addApiResponse("503", responseRef("ProviderUnavailable"));
                responses.addApiResponse("504", responseRef("ProviderTimeout"));
            }
            return operation;
        };
    }

    @Bean
    OpenApiCustomizer stockInsightSchemas() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) { components = new Components(); openApi.setComponents(components); }
            components.addSchemas("ErrorEnvelope", new ObjectSchema()
                .description("공통 오류 응답. data는 null이며 mode와 정제된 오류만 제공합니다.")
                .addProperty("data", new ObjectSchema().nullable(true).description("오류 응답에서는 항상 null"))
                .addProperty("meta", new Schema<>().$ref("#/components/schemas/Meta"))
                .addProperty("error", new Schema<>().$ref("#/components/schemas/ErrorDetail"))
                .required(List.of("data", "meta", "error")));
            addError(components, "InvalidRequest", "입력 누락·형식·범위 위반", "INVALID_REQUEST");
            addError(components, "LocalRequestRequired", "로컬 Host·Origin 요청만 허용합니다.", "LOCAL_REQUEST_REQUIRED");
            addError(components, "MethodNotAllowed", "지원하지 않는 HTTP 메서드", "METHOD_NOT_ALLOWED");
            addError(components, "NotFound", "기업 또는 저장된 원본이 없습니다. SOURCE_NOT_FOUND도 반환할 수 있습니다.", "COMPANY_NOT_FOUND");
            addError(components, "UnsupportedIndustry", "금융업 재무·지표를 지원하지 않습니다.", "UNSUPPORTED_INDUSTRY");
            addError(components, "InternalError", "내부 오류. 원인 예외·DB 주소는 공개하지 않습니다.", "INTERNAL_ERROR");
            addError(components, "ProviderFailure", "외부 응답·연결·정정 공시 불일치 등. 기존 자료가 있으면 보존합니다.", "PROVIDER_CONNECTION_ERROR");
            addError(components, "ProviderUnavailable", "서버 키 누락·인증·호출 제한 또는 외부 요청 중단", "DART_KEY_MISSING");
            addError(components, "ProviderTimeout", "외부 요청 시간이 초과되었습니다.", "PROVIDER_TIMEOUT");
            // A stored source document is arbitrary JSON, not Jackson JsonNode internals.
            components.getSchemas().forEach((name, schema) -> {
                Schema<?> typedSchema = schema;
                if (name.startsWith("Envelope") && name.endsWith("JsonNode") && typedSchema.getProperties() != null) {
                    typedSchema.addProperty("data", new ObjectSchema().additionalProperties(true)
                        .description("저장된 재무·기간·주식수·공시 식별정보 원본 JSON"));
                }
            });
        };
    }

    private ApiResponse responseRef(String name) { return new ApiResponse().$ref("#/components/responses/" + name); }

    private void addError(Components components, String name, String description, String code) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("mode", mode); meta.put("sources", List.of()); meta.put("collectedAt", null);
        meta.put("financialPeriods", List.of()); meta.put("market", null); meta.put("stale", false);
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("data", null); example.put("meta", meta);
        example.put("error", Map.of("code", code, "message", description, "fields", List.of()));
        components.addResponses(name, new ApiResponse().description(description).content(new Content()
            .addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorEnvelope"))
                .example(example))));
    }
}
