package ru.basnukaev.argumentmap.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ru.basnukaev.argumentmap.web.RequestContextLogFilter;

/**
 * Standalone-тест для {@link GlobalExceptionHandler} (PROD-READINESS P2-1).
 * Не поднимает Testcontainers - чистый MockMvc с advice + throwing-фикстурой.
 * Проверяет: (a) неожиданное исключение → 500 ProblemDetail без leak'а
 * message/stack trace + requestId-property; (b) специфичные handler'ы не
 * регрессировали (IllegalArgumentException → 400 с собственным slug).
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void unexpectedException_returns500ProblemDetail_withRequestId_noLeak() throws Exception {
        // RequestContextLogFilter в проде кладёт requestId в MDC; эмулируем.
        String requestId = "test-request-id-123";
        MDC.put(RequestContextLogFilter.MDC_REQUEST_ID, requestId);

        mockMvc.perform(get("/test-fixture/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("internal-error")))
                .andExpect(jsonPath("$.title").value("Внутренняя ошибка"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.requestId").value(requestId))
                // НЕ leak'аем секрет из exception message и stack trace в тело
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRET_LEAK_TOKEN"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("ThrowingController"))));
    }

    @Test
    void illegalArgument_stillReturns400_withSpecificSlug_noRegression() throws Exception {
        mockMvc.perform(get("/test-fixture/bad-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.containsString("illegal-argument")))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedBody_returns400ProblemDetail_noLeak() throws Exception {
        // IllegalArgumentException из compact-конструктора record'а на этапе
        // десериализации → HttpMessageNotReadableException → 400 (не 500).
        // value=999 с "секретом" в сообщении конструктора - проверяем что
        // фрагмент payload не утекает наружу.
        mockMvc.perform(post("/test-fixture/parse-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(
                        org.hamcrest.Matchers.containsString("malformed-request-body")))
                .andExpect(jsonPath("$.title").value("Некорректное тело запроса"))
                .andExpect(jsonPath("$.status").value(400))
                // НЕ leak'аем фрагменты payload / сообщение конструктора в тело
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SECRET_LEAK_TOKEN"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("999"))));
    }

    @Test
    void malformedJson_returns400ProblemDetail() throws Exception {
        // битый JSON (не закрытая скобка) → HttpMessageNotReadableException → 400
        mockMvc.perform(post("/test-fixture/parse-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(
                        org.hamcrest.Matchers.containsString("malformed-request-body")))
                .andExpect(jsonPath("$.status").value(400));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test-fixture/boom")
        String boom() {
            // message с "секретом" - проверяем что он не утекает в ответ
            throw new IllegalStateException("SECRET_LEAK_TOKEN: internal SQL detail");
        }

        @GetMapping("/test-fixture/bad-arg")
        String badArg() {
            throw new IllegalArgumentException("плохой аргумент");
        }

        @PostMapping("/test-fixture/parse-body")
        String parseBody(@RequestBody ValidatingBody body) {
            return "ok";
        }
    }

    /**
     * Record с валидацией в compact-конструкторе - имитирует PdfBbox:
     * IllegalArgumentException бросается во время Jackson-десериализации,
     * Spring оборачивает в HttpMessageNotReadableException.
     */
    record ValidatingBody(int value) {
        ValidatingBody {
            if (value < 0 || value > 100) {
                throw new IllegalArgumentException(
                        "SECRET_LEAK_TOKEN: value вне диапазона, получено value=" + value);
            }
        }
    }
}
