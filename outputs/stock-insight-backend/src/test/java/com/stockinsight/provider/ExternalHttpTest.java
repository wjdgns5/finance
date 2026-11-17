package com.stockinsight.provider;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExternalHttpTest {
    @Test
    void limitsBodySizeAndTotalTimeIncludingSlowBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            try (var out = exchange.getResponseBody()) { out.write(new byte[1000]); }
        });
        server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            try (var out = exchange.getResponseBody()) {
                out.write(new byte[1]); out.flush();
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                out.write(new byte[999]);
            } catch (java.io.IOException ignored) { }
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var http = new ExternalHttp(Duration.ofSeconds(1), Duration.ofMillis(500));
            assertThatThrownBy(() -> http.get(base, "/large", Map.of("secret", "never-log"), 100))
                .isInstanceOfSatisfying(ProviderException.class, ex -> assertThat(ex.getCode()).isEqualTo("PROVIDER_INVALID_RESPONSE"));
            assertThatThrownBy(() -> http.get(base, "/slow", Map.of("secret", "never-log"), 1000))
                .isInstanceOfSatisfying(ProviderException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("PROVIDER_TIMEOUT");
                    assertThat(ex.getMessage()).doesNotContain("never-log", "http");
                });
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
