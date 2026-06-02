package ru.basnukaev.argumentmap.library.shamela.service.mapper;

import static ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaMapperUtils.parseStartPage;
import static ru.basnukaev.argumentmap.library.shamela.service.mapper.ShamelaMapperUtils.sanitizeTitle;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.library.domain.Chapter;
import ru.basnukaev.argumentmap.library.repository.ChapterRepository;
import ru.basnukaev.argumentmap.library.shamela.etl.dto.ShamelaTitleRow;
import ru.basnukaev.argumentmap.library.shamela.repository.ShamelaTitleDao;

/**
 * Маппинг {@code lib_shamela_title} → {@code lib_chapters} с сохранением
 * parent-tree. Алгоритм - BFS от root-titles вглубь: на момент создания
 * child-чаптера его parent уже сохранён, и его UUID известен через
 * {@code shamelaIdToChapterUuid}.
 *
 * <p>{@code order_index} = индекс title в монотонном порядке id (shamela
 * вставляет id в порядке появления заголовка в книге).
 *
 * <p>Защита от битых данных: если {@code parent_id} ссылается на
 * несуществующий title (orphan) - такой title становится root, не
 * падаем. Циклы в parent-tree не должны быть в shamela по природе
 * данных, но если случатся (A→parent B, B→parent A) - BFS не зайдёт
 * в них (root-фильтр требует parent в self-таблице, а у звеньев цикла
 * родитель существует). Чтобы такая потеря не была молчаливой, после
 * BFS детектим непрозвавшиеся title (застрявшие в цикле), warn-логируем
 * их id/title и привязываем к root как fallback - книга импортируется
 * целиком, потеря наблюдаема в логах.
 */
@Service
public class ShamelaChapterMapper {

    private static final Logger log = LoggerFactory.getLogger(ShamelaChapterMapper.class);

    private final ShamelaTitleDao shamelaTitleDao;
    private final ChapterRepository chapterRepository;

    public ShamelaChapterMapper(ShamelaTitleDao shamelaTitleDao,
                                ChapterRepository chapterRepository) {
        this.shamelaTitleDao = shamelaTitleDao;
        this.chapterRepository = chapterRepository;
    }

    /**
     * @return сколько chapter-записей создано
     */
    public int mapChapters(UUID bookUuid, long shamelaBookId, Instant now) {
        List<ShamelaTitleRow> titles = shamelaTitleDao.findAllByBookId(shamelaBookId);
        if (titles.isEmpty()) {
            return 0;
        }
        Map<Integer, ShamelaTitleRow> byId = new HashMap<>();
        for (ShamelaTitleRow t : titles) {
            byId.put(t.id(), t);
        }
        Map<Integer, Integer> orderById = new HashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            orderById.put(titles.get(i).id(), i);
        }
        Map<Integer, List<ShamelaTitleRow>> children = new HashMap<>();
        Queue<ShamelaTitleRow> queue = new ArrayDeque<>();
        for (ShamelaTitleRow t : titles) {
            if (t.parentId() == null || !byId.containsKey(t.parentId())) {
                queue.add(t);
            } else {
                children.computeIfAbsent(t.parentId(), k -> new ArrayList<>()).add(t);
            }
        }
        Map<Integer, UUID> shamelaIdToChapterUuid = new HashMap<>();
        int created = 0;
        while (!queue.isEmpty()) {
            ShamelaTitleRow t = queue.poll();
            UUID parentUuid = (t.parentId() != null && byId.containsKey(t.parentId()))
                    ? shamelaIdToChapterUuid.get(t.parentId())
                    : null;
            persistChapter(t, bookUuid, parentUuid, orderById, now, shamelaIdToChapterUuid);
            created++;
            List<ShamelaTitleRow> kids = children.get(t.id());
            if (kids != null) {
                queue.addAll(kids);
            }
        }

        // Детект потери из-за циклов в parent-tree: звенья цикла никогда не
        // попадают в очередь (у них есть существующий parent → не root), и
        // BFS до них не доходит (родитель сам недостижим). Привязываем такие
        // title к root как fallback - порядок id даёт детерминированный обход.
        List<ShamelaTitleRow> unplaced = titles.stream()
                .filter(t -> !shamelaIdToChapterUuid.containsKey(t.id()))
                .toList();
        if (!unplaced.isEmpty()) {
            String summary = unplaced.stream()
                    .map(t -> "#" + t.id() + " (parent=" + t.parentId()
                            + ", «" + sanitizeTitle(t.content()) + "»)")
                    .collect(Collectors.joining(", "));
            log.warn("shamela book {}: {} title(s) не привязаны деревом - "
                            + "вероятен цикл parent_id; привязываю к root как fallback: {}",
                    shamelaBookId, unplaced.size(), summary);
            for (ShamelaTitleRow t : unplaced) {
                persistChapter(t, bookUuid, null, orderById, now, shamelaIdToChapterUuid);
                created++;
            }
        }
        return created;
    }

    private void persistChapter(ShamelaTitleRow t,
                                UUID bookUuid,
                                UUID parentUuid,
                                Map<Integer, Integer> orderById,
                                Instant now,
                                Map<Integer, UUID> shamelaIdToChapterUuid) {
        UUID chapterUuid = UUID.randomUUID();
        Chapter chapter = new Chapter(
                chapterUuid,
                bookUuid,
                parentUuid,
                sanitizeTitle(t.content()),
                orderById.get(t.id()),
                parseStartPage(t.pageRef()),
                now
        );
        chapterRepository.save(chapter);
        shamelaIdToChapterUuid.put(t.id(), chapterUuid);
    }
}
