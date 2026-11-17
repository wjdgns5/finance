package com.stockinsight.provider;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// 실제 공급원을 호출하지 않는 계약 테스트 서버입니다.
public class StubProviders implements AutoCloseable {
    public static final String NO_DATA = "{\"status\":\"013\",\"message\":\"조회된 데이터가 없습니다.\"}";
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    public final Map<String, String> overrides = new ConcurrentHashMap<>();
    public volatile boolean rateLimited;
    public volatile boolean noPrice;
    public volatile boolean onlyOfs;

    public StubProviders() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                calls.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
                Map<String, String> query = new java.util.HashMap<>();
                for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                    String[] parts = pair.split("=", 2);
                    query.put(parts[0], URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
                }
                byte[] body;
                if (path.endsWith("/corpCode.xml")) { body = directoryZip(); }
                else {
                    String result = overrides.get(path);
                    if (result == null) {
                        if (path.endsWith("/company.json")) { result = fixture("dart-profile.json"); }
                        else if (path.endsWith("/list.json")) { result = fixture("dart-filings.json"); }
                        else if (path.endsWith("/fnlttSinglAcntAll.json")) {
                            result = rateLimited ? "{\"status\":\"020\",\"message\":\"sensitive upstream text\"}" :
                                !"2025".equals(query.get("bsns_year")) || onlyOfs && "CFS".equals(query.get("fs_div"))
                                ? NO_DATA : fixture("dart-financials.json");
                        } else if (path.endsWith("/fnlttSinglAcnt.json")) {
                            result = fixture("dart-periods.json");
                            if (onlyOfs) { result = result.replace("CFS", "OFS"); }
                        } else if (path.endsWith("/stockTotqySttus.json")) { result = fixture("dart-shares.json"); }
                        else if (path.endsWith("/getStockPriceInfo_V2")) {
                            result = noPrice ? "{\"response\":{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0}}}" : fixture("fsc-prices.json");
                        } else { result = "{}"; }
                    }
                    body = result.getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) { out.write(body); }
            });
            server.start();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    public String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public int calls(String path) { return calls.getOrDefault(path, new AtomicInteger()).get(); }
    public void reset() { overrides.clear(); rateLimited = false; noPrice = false; onlyOfs = false; calls.clear(); }

    public static String fixture(String name) {
        try (var stream = StubProviders.class.getResourceAsStream("/provider-fixtures/" + name)) {
            if (stream == null) { throw new IllegalArgumentException(name); }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }

    private byte[] directoryZip() throws java.io.IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(("<result><list><corp_code>00123456</corp_code><corp_name>계약검증제조</corp_name>"
                + "<stock_code>123456</stock_code></list><list><corp_code>00999999</corp_code>"
                + "<corp_name>비상장</corp_name><stock_code> </stock_code></list></result>").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Override public void close() { server.stop(0); executor.shutdownNow(); }
}
