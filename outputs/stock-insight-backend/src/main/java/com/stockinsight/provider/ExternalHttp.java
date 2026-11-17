package com.stockinsight.provider;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;

public class ExternalHttp {
    private final HttpClient http;
    private final Duration timeout;

    public ExternalHttp() { this(Duration.ofSeconds(5), Duration.ofSeconds(15)); }
    public ExternalHttp(Duration connectTimeout, Duration requestTimeout) {
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER).build();
        this.timeout = requestTimeout;
    }

    public byte[] get(String base, String path, Map<String, String> parameters, int maxBytes) {
        String query = parameters.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
            .collect(java.util.stream.Collectors.joining("&"));
        // 요청 URL에 키가 있으므로 URL·외부 오류 본문·원인 예외를 로그나 공개 응답에 전달하지 않습니다.
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            var request = HttpRequest.newBuilder(URI.create(base + path + "?" + query))
                .timeout(timeout).header("Accept", "application/json, application/xml, */*").GET().build();
            pending = http.sendAsync(request, ignored -> new LimitedBody(maxBytes));
            // 헤더뿐 아니라 본문을 다 받을 때까지 전체 요청 시간을 제한합니다.
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new ProviderException(HttpStatus.BAD_GATEWAY, "PROVIDER_HTTP_ERROR", "외부 데이터 서버가 정상 응답하지 않았습니다.");
            }
            return response.body();
        } catch (TimeoutException ex) {
            throw new ProviderException(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT", "외부 데이터 서버 응답 시간이 초과되었습니다.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_INTERRUPTED", "외부 데이터 요청이 중단되었습니다.");
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof HttpTimeoutException) {
                throw new ProviderException(HttpStatus.GATEWAY_TIMEOUT, "PROVIDER_TIMEOUT", "외부 데이터 서버 응답 시간이 초과되었습니다.");
            }
            if (ex.getCause() instanceof TooLarge) { throw ProviderException.invalid(); }
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "PROVIDER_CONNECTION_ERROR", "외부 데이터 서버에 연결할 수 없습니다.");
        } catch (IllegalArgumentException ex) {
            throw new ProviderException(HttpStatus.BAD_GATEWAY, "PROVIDER_CONNECTION_ERROR", "외부 데이터 서버에 연결할 수 없습니다.");
        } finally {
            if (pending != null && !pending.isDone()) { pending.cancel(true); }
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    private static final class TooLarge extends IOException { }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final int maxBytes;
        private long received;
        private Flow.Subscription upstream;
        private boolean failed;
        private LimitedBody(int maxBytes) { this.maxBytes = maxBytes; }
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            upstream = subscription;
            delegate.onSubscribe(subscription);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) { return; }
            received += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (received > maxBytes) {
                failed = true;
                upstream.cancel();
                delegate.onError(new TooLarge());
            } else { delegate.onNext(buffers); }
        }
        @Override public void onError(Throwable throwable) { if (!failed) { delegate.onError(throwable); } }
        @Override public void onComplete() { if (!failed) { delegate.onComplete(); } }
    }
}
