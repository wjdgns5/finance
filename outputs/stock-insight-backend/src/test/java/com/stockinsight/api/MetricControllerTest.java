package com.stockinsight.api;

import com.stockinsight.service.MetricQueryService;
import com.stockinsight.provider.FinancialDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricController.class)
class MetricControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MetricQueryService service;
    @MockitoBean FinancialDataProvider provider;

    @Test
    void validatesStockCodeBeforeQuerying() throws Exception {
        mvc.perform(get("/api/v1/companies/abc/metrics")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.data").isEmpty()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void preservesNotFoundAndUnsupportedStatus() throws Exception {
        when(service.metrics("000000")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "없음"));
        when(service.metrics("990005")).thenThrow(new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_INDUSTRY", "금융업 미지원"));
        mvc.perform(get("/api/v1/companies/000000/metrics")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("COMPANY_NOT_FOUND"));
        mvc.perform(get("/api/v1/companies/990005/metrics")).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_INDUSTRY"));
    }

    @Test
    void annualProfitabilityValidatesYearRange() throws Exception {
        mvc.perform(get("/api/v1/companies/990001/metrics/profitability?years=0")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
