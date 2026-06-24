package ru.basnukaev.argumentmap.hadith.curation.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import ru.basnukaev.argumentmap.hadith.curation.domain.OverrideEntity;
import ru.basnukaev.argumentmap.hadith.curation.repository.OverrideRepository;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.HadithExplanation;
import ru.basnukaev.argumentmap.hadith.domain.HadithRuling;
import ru.basnukaev.argumentmap.hadith.domain.Matn;
import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.NarratorCommentary;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;

/**
 * Apply-слой курации (ADR-065 §3): накладывает overrides на доменные records
 * хадиса/рави ДО маппинга в DTO. Вызывается из {@code findById/findPage}
 * репозиториев (репозиторный fold) — потому любой read-путь display
 * получает overrides «бесплатно», а правки переживают delete-recreate
 * реимпорта (импорт пишет базовый слой, override живёт отдельно).
 *
 * <p><b>Граница raw/effective:</b> import write-path сравнивает/пишет по
 * {@code findByExternalId} (overrides НЕ применяет — см. javadoc репозиториев).
 * apply трогает только display-методы. Преобразование {@link #apply(Hadith,
 * OverrideSet)} — чистое (static), батч-{@link #load} — единственная точка
 * обращения к БД (один {@code IN}-запрос на тип сущности, без N+1).
 */
@Service
public class OverrideApplyService {

    private final OverrideRepository repo;

    public OverrideApplyService(OverrideRepository repo) {
        this.repo = repo;
    }

