package ru.basnukaev.argumentmap.hadith.alminasa.repository;

/**
 * Хелперы staging-DAO alminasa. Копия {@code SunnahDaoSupport} — sunnah-пакет
 * удаляется Планом 4 (ADR-060), зависеть от него нельзя.
 */
final class AmDaoSupport {

    private AmDaoSupport() {
    }

    /**
     * Суммирует затронутые строки batch'а; {@code SUCCESS_NO_INFO (-2)}
     * считаем как 1. Использовать только для логирования.
     */
    static int sumAffected(int[] affected) {
        int total = 0;
        for (int n : affected) {
            total += (n >= 0) ? n : 1;
        }
        return total;
    }
}
