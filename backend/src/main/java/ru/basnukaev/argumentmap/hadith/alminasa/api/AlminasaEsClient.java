package ru.basnukaev.argumentmap.hadith.alminasa.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaHit;
import ru.basnukaev.argumentmap.hadith.alminasa.api.dto.AlminasaPage;

/**
 * Узкий HTTP-клиент открытого ES-прокси alminasa.ai (ADR-060, спека §A).
 * {@code POST {base}/api/reactivesearchproxy/{prefix}{index}{suffix}/_search},
 * обязательные Origin/Referer. Только 4 запроса краулера: страница hadith-12
 * (search_after по hadith_serial_id) и батчевые terms-выборки
 * narrators/explanations/rulings по id.
 *
 * <p>{@code @Retry(name="alminasaApi")} — transient-предикат
 * {@link AlminasaTransientFailurePredicate}. ВАЖНО: вызывать через
 * Spring-прокси (инжект), не self-invoke (регрессия Сессии 55 с llmApi).
 */
@Component
@ConditionalOnProperty(name = "alminasa.enabled", havingValue = "true", matchIfMissing = true)
public class AlminasaEsClient {

    private static final Logger log = LoggerFactory.getLogger(AlminasaEsClient.class);

    static final String HADITH_INDEX = "hadith-12";
    static final String NARRATORS_INDEX = "narrators-12";
    static final String EXPLANATION_INDEX = "hadith-explanation-12";
    static final String RULINGS_INDEX = "rulings-12_v2";

    private final HttpClient httpClient;
    private final AlminasaProperties props;
    private final ObjectMapper objectMapper;

    public AlminasaEsClient(HttpClient alminasaHttpClient,
                            AlminasaProperties props,
                            ObjectMapper objectMapper) {
        this.httpClient = alminasaHttpClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Страница корпуса hadith-12 по {@code hadith_serial_id} asc.
     *
     * @param afterSerialId курсор search_after (null — с начала корпуса)
     */
    @Retry(name = "alminasaApi")
    public AlminasaPage fetchHadithPage(Long afterSerialId, int size) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", size);
        body.set("query", objectMapper.createObjectNode()
                .set("match_all", objectMapper.createObjectNode()));
        body.putArray("sort").addObject()
                .set("hadith_serial_id", objectMapper.createObjectNode().put("order", "asc"));
        body.put("track_total_hits", true);
        if (afterSerialId != null) {
            body.putArray("search_after").add(afterSerialId);
        }
        return toPage(search(HADITH_INDEX, body));
    }

    /** Нарраторы по их numeric id (id рави из narrators[] хадис-дока). */
    @Retry(name = "alminasaApi")
    public List<AlminasaHit> fetchNarratorsByIds(Collection<Long> ids) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", ids.size());
        ArrayNode terms = body.putObject("query").putObject("terms").putArray("id");
        ids.forEach(terms::add);
        return toPage(search(NARRATORS_INDEX, body)).hits();
    }

    /** Шархи по hadith_id (terms по вложенному hadith.hadith_id). */
    @Retry(name = "alminasaApi")
    public AlminasaPage fetchExplanationsByHadithIds(Collection<String> hadithIds) {
        return fetchDependents(EXPLANATION_INDEX, "hadith.hadith_id", hadithIds);
    }

    /** Рулинги по hadith_id (все narrations_type — фильтрует маппер Плана 3). */
    @Retry(name = "alminasaApi")
    public AlminasaPage fetchRulingsByHadithIds(Collection<String> hadithIds) {
        return fetchDependents(RULINGS_INDEX, "hadith_id", hadithIds);
    }

    private AlminasaPage fetchDependents(String index, String termsField, Collection<String> hadithIds) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", props.crawl().dependentFetchSize());
        ArrayNode terms = body.putObject("query").putObject("terms").putArray(termsField);
        hadithIds.forEach(terms::add);
        body.put("track_total_hits", true);
        AlminasaPage page = toPage(search(index, body));
        if (page.totalHits() > page.hits().size()) {
            // dependent-fetch-size не вместил все доки батча — данные не потеряны
            // навсегда (re-crawl батча), но сигнал поднять size/уменьшить батч
            log.warn("alminasa {}: total={} > возвращено {} (dependent-fetch-size={}) — "
                            + "увеличь alminasa.crawl.dependent-fetch-size или уменьши dependent-batch-size",
                    index, page.totalHits(), page.hits().size(), props.crawl().dependentFetchSize());
        }
        return page;
    }

    private JsonNode search(String index, ObjectNode body) {
        URI uri = URI.create(props.baseUrl() + "/api/reactivesearchproxy/"
                + props.indexPrefix() + index + props.indexSuffix() + "/_search");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Origin", props.origin())
                .header("Referer", props.origin() + "/")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new AlminasaApiException(response.statusCode(),
                        "alminasa ES вернул HTTP " + response.statusCode() + " на " + index);
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new AlminasaApiException(0, "alminasa ES I/O ошибка на " + index, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AlminasaApiException(-1, "alminasa ES запрос прерван", e);
        }
    }

    private AlminasaPage toPage(JsonNode response) {
        JsonNode hitsNode = response.path("hits");
        long total = hitsNode.path("total").path("value").asLong();
        List<AlminasaHit> hits = new ArrayList<>();
        for (JsonNode hit : hitsNode.path("hits")) {
            hits.add(new AlminasaHit(
                    hit.path("_id").asText(), hit.path("_source"), hit.path("sort")));
        }
        return new AlminasaPage(total, List.copyOf(hits));
    }
}
