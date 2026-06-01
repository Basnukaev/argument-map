package ru.basnukaev.argumentmap.hadith.sunnah.web;

/**
 * Запрос к sunnah-import endpoint при отсутствующем источнике дампа
 * ({@code sunnah.dump.enabled=false} → {@code SunnahDumpReader}-бина нет).
 * Маппится в 503 Service Unavailable. Phase 5 ETL шаг 2.d.
 */
public class SunnahDumpNotConfiguredException extends RuntimeException {

    public SunnahDumpNotConfiguredException() {
        super("Источник дампа sunnah.com не сконфигурирован (sunnah.dump.enabled=false)");
    }
}
