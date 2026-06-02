package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;

/**
 * Unit-тесты {@link ShamelaChapterMapper} без Spring/БД: DAO и репозиторий
 * замоканы, сохранённые {@link Chapter} перехватываются в список.
 *
 * <p>Фокус - детект потери глав из-за битого parent-tree (цикл /
 * самореференция). Раньше такие title молча исчезали (BFS до них не
 * доходил). Теперь они warn-логируются и привязываются к root как fallback.
 */
class ShamelaChapterMapperTest {

    private static final long BOOK_ID = 42L;
    private static final UUID BOOK_UUID = UUID.randomUUID();

    private ShamelaTitleDao titleDao;
    private ChapterRepository chapterRepository;
    private ShamelaChapterMapper mapper;

    private final List<Chapter> saved = new ArrayList<>();
    private ListAppender<ILoggingEvent> logAppender;
    private Logger mapperLogger;

    @BeforeEach
    void setUp() {
        titleDao = mock(ShamelaTitleDao.class);
        chapterRepository = mock(ChapterRepository.class);
        saved.clear();
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return null;
        });
        mapper = new ShamelaChapterMapper(titleDao, chapterRepository);

        mapperLogger = (Logger) LoggerFactory.getLogger(ShamelaChapterMapper.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        mapperLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        mapperLogger.detachAppender(logAppender);
    }

    private ShamelaTitleRow title(int id, Integer parentId, String content) {
        return new ShamelaTitleRow(BOOK_ID, id, content, null, parentId);
    }

    @Test
    void mapsCleanTreeWithoutWarning() {
        when(titleDao.findAllByBookId(BOOK_ID)).thenReturn(List.of(
                title(1, null, "Корень"),
                title(2, 1, "Раздел"),
                title(3, 2, "Подраздел")
        ));

        int created = mapper.mapChapters(BOOK_UUID, BOOK_ID, Instant.now());

        assertThat(created).isEqualTo(3);
        assertThat(saved).hasSize(3);
        assertThat(warnings()).isEmpty();
    }

    @Test
    void twoNodeCycle_chaptersStillMapped_warnLogged() {
        // Цикл: 2→parent 3, 3→parent 2. Ни один не root → раньше оба
        // молча терялись. Плюс чистый root 1, который должен импортироваться.
        when(titleDao.findAllByBookId(BOOK_ID)).thenReturn(List.of(
                title(1, null, "Корень"),
                title(2, 3, "Цикл A"),
                title(3, 2, "Цикл B")
        ));

        int created = mapper.mapChapters(BOOK_UUID, BOOK_ID, Instant.now());

        // Все три главы сохранены - ничего не потеряно
        assertThat(created).isEqualTo(3);
        assertThat(saved).extracting(Chapter::title)
                .containsExactlyInAnyOrder("Корень", "Цикл A", "Цикл B");
        // Чистый root привязан корректно (parent=null)
        assertThat(saved).filteredOn(c -> c.title().equals("Корень"))
                .allSatisfy(c -> assertThat(c.parentChapterId()).isNull());
        // Звенья цикла привязаны к root как fallback (parent=null)
        assertThat(saved).filteredOn(c -> c.title().startsWith("Цикл"))
                .allSatisfy(c -> assertThat(c.parentChapterId()).isNull());

        // Потеря наблюдаема: warn с id и причиной
        List<String> warns = warnings();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0))
                .contains("цикл")
                .contains("#2")
                .contains("#3")
                .contains(String.valueOf(BOOK_ID));
    }

    @Test
    void nonExistentParent_treatedAsRoot_noWarning() {
        // parent_id=999 не существует - title становится root (orphan
        // handling), без потери, без warn. Это штатное поведение, не цикл.
        when(titleDao.findAllByBookId(BOOK_ID)).thenReturn(List.of(
                title(1, 999, "Сирота"),
                title(2, 1, "Дитя сироты")
        ));

        int created = mapper.mapChapters(BOOK_UUID, BOOK_ID, Instant.now());

        assertThat(created).isEqualTo(2);
        assertThat(saved).filteredOn(c -> c.title().equals("Сирота"))
                .allSatisfy(c -> assertThat(c.parentChapterId()).isNull());
        // Orphan-as-root попадает в очередь, его дитя дозапускается →
        // оба placed, цикла нет → warn не нужен
        assertThat(warnings()).isEmpty();
    }

    @Test
    void selfReference_isCycle_mappedAtRootWithWarning() {
        // Самореференция: 2→parent 2. Тоже не root → терялась бы молча.
        when(titleDao.findAllByBookId(BOOK_ID)).thenReturn(List.of(
                title(1, null, "Корень"),
                title(2, 2, "Сам себе родитель")
        ));

        int created = mapper.mapChapters(BOOK_UUID, BOOK_ID, Instant.now());

        assertThat(created).isEqualTo(2);
        assertThat(saved).extracting(Chapter::title)
                .containsExactlyInAnyOrder("Корень", "Сам себе родитель");
        assertThat(warnings()).hasSize(1);
        assertThat(warnings().get(0)).contains("#2");
    }

    private List<String> warnings() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
