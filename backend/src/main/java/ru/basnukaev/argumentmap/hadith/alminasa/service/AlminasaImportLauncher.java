package ru.basnukaev.argumentmap.hadith.alminasa.service;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.alminasa.service.dto.AlminasaImportSummary;
import ru.basnukaev.argumentmap.hadith.alminasa.web.AlminasaImportConflictException;

/**
 * Async-запуск маппинга alminasa staging→hd_* на single-thread executor
 * ({@link AlminasaImportConfig#alminasaImportExecutor}) с in-memory state
 * (план 5, решение 2).
 *
 * <p><b>Контракт переходов (фикс C2):</b> RUNNING ставится СИНХРОННО в
 * launch-методе ДО submit (CAS IDLE→RUNNING; занято → {@link
 * AlminasaImportConflictException} → 409). {@code TaskRejectedException} на
 * submit (queue=0 + ещё живой воркер) → откат в IDLE + 409. async-тело
 * ОБЯЗАНО гарантировать уход из RUNNING: {@code try { summary → IDLE+lastSummary }
 * catch (RuntimeException → IDLE+lastError) finally { status != RUNNING }} —
 * иначе один transient-фейл навечно залочил бы 409.
 *
 * <p>Один executor сериализует ВСЕ виды импорта: narrators при работающем
 * hadiths → 409 (осознанно). Состояние in-memory, НЕ переживает рестарт бэка —
 * осознанно: рестарт = аварийное восстановление (state сбрасывается в IDLE,
 * импорт идемпотентен, re-run дочинит частичные данные).
 *
 * <p>Submit идёт ПРЯМО на инжектнутый executor (не через {@code @Async}): тело
 * прогона — приватный метод того же бина, self-invocation обошёл бы прокси
 * {@code @Async}. Прямой {@code submit} даёт синхронный {@code TaskRejectedException}
 * на rejection (AbortPolicy) — ровно то, что нужно контракту C2.
 */
@Service
public class AlminasaImportLauncher {

    private static final Logger log = LoggerFactory.getLogger(AlminasaImportLauncher.class);

    /** Статус прогона: запущен ли импорт прямо сейчас. */
    public enum Status { IDLE, RUNNING }

    /** Вид прогона. */
    public enum Kind { NARRATORS, HADITHS, ALL }

    private final AlminasaImportService importService;
    private final ThreadPoolTaskExecutor executor;

    /** Снапшот состояния (immutable); подменяется атомарно на переходах. */
    private final AtomicReference<State> state = new AtomicReference<>(State.idle());

    /** Live-счётчик обработанных доков текущего прогона (читается в {@link #status()}). */
    private final AtomicInteger runningProcessed = new AtomicInteger(0);

    public AlminasaImportLauncher(AlminasaImportService importService,
                                  @Qualifier("alminasaImportExecutor") ThreadPoolTaskExecutor executor) {
        this.importService = importService;
        this.executor = executor;
    }

    /** Запуск импорта рави. Занято → 409; иначе RUNNING + async submit. */
    public State launchNarrators() {
        return launch(Kind.NARRATORS, null);
    }

    /**
     * Запуск импорта хадисов (опционально одного сборника по {@code bookId}).
     * Занято → 409; иначе RUNNING + async submit.
     */
    public State launchHadiths(Integer bookId) {
        return launch(Kind.HADITHS, bookId);
    }

    /**
     * Синхронный CAS IDLE→RUNNING ДО submit (фикс C2). Прямой submit на executor:
     * AbortPolicy при занятом потоке → {@code TaskRejectedException} → откат
     * RUNNING→IDLE + 409.
     */
    private State launch(Kind kind, Integer bookId) {
        claimRunning(kind, bookId);
        try {
            executor.execute(() -> runImport(kind, bookId));
        } catch (TaskRejectedException ex) {
            state.set(State.idle());
            throw new AlminasaImportConflictException();
        }
        return state.get();
    }

    /**
     * Синхронный CAS IDLE→RUNNING ДО submit. Занято (уже RUNNING) →
     * {@link AlminasaImportConflictException}. Сбрасывает live-счётчик.
     */
    private void claimRunning(Kind kind, Integer bookId) {
        State running = State.running(kind, bookId);
        State previous = state.get();
        if (previous.status() == Status.RUNNING || !state.compareAndSet(previous, running)) {
            throw new AlminasaImportConflictException();
        }
        runningProcessed.set(0);
    }

    /**
     * Тело прогона на executor-потоке. Гарантирует уход из RUNNING: на нормальном
     * завершении state ← IDLE + lastSummary; на RuntimeException ← IDLE +
     * lastError — иначе один transient-фейл навечно залочил бы 409 (фикс C2).
     * {@code processedSoFar} обновляется live через IntConsumer.
     */
    private void runImport(Kind kind, Integer bookId) {
        try {
            AlminasaImportSummary summary = switch (kind) {
                case NARRATORS -> importService.importNarrators(runningProcessed::set);
                case HADITHS -> importService.importHadiths(bookId, runningProcessed::set);
                case ALL -> importService.importAll();
            };
            state.set(State.finished(kind, bookId, summary));
        } catch (RuntimeException e) {
            log.error("alminasa import ({}) упал", kind, e);
            state.set(State.failed(kind, bookId, e.toString()));
        }
    }

    /**
     * Снапшот состояния. При RUNNING подставляет live-счётчик
     * {@link #runningProcessed} в {@code processedSoFar}.
     */
    public State status() {
        State snapshot = state.get();
        if (snapshot.status() == Status.RUNNING) {
            return snapshot.withProcessedSoFar(runningProcessed.get());
        }
        return snapshot;
    }

    /**
     * In-memory снапшот прогона. {@code lastSummary}/{@code lastError} — взаимно
     * исключающи (одно из двух непусто после завершения). {@code processedSoFar}
     * живёт здесь, в State (а НЕ в {@link AlminasaImportSummary}); туда же
     * {@code kind}/{@code bookIdFilter}/{@code startedAt} (решение 2 — не
     * расширять summary).
     */
    public record State(
            Status status,
            Kind kind,
            Integer bookIdFilter,
            OffsetDateTime startedAt,
            int processedSoFar,
            AlminasaImportSummary lastSummary,
            String lastError) {

        static State idle() {
            return new State(Status.IDLE, null, null, null, 0, null, null);
        }

        static State running(Kind kind, Integer bookId) {
            return new State(Status.RUNNING, kind, bookId, OffsetDateTime.now(), 0, null, null);
        }

        static State finished(Kind kind, Integer bookId, AlminasaImportSummary summary) {
            return new State(Status.IDLE, kind, bookId, null, 0, summary, null);
        }

        static State failed(Kind kind, Integer bookId, String error) {
            return new State(Status.IDLE, kind, bookId, null, 0, null, error);
        }

        State withProcessedSoFar(int processed) {
            return new State(status, kind, bookIdFilter, startedAt, processed, lastSummary, lastError);
        }
    }
}
