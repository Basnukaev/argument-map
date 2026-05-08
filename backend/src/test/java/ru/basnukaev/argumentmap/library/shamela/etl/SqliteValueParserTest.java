package ru.basnukaev.argumentmap.library.shamela.etl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SqliteValueParserTest {

    @Nested
    class ParseLongOrNull {

        @Test
        void parses_valid_long() {
            assertThat(SqliteValueParser.parseLongOrNull("42")).isEqualTo(42L);
            assertThat(SqliteValueParser.parseLongOrNull("0")).isEqualTo(0L);
            assertThat(SqliteValueParser.parseLongOrNull("9999999999")).isEqualTo(9999999999L);
        }

        @Test
        void trims_whitespace() {
            assertThat(SqliteValueParser.parseLongOrNull("  42  ")).isEqualTo(42L);
        }

        @Test
        void returns_null_for_null_or_blank() {
            assertThat(SqliteValueParser.parseLongOrNull(null)).isNull();
            assertThat(SqliteValueParser.parseLongOrNull("")).isNull();
            assertThat(SqliteValueParser.parseLongOrNull("   ")).isNull();
        }

        @Test
        void returns_null_for_non_numeric() {
            assertThat(SqliteValueParser.parseLongOrNull("abc")).isNull();
            assertThat(SqliteValueParser.parseLongOrNull("12.5")).isNull();
            assertThat(SqliteValueParser.parseLongOrNull("12foo")).isNull();
        }
    }

    @Nested
    class ParseIntegerOrNull {

        @Test
        void parses_valid_int() {
            assertThat(SqliteValueParser.parseIntegerOrNull("42")).isEqualTo(42);
            assertThat(SqliteValueParser.parseIntegerOrNull("-1")).isEqualTo(-1);
        }

        @Test
        void returns_null_for_overflow() {
            assertThat(SqliteValueParser.parseIntegerOrNull("999999999999")).isNull();
        }

        @Test
        void returns_null_for_null_or_blank() {
            assertThat(SqliteValueParser.parseIntegerOrNull(null)).isNull();
            assertThat(SqliteValueParser.parseIntegerOrNull("")).isNull();
        }
    }

    @Nested
    class ParseYearOrNull {

        @Test
        void parses_normal_year() {
            assertThat(SqliteValueParser.parseYearOrNull("728")).isEqualTo(728);
            assertThat(SqliteValueParser.parseYearOrNull("1441")).isEqualTo(1441);
        }

        @Test
        void treats_shamela_unknown_year_99999_as_null() {
            assertThat(SqliteValueParser.parseYearOrNull("99999")).isNull();
        }

        @Test
        void returns_null_for_blank_or_non_numeric() {
            assertThat(SqliteValueParser.parseYearOrNull(null)).isNull();
            assertThat(SqliteValueParser.parseYearOrNull("")).isNull();
            assertThat(SqliteValueParser.parseYearOrNull("unknown")).isNull();
        }
    }

    @Nested
    class ParseBoolOrNull {

        @Test
        void parses_one_as_true_and_zero_as_false() {
            assertThat(SqliteValueParser.parseBoolOrNull("1")).isTrue();
            assertThat(SqliteValueParser.parseBoolOrNull("0")).isFalse();
        }

        @Test
        void trims_whitespace() {
            assertThat(SqliteValueParser.parseBoolOrNull("  1 ")).isTrue();
        }

        @Test
        void returns_null_for_anything_else() {
            assertThat(SqliteValueParser.parseBoolOrNull(null)).isNull();
            assertThat(SqliteValueParser.parseBoolOrNull("")).isNull();
            assertThat(SqliteValueParser.parseBoolOrNull("true")).isNull();
            assertThat(SqliteValueParser.parseBoolOrNull("yes")).isNull();
            assertThat(SqliteValueParser.parseBoolOrNull("2")).isNull();
        }
    }

    @Nested
    class IsDeletedFlag {

        @Test
        void only_string_one_means_deleted() {
            assertThat(SqliteValueParser.isDeletedFlag("1")).isTrue();
            assertThat(SqliteValueParser.isDeletedFlag(" 1 ")).isTrue();
        }

        @Test
        void everything_else_is_not_deleted() {
            assertThat(SqliteValueParser.isDeletedFlag(null)).isFalse();
            assertThat(SqliteValueParser.isDeletedFlag("")).isFalse();
            assertThat(SqliteValueParser.isDeletedFlag("0")).isFalse();
            assertThat(SqliteValueParser.isDeletedFlag("true")).isFalse();
            assertThat(SqliteValueParser.isDeletedFlag("yes")).isFalse();
        }
    }
}
