package ru.basnukaev.argumentmap.hadith.isnad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.basnukaev.argumentmap.hadith.domain.Narrator;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.NarratorRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.ArabicTextNormalizer;

/**
 * Unit-тесты {@link IsnadPersistenceService} (Mockito-моки репозиториев, без
 * Spring/БД). Проверяем: разворот цепи (position 0 = сподвижник),
 * transmission-маппинг, дедуп по normalized-name (reuse существующего + кэш
 * внутри вызова), idempotent delete, graceful no-op.
 */
class IsnadPersistenceServiceTest {

    private final SanadRepository sanadRepository = mock(SanadRepository.class);
    private final NarratorRepository narratorRepository = mock(NarratorRepository.class);
    private final IsnadPersistenceService service =
            new IsnadPersistenceService(sanadRepository, narratorRepository, new ObjectMapper());

    private static ExtractedIsnad isnad(ExtractedNarrator... narrators) {
        return new ExtractedIsnad(true, List.of(narrators), "matn");
    }

    private static Narrator existing(UUID id, String nameAr) {
        String norm = ArabicTextNormalizer.normalize(nameAr);
        return new Narrator(id, null, nameAr, norm, null, null, null, null,
                null, null, null, null, null, 0, null, Instant.EPOCH);
    }

    @Test
    void persist_reversesChain_positionZeroIsCompanion_transmissionMapped() {
        UUID hadithId = UUID.randomUUID();
        // top→companion: الحميدي … عمر (сподвижник)
        when(narratorRepository.findByNameArNormalized(any())).thenReturn(Optional.empty());
        when(narratorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persist(hadithId, isnad(
                new ExtractedNarrator("الحميدي", "حدثنا"),
                new ExtractedNarrator("سفيان", "حدثنا"),
                new ExtractedNarrator("عمر بن الخطاب", "عن النبي")
        ), null, "صحيح البخاري", "Сахих аль-Бухари");

        ArgumentCaptor<SanadNarrator> links = ArgumentCaptor.forClass(SanadNarrator.class);
        verify(sanadRepository, times(3)).saveNarratorLink(links.capture());
        List<SanadNarrator> captured = links.getAllValues();

        // position 0 = сподвижник, transmission = формула к Пророку
        assertThat(captured.get(0).position()).isZero();
        assertThat(captured.get(0).transmissionPhrase()).isEqualTo("عن النبي");
        // позиции возрастают, верх цепи на максимальной
        assertThat(captured.get(2).position()).isEqualTo(2);
        assertThat(captured.get(2).transmissionPhrase()).isEqualTo("حدثنا");
    }

    @Test
    void persist_reusesExistingNarrator_byNormalizedName_noNewSave() {
        UUID hadithId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        String sufyanNorm = ArabicTextNormalizer.normalize("سفيان");
        when(narratorRepository.findByNameArNormalized(sufyanNorm))
                .thenReturn(Optional.of(existing(existingId, "سفيان")));
        when(narratorRepository.findByNameArNormalized(
                ArabicTextNormalizer.normalize("عمر بن الخطاب"))).thenReturn(Optional.empty());
        when(narratorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persist(hadithId, isnad(
                new ExtractedNarrator("سفيان", "حدثنا"),
                new ExtractedNarrator("عمر بن الخطاب", "عن النبي")
        ), null, null, null);

        // سفيان переиспользован → save только для عمر (1 раз)
        verify(narratorRepository, times(1)).save(any());

        ArgumentCaptor<SanadNarrator> links = ArgumentCaptor.forClass(SanadNarrator.class);
        verify(sanadRepository, times(2)).saveNarratorLink(links.capture());
        // position 0 = عمر (после реверса), position 1 = سفيان (existingId)
        assertThat(links.getAllValues().get(1).narratorId()).isEqualTo(existingId);
    }

    @Test
    void persist_repeatedNameInOneChain_cachedWithinCall_singleLookupAndSave() {
        UUID hadithId = UUID.randomUUID();
        when(narratorRepository.findByNameArNormalized(any())).thenReturn(Optional.empty());
        when(narratorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // одно и то же имя дважды в цепи
        service.persist(hadithId, isnad(
                new ExtractedNarrator("فلان", "حدثنا"),
                new ExtractedNarrator("فلان", "عن")
        ), null, null, null);

        // lookup и save по «فلان» ровно один раз (кэш внутри вызова)
        verify(narratorRepository, times(1)).findByNameArNormalized(any());
        verify(narratorRepository, times(1)).save(any());
        verify(sanadRepository, times(2)).saveNarratorLink(any());
    }

    @Test
    void persist_deletesExistingChainsFirst_idempotent() {
        UUID hadithId = UUID.randomUUID();
        when(narratorRepository.findByNameArNormalized(any())).thenReturn(Optional.empty());
        when(narratorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.persist(hadithId, isnad(new ExtractedNarrator("عمر", "عن النبي")),
                null, null, null);

        verify(sanadRepository, times(1)).deleteByHadithId(hadithId);
        verify(sanadRepository, times(1)).save(any(Sanad.class));
    }

    @Test
    void persist_nullOrNotFoundOrEmpty_isNoOp() {
        UUID hadithId = UUID.randomUUID();

        service.persist(hadithId, null, null, null, null);
        service.persist(hadithId, new ExtractedIsnad(false, List.of(), null), null, null, null);
        service.persist(hadithId, new ExtractedIsnad(true, List.of(), null), null, null, null);

        verify(sanadRepository, never()).deleteByHadithId(any());
        verify(sanadRepository, never()).save(any(Sanad.class));
        verify(sanadRepository, never()).saveNarratorLink(any());
    }
}
