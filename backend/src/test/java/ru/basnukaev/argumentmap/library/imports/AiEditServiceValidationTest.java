package ru.basnukaev.argumentmap.library.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.ai.LlmApiException;
import ru.basnukaev.argumentmap.ai.LlmClient;
import ru.basnukaev.argumentmap.library.repository.PageRepository;

/**
 * Unit-тесты для {@link AiEditService#validateProseMirrorJson(String)}.
 * Покрывают валидные cases, markdown fence stripping, и rejection
 * невалидных responses (Этап 17.e, ADR-042).
 *
 * <p>Не SpringBootTest - testing pure parsing logic без БД и HTTP.
 * LlmClient и PageRepository моки (вызовы не делаются в этих тестах
 * validate).
 */
class AiEditServiceValidationTest {

    private AiEditService service;

    @BeforeEach
    void setUp() {
        service = new AiEditService(
                mock(PageRepository.class),
                mock(LlmClient.class),
                new ObjectMapper(),
                10);
    }

    @Test
    void validateProseMirrorJson_validDocStructure_returnsAsIs() {
        String json = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}";

        String result = service.validateProseMirrorJson(json);

        assertThat(result).isEqualTo(json);
    }

    @Test
    void validateProseMirrorJson_emptyContent_returnsAsIs() {
        // ProseMirror doc может быть с пустым content (empty page)
        String json = "{\"type\":\"doc\",\"content\":[]}";

        String result = service.validateProseMirrorJson(json);

        assertThat(result).isEqualTo(json);
    }

    @Test
    void validateProseMirrorJson_markdownFenceWithLanguage_stripped() {
        String wrapped = "```json\n{\"type\":\"doc\",\"content\":[]}\n```";

        String result = service.validateProseMirrorJson(wrapped);

        assertThat(result).isEqualTo("{\"type\":\"doc\",\"content\":[]}");
    }

    @Test
    void validateProseMirrorJson_markdownFenceNoLanguage_stripped() {
        String wrapped = "```\n{\"type\":\"doc\",\"content\":[]}\n```";

        String result = service.validateProseMirrorJson(wrapped);

        assertThat(result).isEqualTo("{\"type\":\"doc\",\"content\":[]}");
    }

    @Test
    void validateProseMirrorJson_wrongRootType_throws() {
        String json = "{\"type\":\"paragraph\",\"content\":[]}";

        assertThatThrownBy(() -> service.validateProseMirrorJson(json))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("не ProseMirror doc");
    }

    @Test
    void validateProseMirrorJson_missingContentArray_throws() {
        String json = "{\"type\":\"doc\"}";

        assertThatThrownBy(() -> service.validateProseMirrorJson(json))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("без content array");
    }

    @Test
    void validateProseMirrorJson_invalidJson_throws() {
        String broken = "{\"type\":\"doc\", invalid";

        assertThatThrownBy(() -> service.validateProseMirrorJson(broken))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("невалидный JSON");
    }

    @Test
    void validateProseMirrorJson_contentNotArray_throws() {
        String json = "{\"type\":\"doc\",\"content\":\"string-not-array\"}";

        assertThatThrownBy(() -> service.validateProseMirrorJson(json))
                .isInstanceOf(LlmApiException.class)
                .hasMessageContaining("без content array");
    }

    @Test
    void validateProseMirrorJson_arabicContent_preserved() {
        // sanity check что arabic text не ломается через JSON парсинг
        String json = "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"بسم الله الرحمن الرحيم\"}]}]}";

        String result = service.validateProseMirrorJson(json);

        assertThat(result).contains("بسم الله الرحمن الرحيم");
    }
}