    /** Батч-fetch overrides набора записей одной таблицы. Пусто → {@link OverrideSet#EMPTY}. */
    public OverrideSet load(OverrideEntity table, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return OverrideSet.EMPTY;
        }
        return OverrideSet.group(repo.findByEntity(table, ids));
    }

    /** Маппер сателлита в DTO с информацией о record-hide (reveal-режим §4.3). */
    @FunctionalInterface
    public interface HideAwareMapper<T, D> {
        D map(T record, boolean hiddenByAdmin, String hideReason);
    }

    /** Чистое наложение field-overrides сателлита через переданный набор (Фаза 5). */
    @FunctionalInterface
    public interface FieldApply<T> {
        T apply(T record, OverrideSet ov);
    }

    /**
     * Field-override + record-hide для списка сателлитов в ОДИН проход (Фаза 5).
     * Один батч-{@code load} на тип, без N+1: сначала накладываем правки полей
     * через {@code fieldApply} (field-hide включается автоматически — null из
     * {@code applyStr/Int}), затем фильтруем record-hidden (читатель — запись
     * вырезана; ADMIN при {@code reveal=true} — с {@code hiddenByAdmin}+reason).
     * Пустой OverrideSet → records как есть, без флагов (общий случай).
     */
    public <T, D> List<D> applyAndHide(OverrideEntity table, List<T> records,
                                       Function<T, UUID> idOf, FieldApply<T> fieldApply,
                                       boolean reveal, HideAwareMapper<T, D> mapper) {
        if (records.isEmpty()) {
            return List.of();
        }
        OverrideSet ov = load(table, records.stream().map(idOf).toList());
        if (ov.isEmpty()) {
            return records.stream().map(r -> mapper.map(r, false, null)).toList();
        }
        List<D> out = new ArrayList<>(records.size());
        for (T r : records) {
            var hide = ov.recordHide(idOf.apply(r));
            if (hide.isEmpty()) {
                // не скрыта целиком → накладываем field-overrides и маппим
                out.add(mapper.map(fieldApply.apply(r, ov), false, null));
            } else if (reveal) {
                // record-hidden раскрываем ADMIN'у с правками полей поверх
                out.add(mapper.map(fieldApply.apply(r, ov), true, hide.get().reason()));
            }
            // non-reveal + record-hidden → вырезаем
        }
        return List.copyOf(out);
    }

    // ── Hadith ────────────────────────────────────────────────────────────────

    public Hadith applyOne(Hadith h) {
        return apply(h, load(OverrideEntity.HD_HADITHS, List.of(h.id())));
    }

    public List<Hadith> applyHadiths(List<Hadith> hadiths) {
        if (hadiths.isEmpty()) {
            return hadiths;
        }
        OverrideSet ov = load(OverrideEntity.HD_HADITHS, hadiths.stream().map(Hadith::id).toList());
        if (ov.isEmpty()) {
            return hadiths;
        }
        return hadiths.stream().map(h -> apply(h, ov)).toList();
    }

    /**
     * Чистое наложение overrides на хадис. Первоисточник
     * ({@code normalized_matn}, {@code full_text_ar}) сознательно НЕ читается
     * из набора — он защищён whitelist'ом и не может быть переопределён.
     */
    public static Hadith apply(Hadith h, OverrideSet ov) {
        if (!ov.hasEntity(h.id())) {
            return h;
        }
        UUID id = h.id();
        return new Hadith(
                h.id(),
                h.collectionId(),
                ov.applyInt(id, "primary_number", h.primaryNumber()),
                h.normalizedMatn(),                                   // PROTECTED
                ov.applyStr(id, "status", h.status()),
                h.sourceId(),
                h.metadata(),
                h.createdAt(),
                h.externalSource(),
                h.externalId(),
                ov.applyStr(id, "hadith_type", h.hadithType()),
                ov.applyStr(id, "chapter_ar", h.chapterAr()),
                ov.applyStr(id, "sub_chapter_ar", h.subChapterAr()),
                h.fullTextAr(),                                       // PROTECTED
                ov.applyStr(id, "authenticity", h.authenticity()));
    }

    // ── Narrator ──────────────────────────────────────────────────────────────

    public Narrator applyOne(Narrator n) {
        return apply(n, load(OverrideEntity.HD_NARRATORS, List.of(n.id())));
    }

    public List<Narrator> applyNarrators(List<Narrator> narrators) {
        if (narrators.isEmpty()) {
            return narrators;
        }
        OverrideSet ov = load(OverrideEntity.HD_NARRATORS, narrators.stream().map(Narrator::id).toList());
        if (ov.isEmpty()) {
            return narrators;
        }
        return narrators.stream().map(n -> apply(n, ov)).toList();
    }

    /**
     * Чистое наложение overrides на рави. {@code name_ar_normalized} —
     * производная (search/disambiguation), НЕ переопределяется здесь: при
     * правке {@code name_ar} нормализованную форму пересчитывает write-сервис
     * (Фаза 3), а не apply (overrides редки → дрейф нормализации незначим).
     */
    public static Narrator apply(Narrator n, OverrideSet ov) {
        if (!ov.hasEntity(n.id())) {
            return n;
        }
        UUID id = n.id();
        return new Narrator(
                n.id(),
                n.authorityId(),
                ov.applyStr(id, "name_ar", n.nameAr()),
                n.nameArNormalized(),                                 // производная
                ov.applyStr(id, "kunya", n.kunya()),
                ov.applyStr(id, "laqab", n.laqab()),
                ov.applyInt(id, "year_birth_hijri", n.yearBirthHijri()),
                ov.applyInt(id, "year_death_hijri", n.yearDeathHijri()),
                ov.applyStr(id, "birthplace", n.birthplace()),
                ov.applyStr(id, "death_place", n.deathPlace()),
                ov.applyStr(id, "primary_residence", n.primaryResidence()),
                ov.applyStr(id, "reliability_grade", n.reliabilityGrade()),
                ov.applyStr(id, "reliability_comment", n.reliabilityComment()),
                n.transmittedCountCached(),
                n.metadata(),
                n.createdAt(),
                n.externalSource(),
                n.externalId(),
                ov.applyStr(id, "tabaqa", n.tabaqa()),
                ov.applyStr(id, "grade_text", n.gradeText()),
                n.bornOnText(),
                n.diedOnText());
    }

    // ── Сателлиты (Фаза 5) ──────────────────────────────────────────────────────
    // Field-hide включается автоматически: applyStr возвращает null, когда у
    // поля override с hidden=true (см. OverrideSet.applyStr). Первоисточник
    // (matn text_ar/text_ar_normalized, commentary comments) сознательно НЕ
    // читается из набора — он защищён whitelist'ом и не переопределяется.

    /**
     * Наложение overrides на вердикт (§5: ruler_name[hideable], ruler_death_year,
     * ruling_text, book_name, page, volume). Позиционный порядок сверен с
     * {@link HadithRuling}.
     */
    public static HadithRuling apply(HadithRuling r, OverrideSet ov) {
        if (!ov.hasEntity(r.id())) {
            return r;
        }
        UUID id = r.id();
        return new HadithRuling(
                r.id(),
                r.hadithId(),
                ov.applyStr(id, "ruler_name", r.rulerName()),       // hideable
                ov.applyInt(id, "ruler_death_year", r.rulerDeathYear()),
                ov.applyStr(id, "ruling_text", r.rulingText()),
                ov.applyStr(id, "book_name", r.bookName()),
                ov.applyInt(id, "page", r.page()),
                ov.applyInt(id, "volume", r.volume()),
                r.metadata(),
                r.createdAt());
    }

    /**
     * Наложение overrides на шарх/иляль/гариб (§5: book_name, author[hideable],
     * author_death_year, page, volume, text[hideable]). {@code kind} —
     * классификация, не editable.
     */
    public static HadithExplanation apply(HadithExplanation e, OverrideSet ov) {
        if (!ov.hasEntity(e.id())) {
            return e;
        }
        UUID id = e.id();
        return new HadithExplanation(
                e.id(),
                e.hadithId(),
                e.kind(),
                ov.applyStr(id, "book_name", e.bookName()),
                ov.applyStr(id, "author", e.author()),             // hideable
                ov.applyInt(id, "author_death_year", e.authorDeathYear()),
                ov.applyInt(id, "page", e.page()),
                ov.applyInt(id, "volume", e.volume()),
                ov.applyStr(id, "text", e.text()),                 // hideable
                e.metadata(),
                e.createdAt());
    }

    /**
     * Наложение overrides на джарх/таʿдиль-цитату (§5: commenter[hideable],
     * commenter_death_year, book_name, author, page, volume). {@code comments}
     * (jsonb verbatim-цитаты) — первоисточник риджаль-книги, НЕ переопределяется.
     */
    public static NarratorCommentary apply(NarratorCommentary c, OverrideSet ov) {
        if (!ov.hasEntity(c.id())) {
            return c;
        }
        UUID id = c.id();
        return new NarratorCommentary(
                c.id(),
                c.narratorId(),
                ov.applyStr(id, "commenter", c.commenter()),       // hideable
                ov.applyInt(id, "commenter_death_year", c.commenterDeathYear()),
                ov.applyStr(id, "book_name", c.bookName()),
                ov.applyStr(id, "author", c.author()),
                ov.applyInt(id, "page", c.page()),
                ov.applyInt(id, "volume", c.volume()),
                c.comments(),                                       // PROTECTED
                c.metadata(),
                c.createdAt());
    }

    /**
     * Наложение overrides на матн-вариацию (§5: printed_number, page_no, volume,
     * divergence_summary, text_ru, text_en). Арабский матн
     * ({@code text_ar}/{@code text_ar_normalized}) — первоисточник, НЕ
     * переопределяется; перевод (ru/en) — наш контент, editable.
     */
    public static Matn apply(Matn m, OverrideSet ov) {
        if (!ov.hasEntity(m.id())) {
            return m;
        }
        UUID id = m.id();
        return new Matn(
                m.id(),
                m.hadithId(),
                m.textAr(),                                         // PROTECTED
                m.textArNormalized(),                               // PROTECTED
                ov.applyStr(id, "text_ru", m.textRu()),
                ov.applyStr(id, "text_en", m.textEn()),
                m.collectionId(),
                ov.applyInt(id, "printed_number", m.printedNumber()),
                ov.applyInt(id, "page_no", m.pageNo()),
                ov.applyInt(id, "volume", m.volume()),
                m.isPrimary(),
                ov.applyStr(id, "divergence_summary", m.divergenceSummary()),
                m.metadata(),
                m.createdAt());
    }

    /**
     * Наложение overrides на цепь передачи (§5: chain_grade, primary_chain).
     * {@code primary_chain} — boolean; override null/hide занулил бы Boolean,
     * но конструктор {@link Sanad} ждёт примитив — потому применяем к
     * boxed-значению и разворачиваем с дефолтом базового флага.
     */
    public static Sanad apply(Sanad s, OverrideSet ov) {
        if (!ov.hasEntity(s.id())) {
            return s;
        }
        UUID id = s.id();
        Boolean primary = ov.applyBool(id, "primary_chain", s.primaryChain());
        return new Sanad(
                s.id(),
                s.hadithId(),
                ov.applyStr(id, "chain_grade", s.chainGrade()),
                s.compiledById(),
                s.compiledInBookId(),
                primary != null ? primary : s.primaryChain(),
                s.metadata(),
                s.createdAt());
    }
}
