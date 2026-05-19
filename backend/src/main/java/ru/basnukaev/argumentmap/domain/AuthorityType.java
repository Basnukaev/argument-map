package ru.basnukaev.argumentmap.domain;

import java.util.Set;

/**
 * Whitelist значений для {@code authorities.type}. Решает проблему
 * flat namespace: один и тот же UUID мог обозначать учёного,
 * издательство, тахкик - семантически разные роли. Type column +
 * валидация (например, {@code HadithGradeService} принимает только
 * SCHOLAR) предотвращают «sahih от имени издательства».
 *
 * <p>Не Java enum, а String constants - чтобы не плодить enum-mapping
 * boilerplate в JDBC RowMapper и synchronized с CHECK constraint в БД
 * (миграция 47). Конвенция совпадает с {@code UserRole} в auth-домене.
 */
public final class AuthorityType {

    public static final String SCHOLAR = "SCHOLAR";
    public static final String MUHAQQIQ = "MUHAQQIQ";
    public static final String PUBLISHER = "PUBLISHER";
    public static final String AUTHOR = "AUTHOR";
    public static final String OTHER = "OTHER";

    private static final Set<String> ALL = Set.of(SCHOLAR, MUHAQQIQ, PUBLISHER, AUTHOR, OTHER);

    private AuthorityType() {
        // utility class
    }

    public static boolean isValid(String type) {
        return type != null && ALL.contains(type);
    }
}
