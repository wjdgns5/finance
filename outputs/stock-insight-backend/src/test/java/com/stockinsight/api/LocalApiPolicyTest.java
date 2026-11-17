package com.stockinsight.api;

import com.stockinsight.provider.FinancialDataProvider;
import com.stockinsight.repository.CompanyRepository;
import com.stockinsight.repository.FinancialStatementRepository;
import com.stockinsight.service.CompanyQueryService;
import com.stockinsight.service.RealDataService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static com.stockinsight.api.ApiModels.*;
import static com.stockinsight.domain.DataTypes.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("real")
@WebMvcTest({CompanyController.class, RealDataController.class})
class LocalApiPolicyTest {
    @Autowired MockMvc mvc;
    @MockitoBean CompanyQueryService query;
    @MockitoBean RealDataService collector;
    @MockitoBean FinancialDataProvider provider;
    @MockitoBean CompanyRepository companies;
    @MockitoBean FinancialStatementRepository statements;

    @BeforeEach void setup() {
        when(provider.mode()).thenReturn(DataMode.REAL);
        var meta = new Meta(DataMode.REAL, List.of(), null, List.of(), null, false);
        when(collector.refresh("123456")).thenReturn(new CollectionResult("123456", List.of()));
        when(query.detail("123456")).thenReturn(Envelope.success(null, meta));
    }

    @Test void foreignWebsiteCannotTriggerCollectionWithSimplePost() throws Exception {
        mvc.perform(post("/api/v1/companies/123456/sync").header("Origin", "https://foreign.example"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("LOCAL_REQUEST_REQUIRED"));
        verifyNoInteractions(collector, query);
    }

    @Test void foreignHostAndOpaqueOriginAreRejectedBeforeReadingData() throws Exception {
        mvc.perform(get("http://foreign.example/api/v1/companies/123456"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/companies/123456").header("Origin", "null"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/companies/123456").header("Sec-Fetch-Site", "cross-site"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(collector, query);
    }

    @Test void localCliAndLocalProxyRequestsRemainAllowed() throws Exception {
        mvc.perform(post("/api/v1/companies/123456/sync"))
            .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/v1/companies/123456").header("Origin", "http://127.0.0.1:5195"))
            .andExpect(status().isOk());
        verify(collector).refresh("123456");
    }

    @Test void documentationAndSwaggerAssetsKeepTheLocalRequestBoundary() throws Exception {
        for (String path : List.of("/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
            "/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js")) {
            mvc.perform(get(path).header("Origin", "https://foreign.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("LOCAL_REQUEST_REQUIRED"));
        }
        verifyNoInteractions(collector, query);
    }

    @Test void internalExceptionNeverRevealsConnectionOrCredentialText() throws Exception {
        when(query.detail("123456")).thenThrow(new IllegalStateException("jdbc:mysql://private/secret?password=never-print"));
        mvc.perform(get("/api/v1/companies/123456"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("never-print"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc:"))));
    }
}
