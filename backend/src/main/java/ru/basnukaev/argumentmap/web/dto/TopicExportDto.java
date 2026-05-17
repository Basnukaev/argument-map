package ru.basnukaev.argumentmap.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JSON-сериализация темы целиком для backup и обмена между инстансами
 * (Этап 6, ADR-037).
 *
 * <p>Включает:
 * <ul>
 *   <li>topic - запись из {@code topics}</li>
 *   <li>nodes - все узлы темы (через {@code NodeRepository.findByTopicId})</li>
 *   <li>edges - все рёбра темы (через {@code EdgeRepository.findByTopicId})</li>
 *   <li>nodeSources - все привязки цитат к узлам (без 9-LEFT-JOIN structured
 *       citation - детали восстанавливаются через locally существующие
 *       Source/Book/Authority при импорте)</li>
 *   <li>sources - top-level массив unique Source записей, на которые ссылаются
 *       nodeSources. Avoiding дубликатов если один source привязан к N узлам</li>
 *   <li>authorities - top-level массив unique Authority записей, на которые
 *       ссылаются sources через {@code authority_id}</li>
 *   <li>books - {@link BookRef} с минимальным набором полей для hint при
 *       импорте (id, title, authorityId). Не полная сериализация книг -
 *       книги это shared library resource (ADR-019), не часть темы</li>
 * </ul>
 *
 * <p>Что НЕ включено (по дизайну):
 * <ul>
 *   <li>revisions - история изменений, ~10x размер темы, не нужна для
 *       обмена и backup</li>
 *   <li>Q&A (questions/answers) - standalone domain, не привязан к topic</li>
 *   <li>Books/Muhaqqiqs/Publishers/PublicationPlaces полностью - referenced
 *       by UUID. При импорте: find-or-skip, missing book → warning, source
 *       сохраняется без bookId</li>
 *   <li>PDF файлы / lib_files - физические бинарные данные, размер</li>
 * </ul>
 *
 * <p>{@code formatVersion} - whitelist в {@code TopicImportService}.
 * Future-proofing миграций формата
 */
public record TopicExportDto(
        String formatVersion,
        Instant exportedAt,
        TopicData topic,
        List<NodeData> nodes,
        List<EdgeData> edges,
        List<NodeSourceData> nodeSources,
        List<SourceData> sources,
        List<AuthorityData> authorities,
        List<BookRef> books
) {

    /**
     * Топик без счётчиков/JOIN - только поля из таблицы {@code topics}.
     */
    public record TopicData(
            UUID id,
            String title,
            String description,
            UUID rootNodeId,
            UUID createdBy,
            Instant createdAt
    ) {
    }

    /**
     * Узел темы с координатами и статусом. {@code nodeType} и {@code status}
     * сериализуются как enum-имена (Jackson default для enum).
     */
    public record NodeData(
            UUID id,
            UUID topicId,
            String nodeType,
            String content,
            String status,
            Double posX,
            Double posY,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /**
     * Ребро. {@code fromNodeId}/{@code toNodeId} остаются в "локальных" UUID
     * из экспорта, при импорте проходят через {@code Map<oldUUID, newUUID>}.
     */
    public record EdgeData(
            UUID id,
            UUID fromNodeId,
            UUID toNodeId,
            String edgeType,
            String rationale,
            String sourceHandle,
            String targetHandle,
            UUID createdBy,
            Instant createdAt
    ) {
    }

    /**
     * Привязка цитаты к узлу. Все positional поля переносятся as-is, при
     * импорте {@code pageId}/{@code pdfFileId}/{@code imageRegionId} могут
     * быть {@code null}-ифицированы если соответствующая lib_* запись не
     * найдена (find-or-skip).
     */
    public record NodeSourceData(
            UUID id,
            UUID nodeId,
            UUID sourceId,
            String quote,
            String context,
            String location,
            UUID pageId,
            Integer rangeStart,
            Integer rangeEnd,
            UUID pdfFileId,
            Integer pdfPageNumber,
            String pdfBbox,
            UUID imageRegionId,
            Instant createdAt
    ) {
    }

    /**
     * Source - точка привязки цитаты. {@code authorityId} ссылается на
     * authority в текущем экспорте, {@code bookId} - на library book
     * (handled find-or-skip при импорте).
     */
    public record SourceData(
            UUID id,
            String sourceType,
            String title,
            String citation,
            String reliability,
            UUID authorityId,
            UUID bookId,
            String metadata,
            Instant createdAt
    ) {
    }

    /**
     * Authority - автор/учёный. При импорте используется find-or-create
     * по {@code (name, era)} паре для дедупликации.
     */
    public record AuthorityData(
            UUID id,
            String name,
            String bio,
            String era,
            String madhab,
            String metadata,
            Instant createdAt,
            String fullName,
            Integer deathYearHijri
    ) {
    }

    /**
     * Минимальный hint для библиотечной книги - только id и title чтобы
     * пользователь импортирующий сторону мог понять какие книги ему нужно
     * добавить вручную если они отсутствуют. Полная сериализация книги
     * (chapters/pages/файлы) намеренно исключена - книги это shared
     * library resource, не часть темы.
     */
    public record BookRef(
            UUID id,
            String title,
            UUID authorityId
    ) {
    }
}
