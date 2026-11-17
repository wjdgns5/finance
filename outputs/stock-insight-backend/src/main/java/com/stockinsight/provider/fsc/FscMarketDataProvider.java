package com.stockinsight.provider.fsc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockinsight.domain.Company;
import com.stockinsight.domain.MarketPrice;
import com.stockinsight.provider.ExternalHttp;
import com.stockinsight.provider.MarketDataProvider;
import com.stockinsight.provider.ProviderException;
import com.stockinsight.provider.dart.DartNormalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import static com.stockinsight.domain.DataTypes.*;

@Component
@Profile("real")
@ConditionalOnProperty(name = "app.data-mode", havingValue = "REAL")
public class FscMarketDataProvider implements MarketDataProvider {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRow(String basDt, String srtnCd, String mrktCtg, String clpr, String mrktTotAmt) { }
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String base, path, key;
    private final ExternalHttp http;

    @Autowired
    public FscMarketDataProvider(ObjectMapper mapper, Clock clock, @Value("${app.fsc.base-url}") String base,
        @Value("${app.fsc.price-path}") String path, @Value("${app.fsc.api-key:}") String key) {
        this(mapper, clock, base, path, key, new ExternalHttp());
    }

    public FscMarketDataProvider(ObjectMapper mapper, Clock clock, String base, String path, String key, ExternalHttp http) {
        this.mapper = mapper; this.clock = clock; this.base = base; this.path = path; this.key = key; this.http = http;
    }

    @Override public Optional<MarketPrice> latestPrice(Company company) {
        if (company.getDataMode() != DataMode.REAL) { throw ProviderException.invalid(); }
        if (key == null || key.isBlank()) {
            throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "FSC_KEY_MISSING", "FSC_API_KEY를 설정해야 종가를 조회할 수 있습니다.");
        }
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul")));
        LocalDate from = today.minusDays(30);
        List<PriceRow> prices = new ArrayList<>();
        int pages = 1;
        for (int page = 1; page <= pages; page++) {
            byte[] bytes = http.get(base, path, Map.of("serviceKey", key, "resultType", "json",
                "numOfRows", "100", "pageNo", String.valueOf(page), "likeSrtnCd", company.getStockCode(),
                "beginBasDt", from.format(DateTimeFormatter.BASIC_ISO_DATE),
                "endBasDt", today.plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)), 2_000_000);
            try {
                JsonNode root = mapper.readTree(bytes);
                JsonNode response = root.has("response") ? root.path("response") : root;
                String code = response.path("header").path("resultCode").asText();
                if (!List.of("00", "000", "0").contains(code)) {
                    throw new ProviderException(HttpStatus.BAD_GATEWAY, "FSC_SERVICE_ERROR", "시세 제공기관의 인증·활용 승인·호출 한도를 확인해 주세요.");
                }
                JsonNode body = response.path("body");
                int count = body.path("totalCount").asInt(-1);
                if (count < 0 || count > 10_000) { throw ProviderException.invalid(); }
                pages = Math.max(1, (count + 99) / 100);
                JsonNode items = body.path("items").path("item");
                if (count > 0 && !items.isArray() && !items.isObject()) { throw ProviderException.invalid(); }
                if (items.isArray()) { for (JsonNode row : items) { prices.add(mapper.treeToValue(row, PriceRow.class)); } }
                else if (items.isObject()) { prices.add(mapper.treeToValue(items, PriceRow.class)); }
            } catch (ProviderException ex) { throw ex;
            } catch (Exception ex) { throw ProviderException.invalid(); }
        }
        List<MarketPrice> matched = new ArrayList<>();
        for (PriceRow row : prices) {
            if (!company.getStockCode().equals(row.srtnCd())) { continue; }
            try {
                LocalDate date = LocalDate.parse(row.basDt(), DateTimeFormatter.BASIC_ISO_DATE);
                if (date.isAfter(today) || date.isBefore(from)) { continue; }
                if (!company.getMarket().name().equals(row.mrktCtg())) { throw ProviderException.invalid(); }
                var close = DartNormalizer.amount(row.clpr());
                var cap = DartNormalizer.amount(row.mrktTotAmt());
                if (close == null || close.signum() <= 0 || cap != null && (cap.signum() < 0 || cap.stripTrailingZeros().scale() > 0)) {
                    throw ProviderException.invalid();
                }
                matched.add(new MarketPrice(company, close, date, DataSource.FSC, cap,
                    cap == null ? null : date, cap == null ? null : DataSource.FSC, "KRW", clock.instant()));
            } catch (ProviderException ex) { throw ex;
            } catch (Exception ex) { throw ProviderException.invalid(); }
        }
        return matched.stream().max(Comparator.comparing(MarketPrice::getClosePriceAsOf));
    }
}
