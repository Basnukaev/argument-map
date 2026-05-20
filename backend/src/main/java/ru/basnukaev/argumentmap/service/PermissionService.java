package ru.basnukaev.argumentmap.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.basnukaev.argumentmap.auth.domain.UserRole;
import ru.basnukaev.argumentmap.domain.Topic;
import ru.basnukaev.argumentmap.domain.TopicMember;
import ru.basnukaev.argumentmap.domain.TopicMemberRole;
import ru.basnukaev.argumentmap.exception.BookAccessDeniedException;
import ru.basnukaev.argumentmap.exception.BookNotFoundException;
import ru.basnukaev.argumentmap.exception.BookWriteAccessDeniedException;
import ru.basnukaev.argumentmap.exception.InsufficientRoleException;
import ru.basnukaev.argumentmap.exception.TopicAccessDeniedException;
import ru.basnukaev.argumentmap.exception.TopicNotFoundException;
import ru.basnukaev.argumentmap.exception.TopicWriteAccessDeniedException;
import ru.basnukaev.argumentmap.library.domain.Book;
import ru.basnukaev.argumentmap.library.domain.BookMember;
import ru.basnukaev.argumentmap.library.domain.BookMemberRole;
import ru.basnukaev.argumentmap.library.repository.BookMemberRepository;
import ru.basnukaev.argumentmap.library.repository.BookRepository;
import ru.basnukaev.argumentmap.repository.TopicMemberRepository;
import ru.basnukaev.argumentmap.repository.TopicRepository;

/**
 * Permission checks для тем и library books (ADR-043 + Amendment Этап 22.c).
 * Vis матрица:
 * <ul>
 *   <li>PRIVATE: только owner может read/write
 *   <li>SHARED: owner + EDITOR могут write, owner + EDITOR + MEMBER могут read
 *   <li>PUBLIC: все аутентифицированные могут read, owner + EDITOR могут write
 * </ul>
 *
 * <p>ADMIN роль (ADR-040) bypass всех проверок.
 *
 * <p>Делается в Service-слое (не в Controller через @PreAuthorize) для
 * переиспользования в future GraphQL/CLI/scheduled jobs.
 *
 * <p>Q&amp;A questions/answers не имеют visibility model (open discussion) -
 * для author/admin guards на mutating операциях см. QuestionService /
 * AnswerService напрямую.
 */
@Service
public class PermissionService {

    private final TopicRepository topicRepository;
    private final TopicMemberRepository topicMemberRepository;
    private final BookRepository bookRepository;
    private final BookMemberRepository bookMemberRepository;

    public PermissionService(TopicRepository topicRepository,
                             TopicMemberRepository topicMemberRepository,
                             BookRepository bookRepository,
                             BookMemberRepository bookMemberRepository) {
        this.topicRepository = topicRepository;
        this.topicMemberRepository = topicMemberRepository;
        this.bookRepository = bookRepository;
        this.bookMemberRepository = bookMemberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canReadTopic(UUID topicId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        return canReadTopic(topic, userId);
    }

    /**
     * Перегрузка для случаев когда у вызывающего уже есть Topic - избегаем
     * лишнего SELECT (используется в TopicService.getTopic после findById).
     */
    @Transactional(readOnly = true)
    public boolean canReadTopic(Topic topic, UUID userId) {
        return VisibilityPolicy.canRead(
                topic.visibility(), topic.createdBy(), userId,
                actorId -> topicMemberRepository.existsByTopicAndUser(topic.id(), actorId)
        );
    }

    @Transactional(readOnly = true)
    public boolean canWriteTopic(UUID topicId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException(topicId));
        return canWriteTopic(topic, userId);
    }

    @Transactional(readOnly = true)
    public boolean canWriteTopic(Topic topic, UUID userId) {
        return VisibilityPolicy.canWrite(
                topic.visibility(), topic.createdBy(), userId,
                actorId -> topicMemberRepository.findByTopicAndUser(topic.id(), actorId)
                        .map(TopicMember::role)
                        .map(TopicMemberRole.EDITOR::equals)
                        .orElse(false)
        );
    }

    /**
     * Только owner темы может удалять её, менять visibility и управлять
     * членами. EDITOR это не может (даже на SHARED).
     */
    @Transactional(readOnly = true)
    public boolean isOwner(UUID topicId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        return topicRepository.findById(topicId)
                .map(t -> t.createdBy().equals(userId))
                .orElse(false);
    }

    // ---- assert-варианты (бросают исключение) ----

