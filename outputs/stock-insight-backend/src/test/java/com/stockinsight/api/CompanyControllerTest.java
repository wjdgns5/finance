package com.stockinsight.api;

import com.stockinsight.provider.FinancialDataProvider;
import com.stockinsight.service.CompanyQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static com.stockinsight.domain.DataTypes.DataMode.MOCK;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyController.class)
class CompanyControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyQueryService service;
    @MockitoBean FinancialDataProvider provider;

    @BeforeEach void setup() { when(provider.mode()).thenReturn(MOCK); }

    @Test
    void blankQueryIs400WithEnvelope() throws Exception {
        mvc.perform(get("/api/v1/companies").param("query", " "))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.meta.mode").value("MOCK"));
        verifyNoInteractions(service);
    }

    @Test
    void missingQueryIs400() throws Exception {
        mvc.perform(get("/api/v1/companies")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field").value("query"));
    }

    @Test
    void invalidStockCodeIs400() throws Exception {
        mvc.perform(get("/api/v1/companies/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void excessiveYearRangeIs400() throws Exception {
        mvc.perform(get("/api/v1/companies/990001/financials").param("years", "6"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void nonNumericYearsIs400() throws Exception {
        mvc.perform(get("/api/v1/companies/990001/financials").param("years", "five"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
