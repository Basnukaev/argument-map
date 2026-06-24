package ru.basnukaev.argumentmap.hadith.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import ru.basnukaev.argumentmap.hadith.curation.repository.OverrideRepository;
import ru.basnukaev.argumentmap.hadith.curation.service.OverrideApplyService;
import ru.basnukaev.argumentmap.hadith.domain.Hadith;
import ru.basnukaev.argumentmap.hadith.domain.Sanad;
import ru.basnukaev.argumentmap.hadith.domain.SanadNarrator;
import ru.basnukaev.argumentmap.hadith.repository.HadithCrossrefRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithEditionRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithExplanationRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRepository;
import ru.basnukaev.argumentmap.hadith.repository.HadithRulingRepository;
import ru.basnukaev.argumentmap.hadith.repository.MatnRepository;
import ru.basnukaev.argumentmap.hadith.repository.SanadRepository;
import ru.basnukaev.argumentmap.hadith.service.HadithGradeBridgeService;
import ru.basnukaev.argumentmap.hadith.service.SanadGraphService;
import ru.basnukaev.argumentmap.hadith.web.dto.HadithDetailResponse;
import ru.basnukaev.argumentmap.repository.HadithGradeRepository;

/**
 * Unit-тест correctness lock-in для grouped narrator-lookup в
 * {@link HadithController#getDetail} (был O(sanads × links) линейный скан,
 * стал {@code groupingBy(sanadId)} + сортировка по position). Без Spring/БД:
 * репозитории замоканы.
 *
 * <p>Проверяет что при нескольких sanad'ах каждый получает ровно своих
 * narrator'ов (группировка не «протекает» между sanad'ами) и в правильном
 * порядке position - инвариант, который легко сломать при рефакторинге
 * вложенного цикла в map-lookup.
 */
class HadithControllerGetDetailTest {

    @Test
    void groupsNarratorsPerSanad_inPositionOrder() {
        HadithRepository hadithRepository = mock(HadithRepository.class);
        SanadRepository sanadRepository = mock(SanadRepository.class);
        MatnRepository matnRepository = mock(MatnRepository.class);
        HadithEditionRepository editionRepository = mock(HadithEditionRepository.class);
        HadithRulingRepository rulingRepository = mock(HadithRulingRepository.class);
        HadithExplanationRepository explanationRepository = mock(HadithExplanationRepository.class);
        HadithCrossrefRepository crossrefRepository = mock(HadithCrossrefRepository.class);
        HadithGradeRepository hadithGradeRepository = mock(HadithGradeRepository.class);
        SanadGraphService sanadGraphService = mock(SanadGraphService.class);
        HadithGradeBridgeService hadithGradeBridgeService = mock(HadithGradeBridgeService.class);

        UUID hadithId = UUID.randomUUID();
        UUID sanadA = UUID.randomUUID();
        UUID sanadB = UUID.randomUUID();
        UUID narA0 = UUID.randomUUID();
        UUID narA1 = UUID.randomUUID();
        UUID narB0 = UUID.randomUUID();

        when(hadithRepository.findById(hadithId)).thenReturn(Optional.of(
                new Hadith(hadithId, null, 1, "متن", "DRAFT", null, null, Instant.now())));

        when(sanadRepository.findByHadithId(hadithId)).thenReturn(List.of(
                new Sanad(sanadA, hadithId, "صحيح", null, null, true, null, Instant.now()),
                new Sanad(sanadB, hadithId, "حسن", null, null, false, null, Instant.now())
        ));

        // Links для обоих sanad'ов в одном bulk-списке, position сознательно
        // вперемешку (A position 1 раньше A position 0) - проверяем что
        // группировка сортирует по position внутри каждого sanad.
        when(sanadRepository.findNarratorsBySanadIds(List.of(sanadA, sanadB)))
                .thenReturn(List.of(
                        new SanadNarrator(sanadA, 1, narA1, "عن"),
                        new SanadNarrator(sanadA, 0, narA0, "سمعت"),
                        new SanadNarrator(sanadB, 0, narB0, "حدثنا")
                ));

        when(matnRepository.findByHadithId(hadithId)).thenReturn(List.of());
        when(editionRepository.findByHadithId(hadithId)).thenReturn(List.of());
        when(rulingRepository.findByHadithId(hadithId)).thenReturn(List.of());
        when(explanationRepository.findByHadithId(hadithId)).thenReturn(List.of());
        when(crossrefRepository.findByHadithId(hadithId)).thenReturn(List.of());

        HadithController controller = new HadithController(
                hadithRepository, sanadRepository, matnRepository,
                editionRepository, rulingRepository, explanationRepository,
                crossrefRepository, hadithGradeRepository, sanadGraphService,
                hadithGradeBridgeService,
                new OverrideApplyService(mock(OverrideRepository.class)),
                new ObjectMapper());

        HadithDetailResponse resp = controller.getDetail(hadithId);

        assertThat(resp.sanads()).hasSize(2);

        HadithDetailResponse.SanadDto a = resp.sanads().stream()
                .filter(s -> s.id().equals(sanadA)).findFirst().orElseThrow();
        HadithDetailResponse.SanadDto b = resp.sanads().stream()
                .filter(s -> s.id().equals(sanadB)).findFirst().orElseThrow();

        // Sanad A: оба своих narrator'а, отсортированы по position (0, затем 1)
        assertThat(a.narrators()).extracting(HadithDetailResponse.NarratorLinkDto::position)
                .containsExactly(0, 1);
        assertThat(a.narrators()).extracting(HadithDetailResponse.NarratorLinkDto::narratorId)
                .containsExactly(narA0, narA1);

        // Sanad B: только свой единственный narrator (нет утечки из A)
        assertThat(b.narrators()).extracting(HadithDetailResponse.NarratorLinkDto::narratorId)
                .containsExactly(narB0);
    }
}
