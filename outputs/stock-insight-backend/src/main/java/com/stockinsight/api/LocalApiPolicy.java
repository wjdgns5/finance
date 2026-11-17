package com.stockinsight.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Set;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LocalApiPolicy implements WebMvcConfigurer {
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                // CORS의 응답 읽기 제한과 별개로, 외부 사이트의 단순 POST가 수집을 실행하지 못하게 합니다.
                if (!LOCAL_HOSTS.contains(request.getServerName())
                    || "cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))
                    || !localOrigin(request.getHeader("Origin"))) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "LOCAL_REQUEST_REQUIRED", "로컬 화면 또는 로컬 실행 도구에서 요청해 주세요.");
                }
                return true;
            }
        }).addPathPatterns("/api/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
            "/swagger-ui.html", "/swagger-ui/**");
    }

    private static boolean localOrigin(String origin) {
        if (origin == null) return true; // PowerShell 등 로컬 CLI 요청에는 Origin이 없습니다.
        try {
            var uri = URI.create(origin);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                && uri.getHost() != null && LOCAL_HOSTS.contains(uri.getHost())
                && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
        } catch (IllegalArgumentException ex) { return false; }
    }
}
