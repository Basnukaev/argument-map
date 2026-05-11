package ru.basnukaev.argumentmap.library.shamela.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Утилиты для shamela-DAO. Содержит общие хелперы которые иначе
 * пришлось бы дублировать в каждом DAO: nullable-сеттеры/геттеры
 * для PreparedStatement/ResultSet, агрегация результата batchUpdate,
 * размер batch'а.
 *
 * <p>Используется через композицию (static calls), не наследование -
 * следуем coding-standards "composition over inheritance".
 */
final class ShamelaDaoSupport {

    /**
     * Размер batch'а для bulk upsert в shamela-DAO. Подобран
     * эмпирически: 1000 строк - компромисс между пропускной
     * способностью и размером WAL-сегмента при сбое.
     */
    static final int BATCH_SIZE = 1000;

    private ShamelaDaoSupport() {
    }

    /**
     * Суммирует количество затронутых строк во всех batch'ах.
     * JDBC-драйвер может вернуть SUCCESS_NO_INFO (-2) если число
     * затронутых строк неизвестно - в этом случае считаем как 1.
     */
    static int sumAffected(int[][] batches) {
        int total = 0;
        for (int[] batch : batches) {
            for (int n : batch) {
                total += (n >= 0) ? n : 1;
            }
        }
        return total;
    }

    static void setNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BIGINT);
        } else {
            ps.setLong(idx, value);
        }
    }

    static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    static void setNullableBoolean(PreparedStatement ps, int idx, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.BOOLEAN);
        } else {
            ps.setBoolean(idx, value);
        }
    }

    static void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.VARCHAR);
        } else {
            ps.setString(idx, value);
        }
    }

    /**
     * Передаёт jsonb-строку как VARCHAR с расчётом на {@code ?::jsonb}
     * cast на стороне SQL. Так работают все shamela-DAO с jsonb-колонками
     * (postgresql JDBC у нас в runtime-scope, прямой PGobject недоступен
     * на compile).
     */
    static void setNullableJsonString(PreparedStatement ps, int idx, String json) throws SQLException {
        setNullableString(ps, idx, json);
    }

    static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static Boolean getNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
