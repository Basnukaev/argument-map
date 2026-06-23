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
import ru.basnukaev.argumentmap.hadith.domain.Narrator;

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

    /**
     * Record-level hide для списка сателлитов (Фаза 4, §4.2/§4.3). Обычному
     * читателю ({@code reveal=false}) скрытая запись НЕ отдаётся (вырезана);
     * ADMIN ({@code reveal=true}) получает её с {@code hiddenByAdmin=true} +
     * причиной, чтобы раскрыть. Один батч-{@code load} на тип (без N+1);
     * пустой OverrideSet → все видимы без флагов (общий случай, без аллокаций).
     */
    public <T, D> List<D> applyRecordHide(OverrideEntity table, List<T> records,
                                          Function<T, UUID> idOf, boolean reveal,
                                          HideAwareMapper<T, D> mapper) {
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
                out.add(mapper.map(r, false, null));
            } else if (reveal) {
                out.add(mapper.map(r, true, hide.get().reason()));
            }
            // non-reveal + hidden → вырезаем (не добавляем в out)
        }
        return out;
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
}
