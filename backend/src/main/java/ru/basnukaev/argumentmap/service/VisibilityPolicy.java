package ru.basnukaev.argumentmap.service;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Канонический алгоритм authorize-by-visibility для PRIVATE/SHARED/PUBLIC
 * entity'ёв (Topic, Book, и потенциально будущие). Введён backend
 * architecture audit 2026-05-18 finding 4 - PermissionService содержал
 * две почти идентичные реализации (canReadTopic/canWriteTopic vs
 * canReadBook/canWriteBook) различающиеся только member repository.
 *
 * <p>Стратегия:
 * <ul>
 *   <li><b>canRead(visibility, ownerId, actorId, isMember):</b>
 *     PRIVATE → owner-only; SHARED → owner + любой member;
 *     PUBLIC → все аутентифицированные (true).
 *   <li><b>canWrite(visibility, ownerId, actorId, isEditorMember):</b>
 *     PRIVATE → owner-only; SHARED/PUBLIC → owner + EDITOR member.
 * </ul>
 *
 * <p>Лямбды {@code isMember} / {@code isEditorMember} - lookup hooks к
 * соответствующему repository (TopicMemberRepository vs
 * BookMemberRepository). Caller передаёт lambda, helper не знает про
 * репозитории - чисто алгоритм.
 *
 * <p>Visibility-литералы передаются как String (TopicVisibility.PRIVATE /
 * BookVisibility.PRIVATE) - значения совпадают, общий алгоритм работает.
 */
final class VisibilityPolicy {

    static final String PRIVATE = "PRIVATE";
    static final String SHARED = "SHARED";
    static final String PUBLIC = "PUBLIC";

    private VisibilityPolicy() {
    }

    /**
     * Проверка чтения. {@code isMember} - возвращает true если actor
     * есть в members-таблице с любой ролью (lookup делает caller через
     * repository).
     */
    static boolean canRead(String visibility, UUID ownerId, UUID actorId,
                           Predicate<UUID> isMember) {
        if (ownerId != null && ownerId.equals(actorId)) {
            return true;
        }
        if (PUBLIC.equals(visibility)) {
            return true;
        }
        if (SHARED.equals(visibility)) {
            return isMember.test(actorId);
        }
        // PRIVATE без owner-match
        return false;
    }

    /**
     * Проверка записи. {@code isEditorMember} - возвращает true если
     * actor есть в members с ролью EDITOR (lookup делает caller).
     *
     * <p>PRIVATE: только owner. SHARED/PUBLIC: owner + EDITOR.
     */
    static boolean canWrite(String visibility, UUID ownerId, UUID actorId,
                            Predicate<UUID> isEditorMember) {
        if (ownerId != null && ownerId.equals(actorId)) {
            return true;
        }
        if (PRIVATE.equals(visibility)) {
            return false;
        }
        return isEditorMember.test(actorId);
    }

    /**
     * Generic overload через Function для тестирования - возвращает Role
     * либо null, caller сам делает проверку EDITOR.equals. Используется
     * когда у нас уже есть TopicMember/BookMember объект и не хотим
     * лишний lookup.
     */
    static <R> boolean canWriteWithRoleLookup(String visibility, UUID ownerId,
                                              UUID actorId, R editorRole,
                                              Function<UUID, R> roleLookup,
                                              BiPredicate<R, R> roleEquals) {
        if (ownerId != null && ownerId.equals(actorId)) {
            return true;
        }
        if (PRIVATE.equals(visibility)) {
            return false;
        }
        R actorRole = roleLookup.apply(actorId);
        return actorRole != null && roleEquals.test(actorRole, editorRole);
    }
}
