package ru.basnukaev.argumentmap.hadith.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.web.dto.HadithDetailResponse.GradeDto;

/**
 * Unit-тест парсинга курируемых оценок из metadata (без Spring/БД).
 */
class HadithGradesParseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesGradesArray() {
        String metadata = "{\"grades\":["
                + "{\"scholar\":\"аль-Бухари\",\"grade\":\"Сахих\",\"note\":\"№1\"},"
                + "{\"scholar\":\"Муслим\",\"grade\":\"Сахих\"}"
                + "]}";
        List<GradeDto> grades = HadithController.parseGrades(metadata, objectMapper);
        assertEquals(2, grades.size());
        assertEquals("аль-Бухари", grades.get(0).scholar());
        assertEquals("Сахих", grades.get(0).grade());
        assertEquals("№1", grades.get(0).note());
        // note отсутствует → null, не падаем
        assertEquals("Муслим", grades.get(1).scholar());
        assertTrue(grades.get(1).note() == null);
    }

    @Test
    void returnsEmptyForNullOrMissingOrMalformed() {
        assertTrue(HadithController.parseGrades(null, objectMapper).isEmpty());
        assertTrue(HadithController.parseGrades("", objectMapper).isEmpty());
        // metadata без ключа grades
        assertTrue(HadithController.parseGrades("{\"foo\":1}", objectMapper).isEmpty());
        // невалидный JSON → пустой список, без исключения
        assertTrue(HadithController.parseGrades("{not json", objectMapper).isEmpty());
        // grades не массив
        assertTrue(HadithController.parseGrades("{\"grades\":\"x\"}", objectMapper).isEmpty());
    }
}
