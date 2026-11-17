package com.stockinsight.provider;

import org.springframework.http.HttpStatus;
import com.stockinsight.api.ApiException;

public class ProviderException extends ApiException {
    public ProviderException(HttpStatus status, String code, String message) { super(status, code, message); }
    public static ProviderException invalid() {
        return new ProviderException(HttpStatus.BAD_GATEWAY, "PROVIDER_INVALID_RESPONSE", "외부 데이터의 형식이나 기준을 확인할 수 없습니다.");
    }
}
