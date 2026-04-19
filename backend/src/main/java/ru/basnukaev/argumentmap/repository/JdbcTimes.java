package ru.basnukaev.argumentmap.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Утилита маппинга Instant ↔ OffsetDateTime для TIMESTAMPTZ-колонок.
 * PG JDBC не умеет автоматически вывести SQL-тип для {@link Instant}
 * (см. PgPreparedStatement.setObject), поэтому конвертируем к
 * OffsetDateTime в UTC на запись и обратно — на чтение.
 */
public final class JdbcTimes {

    private JdbcTimes() {
    }

    public static OffsetDateTime odt(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime odt = rs.getObject(column, OffsetDateTime.class);
        return odt == null ? null : odt.toInstant();
    }
}