    @Transactional(readOnly = true)
    public void assertCanRead(UUID topicId, UUID userId, String role) {
        if (!canReadTopic(topicId, userId, role)) {
            throw new TopicAccessDeniedException(topicId, userId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanWrite(UUID topicId, UUID userId, String role) {
        // Если читать нельзя - это access deny на уровне read (404-like
        // behaviour: не leak'аем существование private темы). Если читать
        // можно но писать нельзя - это write deny.
        if (!canReadTopic(topicId, userId, role)) {
            throw new TopicAccessDeniedException(topicId, userId);
        }
        if (!canWriteTopic(topicId, userId, role)) {
            throw new TopicWriteAccessDeniedException(topicId, userId);
        }
    }

    @Transactional(readOnly = true)
    public void assertIsOwner(UUID topicId, UUID userId, String role) {
        if (!isOwner(topicId, userId, role)) {
            throw new TopicWriteAccessDeniedException(topicId, userId);
        }
    }

    // ============================================================
    // Library books (ADR-043 Amendment, Этап 22.c)
    // ============================================================

    @Transactional(readOnly = true)
    public boolean canReadBook(UUID bookId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return canReadBook(book, userId);
    }

    /**
     * Перегрузка для случаев когда у вызывающего уже есть Book - избегаем
     * лишнего SELECT.
     */
    @Transactional(readOnly = true)
    public boolean canReadBook(Book book, UUID userId) {
        return VisibilityPolicy.canRead(
                book.visibility(), book.createdBy(), userId,
                actorId -> bookMemberRepository.existsByBookAndUser(book.id(), actorId)
        );
    }

    @Transactional(readOnly = true)
    public boolean canWriteBook(UUID bookId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));
        return canWriteBook(book, userId);
    }

    @Transactional(readOnly = true)
    public boolean canWriteBook(Book book, UUID userId) {
        return VisibilityPolicy.canWrite(
                book.visibility(), book.createdBy(), userId,
                actorId -> bookMemberRepository.findByBookAndUser(book.id(), actorId)
                        .map(BookMember::role)
                        .map(BookMemberRole.EDITOR::equals)
                        .orElse(false)
        );
    }

    /**
     * Только owner книги может удалять её, менять visibility и управлять
     * членами. EDITOR этого не может. ADMIN bypass.
     */
    @Transactional(readOnly = true)
    public boolean isBookOwner(UUID bookId, UUID userId, String role) {
        if (UserRole.ADMIN.equals(role)) {
            return true;
        }
        return bookRepository.findById(bookId)
                .map(b -> b.createdBy() != null && b.createdBy().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public void assertCanReadBook(UUID bookId, UUID userId, String role) {
        if (!canReadBook(bookId, userId, role)) {
            throw new BookAccessDeniedException(bookId, userId);
        }
    }

    @Transactional(readOnly = true)
    public void assertCanWriteBook(UUID bookId, UUID userId, String role) {
        // Если читать нельзя - access deny на уровне read. Иначе если
        // писать нельзя - write deny. Тот же подход что для топиков.
        if (!canReadBook(bookId, userId, role)) {
            throw new BookAccessDeniedException(bookId, userId);
        }
        if (!canWriteBook(bookId, userId, role)) {
            throw new BookWriteAccessDeniedException(bookId, userId);
        }
    }

    @Transactional(readOnly = true)
    public void assertIsBookOwner(UUID bookId, UUID userId, String role) {
        if (!isBookOwner(bookId, userId, role)) {
            throw new BookWriteAccessDeniedException(bookId, userId);
        }
    }

    /**
     * Vision 49d Section 2.4: role-based authorization. Бросает
     * {@link InsufficientRoleException} если actual роль ниже required в
     * иерархии USER &lt; STUDENT &lt; SCHOLAR &lt; ADMIN. Семантика
     * матрицы прав - см.
     * {@code docs/superpowers/specs/2026-05-20-roles-system-design.md}.
     *
     * <p>Использовать в service-слое перед действием которое требует
     * минимальной роли. Пример: HadithGradeService.addGrade →
     * {@code assertHasRoleAtLeast(userId, role, UserRole.SCHOLAR)}.
     *
     * <p>Не делает DB query - чистая проверка in-memory hierarchy.
     */
    public void assertHasRoleAtLeast(UUID userId, String role, String requiredRole) {
        if (!UserRole.hasAtLeast(role, requiredRole)) {
            throw new InsufficientRoleException(userId, role, requiredRole);
        }
    }
}
