package ru.basnukaev.argumentmap.hadith.sunnah.repository;

/**
 * Общие хелперы staging-DAO sunnah.com. В отличие от shamela-DAO здесь
 * нет nullable-сеттеров: sunnah-DAO используют
 * {@code JdbcTemplate.batchUpdate(sql, List&lt;Object[]&gt;)}, который сам
 * корректно подаёт SQL NULL для null-элементов массива, а jsonb-колонки
 * получают значение через {@code ?::jsonb}-cast. {@code imported_at} имеет
 * DEFAULT now() и обновляется в {@code ON CONFLICT}-ветке, поэтому не
 * передаётся параметром. Остаётся только агрегация результата batch'а.
 */
final class SunnahDaoSupport {

    private SunnahDaoSupport() {
    }

    /**
     * Суммирует затронутые строки по batch'у. JDBC-драйвер может вернуть
     * {@code SUCCESS_NO_INFO (-2)}, если число неизвестно — считаем как 1.
     *
     * <p>Возвращаемое из upsertAll значение — «обработано строк»
     * (insert+update суммарно), приблизительное при SUCCESS_NO_INFO; это
     * НЕ дельта вставок. Вызывающим использовать только для логирования.
     */
    static int sumAffected(int[] affected) {
        int total = 0;
        for (int n : affected) {
            total += (n >= 0) ? n : 1;
        }
        return total;
    }
}
