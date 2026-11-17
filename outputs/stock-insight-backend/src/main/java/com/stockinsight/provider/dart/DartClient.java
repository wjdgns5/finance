package com.stockinsight.provider.dart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockinsight.provider.ExternalHttp;
import com.stockinsight.provider.ProviderException;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import static com.stockinsight.provider.dart.DartModels.*;

@Component
@Profile("real")
public class DartClient {
    private final String base;
    private final String key;
    private final ObjectMapper mapper;
    private final ExternalHttp http;
    private final Clock clock;
    private List<CorpEntry> directory = List.of();
    private Instant directoryFetchedAt = Instant.MIN;

    @org.springframework.beans.factory.annotation.Autowired
    public DartClient(ObjectMapper mapper, Clock clock, @Value("${app.dart.base-url}") String base,
                      @Value("${app.dart.api-key:}") String key) {
        this(mapper, clock, base, key, new ExternalHttp());
    }

    public DartClient(ObjectMapper mapper, Clock clock, String base, String key, ExternalHttp http) {
        this.mapper = mapper; this.clock = clock; this.base = base; this.key = key; this.http = http;
    }

    public synchronized List<CorpEntry> directory() {
        requireKey();
        if (!directory.isEmpty() && Duration.between(directoryFetchedAt, clock.instant()).compareTo(Duration.ofDays(1)) < 0) {
            return directory;
        }
        byte[] bytes = http.get(base, "/corpCode.xml", Map.of("crtfc_key", key), 32_000_000);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            if (zip.getNextEntry() == null) { throw ProviderException.invalid(); }
            byte[] xml = zip.readNBytes(128_000_001);
            if (xml.length > 128_000_000) { throw ProviderException.invalid(); }
            directory = parseDirectory(xml);
            directoryFetchedAt = clock.instant();
            return directory;
        } catch (ProviderException ex) { throw ex;
        } catch (Exception ex) { throw ProviderException.invalid(); }
    }

    public static List<CorpEntry> parseDirectory(byte[] xml) {
        try {
            var factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml), "UTF-8");
            List<CorpEntry> entries = new ArrayList<>();
            String corp = null, name = null, stock = null;
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD) { throw ProviderException.invalid(); }
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        switch (reader.getLocalName()) {
                            case "list" -> { corp = null; name = null; stock = null; }
                            case "corp_code" -> corp = reader.getElementText().strip();
                            case "corp_name" -> name = reader.getElementText().strip();
                            case "stock_code" -> stock = reader.getElementText().strip();
                            default -> { }
                        }
                    } else if (event == XMLStreamConstants.END_ELEMENT && "list".equals(reader.getLocalName())) {
                        if (stock != null && stock.matches("[0-9]{6}")) {
                            if (corp == null || !corp.matches("[0-9]{8}") || name == null || name.isBlank()) {
                                throw ProviderException.invalid();
                            }
                            entries.add(new CorpEntry(corp, name, stock));
                        }
                    }
                }
            } finally { reader.close(); }
            if (entries.isEmpty() || entries.stream().map(CorpEntry::stockCode).distinct().count() != entries.size()) {
                throw ProviderException.invalid();
            }
            return List.copyOf(entries);
        } catch (ProviderException ex) { throw ex;
        } catch (Exception ex) { throw ProviderException.invalid(); }
    }

    public CompanyProfile profile(String corpCode) {
        return convert(json("/company.json", Map.of("corp_code", corpCode)), CompanyProfile.class);
    }

    public FinancialResponse financials(String corpCode, int year, String basis) {
        JsonNode raw = json("/fnlttSinglAcntAll.json", Map.of("corp_code", corpCode,
            "bsns_year", String.valueOf(year), "reprt_code", "11011", "fs_div", basis));
        return new FinancialResponse(rows(raw, FinancialRow.class), raw);
    }

    public SharesResponse shares(String corpCode, int year) {
        JsonNode raw = json("/stockTotqySttus.json", Map.of("corp_code", corpCode,
            "bsns_year", String.valueOf(year), "reprt_code", "11011"));
        return new SharesResponse(rows(raw, ShareRow.class), raw);
    }

    public PeriodResponse periods(String corpCode, int year) {
        JsonNode raw = json("/fnlttSinglAcnt.json", Map.of("corp_code", corpCode,
            "bsns_year", String.valueOf(year), "reprt_code", "11011"));
        return new PeriodResponse(rows(raw, PeriodRow.class), raw);
    }

    public List<Filing> annualFilings(String corpCode, int earliestYear) {
        List<Filing> filings = new ArrayList<>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            JsonNode raw = json("/list.json", Map.of("corp_code", corpCode,
                "bgn_de", earliestYear + "0101", "end_de", LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul"))).format(DateTimeFormatter.BASIC_ISO_DATE),
                "pblntf_detail_ty", "A001", "last_reprt_at", "N", "page_count", "100", "page_no", String.valueOf(page)));
            filings.addAll(rows(raw, Filing.class));
            totalPages = raw.path("total_page").asInt(1);
            if (totalPages < 1 || totalPages > 100) { throw ProviderException.invalid(); }
        }
        return List.copyOf(filings);
    }

    private JsonNode json(String path, Map<String, String> parameters) {
        requireKey();
        var query = new LinkedHashMap<>(parameters);
        query.put("crtfc_key", key);
        byte[] bytes = http.get(base, path, query, 12_000_000);
        try {
            JsonNode raw = mapper.readTree(bytes);
            String status = raw.path("status").asText();
            switch (status) {
                case "000", "013" -> { return raw; }
                case "010", "011", "012", "901" -> throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DART_AUTH_ERROR", "DART 인증키 또는 접근 IP 설정을 확인해 주세요.");
                case "020", "021" -> throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "DART_RATE_LIMIT", "DART 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
                default -> throw new ProviderException(HttpStatus.BAD_GATEWAY,
                    "DART_SERVICE_ERROR", "DART에서 자료를 정상 제공하지 못했습니다.");
            }
        } catch (ProviderException ex) { throw ex;
        } catch (Exception ex) { throw ProviderException.invalid(); }
    }

    private <T> List<T> rows(JsonNode raw, Class<T> type) {
        if ("013".equals(raw.path("status").asText())) { return List.of(); }
        if (!raw.path("list").isArray()) { throw ProviderException.invalid(); }
        List<T> values = new ArrayList<>();
        for (JsonNode row : raw.path("list")) { values.add(convert(row, type)); }
        return List.copyOf(values);
    }

    private <T> T convert(JsonNode raw, Class<T> type) {
        try { return mapper.treeToValue(raw, type);
        } catch (Exception ex) { throw ProviderException.invalid(); }
    }

    private void requireKey() {
        if (key == null || key.isBlank()) {
            throw new ProviderException(HttpStatus.SERVICE_UNAVAILABLE, "DART_KEY_MISSING", "DART_API_KEY를 설정해야 실제 공시 자료를 조회할 수 있습니다.");
        }
    }
}
